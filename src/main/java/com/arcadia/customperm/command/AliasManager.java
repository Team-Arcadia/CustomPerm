package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers admin-defined aliases. Each alias is a top-level literal that delegates
 * its execution to the underlying command(s) via the server command dispatcher.
 *
 * Two entry points:
 *   - {@link #registerAll(CommandDispatcher)} : at server start / RegisterCommandsEvent,
 *     registers every alias from config.
 *   - {@link #registerOrReplace(CommandDispatcher, String)} : at runtime (admin uses
 *     /customperm alias add|addstep|removestep|remove), re-registers a single alias
 *     so the change takes effect without requiring /reload.
 *
 * Permission node for an alias {@code foo} is {@code customperm.alias.foo}.
 */
public class AliasManager {

    private static final Field CHILDREN_FIELD;
    private static final Field LITERALS_FIELD;
    private static final Field ARGUMENTS_FIELD;
    private static final Map<String, CommandNode<CommandSourceStack>> SHADOWED_ORIGINALS = new HashMap<>();
    private static final Set<String> REGISTERED_ALIASES = new HashSet<>();

    /**
     * Profondeur maximale d'alias imbriqués. Sans cette garde, un alias qui s'invoque
     * lui-même (ou un cycle a→b→a) part en récursion infinie avec une source élevée
     * op-4 jusqu'au StackOverflowError.
     */
    static final int MAX_ALIAS_DEPTH = 8;
    private static final ThreadLocal<Integer> ALIAS_DEPTH = ThreadLocal.withInitial(() -> 0);

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

    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        AliasesConfig cfg = CustomPerm.configManager.getAliases();
        for (Map.Entry<String, List<String>> entry : cfg.aliases.entrySet()) {
            registerOrReplace(dispatcher, entry.getKey());
        }
    }

    /**
     * Synchronise le dispatcher live avec l'état courant de la config (hot-reload É2.6).
     * Couvre les trois cas qu'un simple registerAll raterait après édition d'aliases.json :
     * alias ajoutés au fichier, alias supprimés (avec restauration du nœud shadowé), et
     * steps modifiés — la closure d'exécution capture la liste de steps à l'enregistrement,
     * donc un re-register est obligatoire pour qu'elle pointe sur les nouveaux steps.
     */
    public static void applyConfig(CommandDispatcher<CommandSourceStack> dispatcher) {
        Set<String> names = new HashSet<>(CustomPerm.configManager.getAliases().aliases.keySet());
        names.addAll(REGISTERED_ALIASES);
        for (String name : names) {
            registerOrReplace(dispatcher, name);
        }
    }

    /**
     * Purge l'état statique lié au dispatcher courant. À appeler quand le dispatcher est
     * remplacé (RegisterCommandsEvent — /reload, redémarrage) ou détruit (arrêt serveur) :
     * sinon REGISTERED_ALIASES/SHADOWED_ORIGINALS retiennent des nœuds de l'ancien arbre
     * (fuite mémoire) et une suppression d'alias restaurerait un nœud périmé.
     */
    public static void clearServerState() {
        SHADOWED_ORIGINALS.clear();
        REGISTERED_ALIASES.clear();
    }

    /**
     * Re-registers a single alias on the live dispatcher: removes any existing node
     * with that name, then re-adds it from the current config (or skips if the alias
     * was just deleted from config).
     */
    public static void registerOrReplace(CommandDispatcher<CommandSourceStack> dispatcher, String aliasName) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        CommandNode<CommandSourceStack> existing = root.getChild(aliasName);

        List<String> steps = CustomPerm.configManager.getAliases().aliases.get(aliasName);
        if (steps != null && !steps.isEmpty()) {
            if (existing != null
                    && !REGISTERED_ALIASES.contains(aliasName)
                    && !SHADOWED_ORIGINALS.containsKey(aliasName)) {
                SHADOWED_ORIGINALS.put(aliasName, existing);
            }
            removeFromRoot(root, aliasName);
            registerOne(dispatcher, aliasName, steps);
            REGISTERED_ALIASES.add(aliasName);
        } else {
            if (REGISTERED_ALIASES.remove(aliasName)) {
                removeFromRoot(root, aliasName);
            }
            CommandNode<CommandSourceStack> original = SHADOWED_ORIGINALS.remove(aliasName);
            if (original != null) {
                putInRoot(root, aliasName, original);
            }
        }
    }

    private static void registerOne(CommandDispatcher<CommandSourceStack> dispatcher, String alias, List<String> steps) {
        if (steps == null || steps.isEmpty()) return;
        String permNode = "customperm.alias." + alias;

        dispatcher.register(
            Commands.literal(alias)
                .requires(src -> src.hasPermission(2) || PermissionService.get().hasPermission(src, permNode))
                .executes(ctx -> executeAlias(ctx.getSource(), alias, steps))
        );
    }

    static int executeAlias(CommandSourceStack source, String alias, List<String> steps) {
        var server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias + " failed: no server context."));
            return 0;
        }

        int depth = ALIAS_DEPTH.get();
        if (depth >= MAX_ALIAS_DEPTH) {
            source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias
                + " aborted: nested alias depth exceeds " + MAX_ALIAS_DEPTH + " (recursive alias chain?)."));
            CustomPerm.LOGGER.warn("[CustomPerm] alias /{} aborted at depth {} — recursive alias chain detected.", alias, depth);
            return 0;
        }
        ALIAS_DEPTH.set(depth + 1);
        try {
            // Elevate to op-level 4 so steps that delegate to op-only commands work.
            var elevated = source.withPermission(4);
            int executed = 0;
            for (String step : steps) {
                String command = normalizeStep(step);
                if (command.isEmpty()) continue;

                try {
                    if (!CommandTreeRewriter.executeOriginalCommand(elevated, command)) {
                        server.getCommands().getDispatcher().execute(command, elevated);
                    }
                    executed++;
                } catch (CommandSyntaxException e) {
                    source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias
                        + " step failed: " + command + " (" + e.getMessage() + ")"));
                    CustomPerm.LOGGER.warn("[CustomPerm] alias /{} step `{}` failed: {}", alias, command, e.getMessage());
                } catch (Throwable t) {
                    // D1 : ne pas absorber les erreurs JVM fatales (OOM, SOE…) — même politique que LuckPermsService.
                    if (t instanceof Error e) throw e;
                    source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias
                        + " step threw: " + command + " (" + t.getClass().getSimpleName() + ")"));
                    CustomPerm.LOGGER.warn("[CustomPerm] alias /{} step `{}` threw", alias, command, t);
                }
            }
            return executed;
        } finally {
            ALIAS_DEPTH.set(depth);
        }
    }

    static String normalizeStep(String step) {
        if (step == null) return "";

        String command = step.strip();
        // Un seul slash optionnel : en retirer plusieurs casserait les commandes dont le
        // littéral racine commence par "/" (style WorldEdit : step "//wand" → "/wand").
        if (command.startsWith("/")) {
            command = command.substring(1).strip();
        }
        return command;
    }

    @SuppressWarnings("unchecked")
    private static void removeFromRoot(CommandNode<CommandSourceStack> root, String name) {
        try {
            Map<String, CommandNode<CommandSourceStack>> children =
                (Map<String, CommandNode<CommandSourceStack>>) CHILDREN_FIELD.get(root);
            Map<String, LiteralCommandNode<CommandSourceStack>> literals =
                (Map<String, LiteralCommandNode<CommandSourceStack>>) LITERALS_FIELD.get(root);
            Map<String, ArgumentCommandNode<CommandSourceStack, ?>> arguments =
                (Map<String, ArgumentCommandNode<CommandSourceStack, ?>>) ARGUMENTS_FIELD.get(root);

            children.remove(name);
            literals.remove(name);
            arguments.remove(name);
        } catch (IllegalAccessException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not remove alias /{} from dispatcher: {}", name, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void putInRoot(CommandNode<CommandSourceStack> root, String name, CommandNode<CommandSourceStack> node) {
        try {
            Map<String, CommandNode<CommandSourceStack>> children =
                (Map<String, CommandNode<CommandSourceStack>>) CHILDREN_FIELD.get(root);
            Map<String, LiteralCommandNode<CommandSourceStack>> literals =
                (Map<String, LiteralCommandNode<CommandSourceStack>>) LITERALS_FIELD.get(root);
            Map<String, ArgumentCommandNode<CommandSourceStack, ?>> arguments =
                (Map<String, ArgumentCommandNode<CommandSourceStack, ?>>) ARGUMENTS_FIELD.get(root);

            children.put(name, node);
            if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
                literals.put(name, literal);
                arguments.remove(name);
            } else if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
                arguments.put(name, argument);
                literals.remove(name);
            }
        } catch (IllegalAccessException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not restore shadowed command /{}: {}", name, e.getMessage());
        }
    }
}
