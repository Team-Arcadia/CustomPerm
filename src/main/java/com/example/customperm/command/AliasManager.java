package com.example.customperm.command;

import com.example.customperm.CustomPerm;
import com.example.customperm.config.AliasesConfig;
import com.example.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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
            registerOne(dispatcher, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Re-registers a single alias on the live dispatcher: removes any existing node
     * with that name, then re-adds it from the current config (or skips if the alias
     * was just deleted from config).
     */
    public static void registerOrReplace(CommandDispatcher<CommandSourceStack> dispatcher, String aliasName) {
        removeFromRoot(dispatcher.getRoot(), aliasName);

        List<String> steps = CustomPerm.configManager.getAliases().aliases.get(aliasName);
        if (steps != null && !steps.isEmpty()) {
            registerOne(dispatcher, aliasName, steps);
        }
    }

    private static void registerOne(CommandDispatcher<CommandSourceStack> dispatcher, String alias, List<String> steps) {
        if (steps == null || steps.isEmpty()) return;
        String permNode = "customperm.alias." + alias;

        dispatcher.register(
            Commands.literal(alias)
                .requires(src -> src.hasPermission(2) || PermissionService.get().hasPermission(src, permNode))
                .executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    // Elevate to op-level 4 so steps that delegate to op-only commands work.
                    var elevated = ctx.getSource().withPermission(4);
                    int executed = 0;
                    for (String step : steps) {
                        if (step == null || step.isBlank()) continue;
                        try {
                            server.getCommands().performPrefixedCommand(elevated, step);
                            executed++;
                        } catch (Throwable t) {
                            CustomPerm.LOGGER.warn("[CustomPerm] alias /{} step `{}` threw: {}", alias, step, t.toString());
                        }
                    }
                    return executed;
                })
        );
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
}
