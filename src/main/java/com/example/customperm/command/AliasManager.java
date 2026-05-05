package com.example.customperm.command;

import com.example.customperm.CustomPerm;
import com.example.customperm.config.AliasesConfig;
import com.example.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;
import java.util.Map;

/**
 * Registers admin-defined aliases. Each alias is a top-level literal that delegates
 * its execution to the underlying command(s) via the server command dispatcher.
 *
 * An alias may be a single command or a chained list of commands (macro). Steps run
 * sequentially through the same source; if a step fails, subsequent steps still run
 * (matches vanilla command-block behavior, predictable for admins).
 *
 * Permission node for an alias `foo` is `customperm.alias.foo`.
 */
public class AliasManager {

    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        AliasesConfig cfg = CustomPerm.configManager.getAliases();
        for (Map.Entry<String, List<String>> entry : cfg.aliases.entrySet()) {
            String alias = entry.getKey();
            List<String> steps = entry.getValue();
            if (steps == null || steps.isEmpty()) continue;
            String permNode = "customperm.alias." + alias;

            dispatcher.register(
                Commands.literal(alias)
                    .requires(src -> src.hasPermission(2) || PermissionService.get().hasPermission(src, permNode))
                    .executes(ctx -> {
                        var server = ctx.getSource().getServer();
                        int executed = 0;
                        for (String step : steps) {
                            if (step == null || step.isBlank()) continue;
                            try {
                                server.getCommands().performPrefixedCommand(ctx.getSource(), step);
                                executed++;
                            } catch (Throwable t) {
                                CustomPerm.LOGGER.warn("[CustomPerm] alias /{} step `{}` threw: {}", alias, step, t.toString());
                            }
                        }
                        return executed;
                    })
            );
        }
    }
}
