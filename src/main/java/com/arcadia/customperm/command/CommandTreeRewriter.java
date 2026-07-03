package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.ConfigSnapshot;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.perm.PermissionService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
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
 *
 * <p><strong>LuckPerms coexistence.</strong> LuckPerms' NeoForge {@code BrigadierInjector}
 * overwrites every command node's {@code requirement} (reflectively) AFTER this event, which
 * would drop CustomPerm's clone-time predicate. To let {@code customperm.command.<name>} work
 * alongside LuckPerms, {@link #reassertExposedCommands} re-applies an {@link ExposureGate} on the
 * live requirement on the next server tick (and on config changes) — running last, and additively
 * (it only opens access for exposed+granted sources, deferring to LuckPerms/vanilla otherwise).</p>
 */
public class CommandTreeRewriter implements ICommandTreeReloader {

    private static final Field CHILDREN_FIELD;
    private static final Field LITERALS_FIELD;
    private static final Field ARGUMENTS_FIELD;
    private static final Field REQUIREMENT_FIELD;
    private static final Map<String, CommandNode<CommandSourceStack>> ORIGINAL_ROOTS = new HashMap<>();
    private static final Set<CommandNode<CommandSourceStack>> WRAPPED_NODES =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /** Set when the exposure gate needs re-applying on the next server tick (after LuckPerms injects). */
    private static volatile boolean reassertPending = false;

    static {
        try {
            CHILDREN_FIELD = CommandNode.class.getDeclaredField("children");
            CHILDREN_FIELD.setAccessible(true);
            LITERALS_FIELD = CommandNode.class.getDeclaredField("literals");
            LITERALS_FIELD.setAccessible(true);
            ARGUMENTS_FIELD = CommandNode.class.getDeclaredField("arguments");
            ARGUMENTS_FIELD.setAccessible(true);
            REQUIREMENT_FIELD = CommandNode.class.getDeclaredField("requirement");
            REQUIREMENT_FIELD.setAccessible(true);
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
        if (server != null) {
            // Applique aussi les changements d'aliases.json au dispatcher live : ajouts,
            // suppressions (avec restauration du nœud shadowé) et steps modifiés. Sans
            // cela, /customperm reload ne touche que la config en mémoire et les alias
            // continuent d'exécuter les anciens steps capturés dans leur closure.
            AliasManager.applyConfig(server.getCommands().getDispatcher());
        }
        // repair APRÈS applyConfig : un nœud restauré par la suppression d'un alias
        // redevient éligible au wrapping s'il est exposé.
        int repaired = repair(server);
        // Re-pose la vérification CustomPerm par-dessus une éventuelle injection LuckPerms.
        reassertExposedCommands(server);
        CustomPerm.LOGGER.info("[CustomPerm] CommandTreeRewriter.onConfigReload — repaired {} command wrapper(s).", repaired);
    }

    // LOWEST : maximise la chance de passer après les handlers RegisterCommandsEvent des
    // autres mods, pour que leurs commandes soient déjà dans le dispatcher au wrapping.
    // (Filet de sécurité complémentaire : repair() au ServerStartedEvent.)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Nouveau dispatcher à chaque RegisterCommandsEvent (/reload vanilla, redémarrage
        // dans la même JVM) : l'état statique référence l'ancien arbre et doit être purgé,
        // sinon fuite mémoire + restauration de nœuds périmés côté AliasManager.
        clearServerState();
        AliasManager.clearServerState();

        CustomPermCommand.register(dispatcher);
        AliasManager.registerAll(dispatcher);

        int wrapped = wrapUnwrappedRoots(dispatcher);
        CustomPerm.LOGGER.info("[CustomPerm] Wrapped {} top-level command(s) for permission gating.", wrapped);

        // LuckPerms' BrigadierInjector overwrites every command's requirement AFTER this event.
        // Defer the CustomPerm re-assertion to a server tick so it runs last and wins.
        reassertPending = true;
    }

    /**
     * Server-thread tick hook: re-applies CustomPerm's exposure gate once whenever it has been
     * flagged pending (boot, /reload, command tree rebuild). This runs AFTER LuckPerms'
     * BrigadierInjector has replaced command requirements, so CustomPerm's grant wins.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!reassertPending) return;
        reassertPending = false;
        int n = reassertExposedCommands(event.getServer());
        if (n > 0) {
            CustomPerm.LOGGER.info("[CustomPerm] Re-asserted exposure over other permission handlers for {} command(s).", n);
            event.getServer().getPlayerList().getPlayers()
                .forEach(p -> event.getServer().getCommands().sendCommands(p));
        }
    }

    /** Purge l'état statique lié au dispatcher courant — voir onRegisterCommands. */
    public static void clearServerState() {
        ORIGINAL_ROOTS.clear();
        WRAPPED_NODES.clear();
        RateLimiter.clearServerState();
    }

    public static int repair(MinecraftServer server) {
        if (server == null) return 0;
        return wrapUnwrappedRoots(server.getCommands().getDispatcher());
    }

    /**
     * Re-applies CustomPerm's exposure gate directly on each exposed command's LIVE requirement,
     * by reflectively composing {@link ExposureGate} on top of whatever predicate currently owns
     * the node. This is what lets {@code customperm.command.<name>} work even when LuckPerms'
     * BrigadierInjector has replaced the requirement with its own — CustomPerm re-wraps last.
     *
     * <p>The composition is <strong>additive</strong>: if CustomPerm doesn't grant the source, the
     * gate defers to the delegate (LuckPerms' / vanilla's check), so no existing gating is broken.
     * Commands no longer exposed have their delegate restored.</p>
     *
     * @return number of nodes whose requirement was changed.
     */
    public static int reassertExposedCommands(MinecraftServer server) {
        if (server == null || !CustomPerm.isDirectCommandExposureEnabled()) return 0;
        Set<String> exposed = CustomPerm.configManager.getCommands().grantedCommands;
        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();

        int changed = 0;
        for (CommandNode<CommandSourceStack> node : root.getChildren()) {
            String name = node.getName();
            if ("customperm".equals(name)) continue;
            // LuckPerms injects a requirement on EVERY node (root + arguments/children), so the
            // gate must cover the whole subtree — otherwise the root command is reachable but its
            // sub-arguments (e.g. /gamemode <mode>) stay gated by LuckPerms.
            IdentityHashMap<CommandNode<CommandSourceStack>, Boolean> visited = new IdentityHashMap<>();
            if (exposed.contains(name)) {
                changed += applyGateRecursive(node, name, visited);
            } else {
                changed += restoreGateRecursive(node, visited);
            }
        }
        return changed;
    }

    /** Reflectively composes an {@link ExposureGate} onto {@code node} and every descendant. */
    private static int applyGateRecursive(CommandNode<CommandSourceStack> node, String rootName,
            IdentityHashMap<CommandNode<CommandSourceStack>, Boolean> visited) {
        if (visited.put(node, Boolean.TRUE) != null) return 0;  // cycle/redirect guard
        int changed = 0;
        try {
            @SuppressWarnings("unchecked")
            Predicate<CommandSourceStack> current = (Predicate<CommandSourceStack>) REQUIREMENT_FIELD.get(node);
            if (!(current instanceof ExposureGate)) {
                REQUIREMENT_FIELD.set(node, new ExposureGate(rootName, current));
                changed++;
            }
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not re-assert exposure for /{} (node '{}')", rootName, node.getName(), t);
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            changed += applyGateRecursive(child, rootName, visited);
        }
        return changed;
    }

    /** Restores the delegate on {@code node} and every descendant still carrying our gate. */
    private static int restoreGateRecursive(CommandNode<CommandSourceStack> node,
            IdentityHashMap<CommandNode<CommandSourceStack>, Boolean> visited) {
        if (visited.put(node, Boolean.TRUE) != null) return 0;
        int changed = 0;
        try {
            @SuppressWarnings("unchecked")
            Predicate<CommandSourceStack> current = (Predicate<CommandSourceStack>) REQUIREMENT_FIELD.get(node);
            if (current instanceof ExposureGate gate) {
                REQUIREMENT_FIELD.set(node, gate.delegate());
                changed++;
            }
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not restore requirement for node '{}'", node.getName(), t);
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            changed += restoreGateRecursive(child, visited);
        }
        return changed;
    }

    /**
     * Requirement predicate that grants an exposed command when CustomPerm authorises the source,
     * and otherwise defers to the {@code delegate} (the predicate previously on the node — e.g.
     * LuckPerms' injected requirement, or the vanilla op-level check). A concrete class (not a
     * lambda) so {@link #reassertExposedCommands} can recognise its own gate and stay idempotent.
     */
    record ExposureGate(String rootName, Predicate<CommandSourceStack> delegate)
            implements Predicate<CommandSourceStack> {
        @Override
        public boolean test(CommandSourceStack source) {
            boolean op2 = source.hasPermission(2);
            boolean customPermAllows = op2
                || PermissionService.get().hasPermission(source, "customperm.command." + rootName);
            if (!customPermAllows) {
                // Not granted by CustomPerm — keep whatever gating was there before (additive).
                return delegate != null && delegate.test(source);
            }
            if (CustomPerm.configManager.getCommands().shouldPreserveOriginalRequires(rootName)) {
                return delegate == null || delegate.test(source);
            }
            return true;
        }
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
                if (wrappedRoot == original) continue;  // unknown type — the tick-time re-assert still gates it
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
                wrapCommand(rootName, literal.getCommand()),
                wrappedReq,
                literal.getRedirect(),     // redirect: keep pointer to original (rare; not deep-cloned)
                literal.getRedirectModifier(),
                literal.isFork()
            );
        } else if (original instanceof ArgumentCommandNode<?, ?>) {
            wrapped = cloneArgument(original, wrappedReq, rootName);
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
            Predicate<CommandSourceStack> wrappedReq,
            String rootName) {
        ArgumentCommandNode argNode = (ArgumentCommandNode) original;
        return new ArgumentCommandNode<>(
            argNode.getName(),
            argNode.getType(),
            wrapCommand(rootName, argNode.getCommand()),
            wrappedReq,
            argNode.getRedirect(),
            argNode.getRedirectModifier(),
            argNode.isFork(),
            argNode.getCustomSuggestions()
        );
    }

    /**
     * Wraps a leaf node's execution callback with the rate-limit check for {@code rootName}
     * (see RateLimitsConfig). Applied uniformly to every executable node under the root, so
     * `/observable foo` and `/observable bar` share a single per-player counter keyed by
     * the root command name. Console/command-block sources have no stable UUID and are left
     * unlimited.
     */
    private static Command<CommandSourceStack> wrapCommand(String rootName, Command<CommandSourceStack> original) {
        if (original == null) return null;
        return ctx -> {
            CommandSourceStack source = ctx.getSource();
            RateLimitsConfig.Rule rule = CustomPerm.configManager.getRateLimits().get(rootName);
            if (rule != null && rule.enabled && source.getEntity() instanceof ServerPlayer player) {
                RateLimiter.Result result = RateLimiter.tryAcquire(
                    rootName, player.getUUID(), rule.maxExecutions, rule.windowSeconds);
                if (!result.allowed()) {
                    source.sendFailure(Component.literal(
                        "[CustomPerm] Rate limit reached for /" + rootName + " — try again in "
                            + result.retryAfterSeconds() + "s (max " + rule.maxExecutions
                            + " per " + rule.windowSeconds + "s)."));
                    return 0;
                }
            }
            return original.run(ctx);
        };
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
