package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.ConfigSnapshot;
import com.arcadia.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Replaces every top-level command node with a wrapped clone whose {@code requires}
 * predicate adds CustomPerm's permission check on top of the original.
 *
 * Why clone instead of mutating the existing node's {@code requirement} field?
 * Brigadier's {@code requirement} is {@code private final}, and writing to it via
 * reflection succeeds without exception but the JVM/JIT may keep the original value
 * (final field optimisation). By BUILDING a new node whose final field is set at
 * construction time, no JIT shenanigans can interfere.
 *
 * To install the wrapped node, we mutate the parent's {@code children} / {@code literals}
 * / {@code arguments} maps via reflection. The fields are final, but we read them
 * (no field-write involved) and modify the contained Map (which is mutable).
 *
 * The wrapper is dynamic — at evaluation time it consults
 * {@link com.arcadia.customperm.config.CommandsConfig#grantedCommands}. The admin can
 * add/remove commands at runtime without re-wrapping.
 */
public class CommandTreeRewriter implements ICommandTreeReloader {

    private static final Field CHILDREN_FIELD;
    private static final Field LITERALS_FIELD;
    private static final Field ARGUMENTS_FIELD;
    private static final Map<String, CommandNode<CommandSourceStack>> ORIGINAL_ROOTS = new HashMap<>();
    private static final Set<CommandNode<CommandSourceStack>> WRAPPED_NODES =
        Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        try {
            CHILDREN_FIELD = CommandNode.class.getDeclaredField("children");
            CHILDREN_FIELD.setAccessible(true);
            LITERALS_FIELD = CommandNode.class.getDeclaredField("literals");
            LITERALS_FIELD.setAccessible(true);
            ARGUMENTS_FIELD = CommandNode.class.getDeclaredField("arguments");
            ARGUMENTS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Brigadier API changed: CommandNode internal maps not found", e);
        }
    }

    /**
     * Appelée après chaque hot-reload réussi de la configuration (É2.6).
     *
     * <p>Les prédicats de {@code wrapRecursive} lisent {@code grantedCommands} dynamiquement
     * à chaque évaluation — aucun re-wrapping structurel n'est nécessaire après un reload.
     * Le push du {@code ClientboundCommandsPacket} est déjà géré par le reload handler
     * ({@code CustomPermCommand.reload} étape 3) via {@code server.execute()} (INVARIANT-501) —
     * ne pas le répliquer ici pour éviter un double envoi.</p>
     *
     * @param snapshot Nouveau snapshot de configuration (déjà appliqué dans ConfigManager)
     * @param server   Serveur Minecraft — peut être null si aucun serveur actif
     */
    @Override
    public void onConfigReload(ConfigSnapshot snapshot, MinecraftServer server) {
        int repaired = repair(server);
        CustomPerm.LOGGER.info("[CustomPerm] CommandTreeRewriter.onConfigReload — repaired {} command wrapper(s).", repaired);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        CustomPermCommand.register(dispatcher);
        AliasManager.registerAll(dispatcher);

        int wrapped = wrapUnwrappedRoots(dispatcher);
        CustomPerm.LOGGER.info("[CustomPerm] Wrapped {} top-level command(s) for permission gating.", wrapped);
    }

    public static int repair(MinecraftServer server) {
        if (server == null) return 0;
        return wrapUnwrappedRoots(server.getCommands().getDispatcher());
    }

    private static int wrapUnwrappedRoots(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!CustomPerm.isDirectCommandExposureEnabled()) {
            return 0;
        }

        Set<String> skipRoots = new HashSet<>();
        skipRoots.add("customperm");
        skipRoots.addAll(CustomPerm.configManager.getAliases().aliases.keySet());

        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        List<CommandNode<CommandSourceStack>> originals = new ArrayList<>(root.getChildren());

        int wrapped = 0;
        for (CommandNode<CommandSourceStack> original : originals) {
            String name = original.getName();
            if (skipRoots.contains(name)) continue;
            if (WRAPPED_NODES.contains(original)) continue;
            try {
                ORIGINAL_ROOTS.put(name, original);
                IdentityHashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>> visited = new IdentityHashMap<>();
                CommandNode<CommandSourceStack> wrappedRoot = wrapRecursive(original, name, visited);
                if (wrappedRoot == original) continue;  // unknown type, skip
                replaceInParent(root, original, wrappedRoot);
                wrapped++;
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Failed to wrap /{}", name, t);
            }
        }

        return wrapped;
    }

    static boolean executeOriginalCommand(CommandSourceStack source, String command) throws Exception {
        String rootName = commandRoot(command);
        if (rootName.isEmpty()) return false;

        CommandNode<CommandSourceStack> original = ORIGINAL_ROOTS.get(rootName);
        if (original == null) return false;

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(original);
        dispatcher.execute(command, source);
        return true;
    }

    private static String commandRoot(String command) {
        String trimmed = command == null ? "" : command.strip();
        if (trimmed.isEmpty()) return "";

        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        return trimmed.substring(0, end);
    }

    private static CommandNode<CommandSourceStack> wrapRecursive(
            CommandNode<CommandSourceStack> original,
            String rootName,
            IdentityHashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>> visited) {

        if (visited.containsKey(original)) return visited.get(original);

        Predicate<CommandSourceStack> origReq = original.getRequirement();
        Predicate<CommandSourceStack> wrappedReq = source -> {
            boolean originalAllows = origReq == null || origReq.test(source);
            if (!CustomPerm.isDirectCommandExposureEnabled()) {
                return originalAllows;
            }
            if (!CustomPerm.configManager.getCommands().grantedCommands.contains(rootName)) {
                return originalAllows;
            }
            boolean customPermAllows = source.hasPermission(2)
                    || PermissionService.get().hasPermission(source, "customperm.command." + rootName);
            if (!customPermAllows) return false;
            if (CustomPerm.configManager.getCommands().shouldPreserveOriginalRequires(rootName)) {
                return originalAllows;
            }
            return true;
        };

        CommandNode<CommandSourceStack> wrapped;
        if (original instanceof LiteralCommandNode<CommandSourceStack> literal) {
            wrapped = new LiteralCommandNode<>(
                literal.getLiteral(),
                literal.getCommand(),
                wrappedReq,
                literal.getRedirect(),     // redirect: keep pointer to original (rare; not deep-cloned)
                literal.getRedirectModifier(),
                literal.isFork()
            );
        } else if (original instanceof ArgumentCommandNode<?, ?>) {
            wrapped = cloneArgument(original, wrappedReq);
        } else {
            return original;  // unknown node type — leave alone
        }

        visited.put(original, wrapped);
        WRAPPED_NODES.add(wrapped);

        for (CommandNode<CommandSourceStack> child : original.getChildren()) {
            CommandNode<CommandSourceStack> wrappedChild = wrapRecursive(child, rootName, visited);
            if (wrappedChild == child
                    && !(child instanceof LiteralCommandNode<?>)
                    && !(child instanceof ArgumentCommandNode<?, ?>)) {
                CustomPerm.LOGGER.warn("[CustomPerm] Skipping unknown child node type while wrapping /{} {}: {}",
                        rootName, child.getName(), child.getClass().getName());
                continue;
            }
            wrapped.addChild(wrappedChild);
        }

        return wrapped;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandNode<CommandSourceStack> cloneArgument(
            CommandNode<CommandSourceStack> original,
            Predicate<CommandSourceStack> wrappedReq) {
        ArgumentCommandNode argNode = (ArgumentCommandNode) original;
        return new ArgumentCommandNode<>(
            argNode.getName(),
            argNode.getType(),
            argNode.getCommand(),
            wrappedReq,
            argNode.getRedirect(),
            argNode.getRedirectModifier(),
            argNode.isFork(),
            argNode.getCustomSuggestions()
        );
    }

    @SuppressWarnings("unchecked")
    private static void replaceInParent(
            CommandNode<CommandSourceStack> parent,
            CommandNode<CommandSourceStack> original,
            CommandNode<CommandSourceStack> wrapped) throws IllegalAccessException {

        Map<String, CommandNode<CommandSourceStack>> children =
            (Map<String, CommandNode<CommandSourceStack>>) CHILDREN_FIELD.get(parent);
        Map<String, LiteralCommandNode<CommandSourceStack>> literals =
            (Map<String, LiteralCommandNode<CommandSourceStack>>) LITERALS_FIELD.get(parent);
        Map<String, ArgumentCommandNode<CommandSourceStack, ?>> arguments =
            (Map<String, ArgumentCommandNode<CommandSourceStack, ?>>) ARGUMENTS_FIELD.get(parent);

        String name = original.getName();
        children.put(name, wrapped);
        if (wrapped instanceof LiteralCommandNode<CommandSourceStack> lit) {
            literals.put(name, lit);
            arguments.remove(name);
        } else if (wrapped instanceof ArgumentCommandNode<CommandSourceStack, ?> arg) {
            arguments.put(name, arg);
            literals.remove(name);
        }
    }
}
