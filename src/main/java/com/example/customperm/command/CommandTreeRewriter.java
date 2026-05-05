package com.example.customperm.command;

import com.example.customperm.CustomPerm;
import com.example.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Walks the command dispatcher after every registration pass and wraps each command's
 * `requires(...)` predicate so a player passes either via vanilla op level OR via the
 * permission node `customperm.command.<root>` (resolved by LuckPerms or the internal grade store).
 *
 * Our own `/customperm` command and registered aliases are NOT rewritten — they manage their own auth.
 *
 * Note: the field {@code CommandNode.requirement} is made public + non-final by our
 * {@code META-INF/accesstransformer.cfg}, so we can read/write it directly.
 */
public class CommandTreeRewriter {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Register our own commands first so the rewriter sees them and can skip them.
        CustomPermCommand.register(dispatcher);
        AliasManager.registerAll(dispatcher);

        Set<String> skipRoots = new HashSet<>();
        skipRoots.add("customperm");
        skipRoots.addAll(CustomPerm.configManager.getAliases().aliases.keySet());

        Set<CommandNode<CommandSourceStack>> visited = new HashSet<>();
        for (CommandNode<CommandSourceStack> child : dispatcher.getRoot().getChildren()) {
            String rootName = child.getName();
            if (skipRoots.contains(rootName)) continue;
            rewrite(child, rootName, visited);
        }
    }

    private static void rewrite(CommandNode<CommandSourceStack> node, String rootName, Set<CommandNode<CommandSourceStack>> visited) {
        if (!visited.add(node)) return;

        Predicate<CommandSourceStack> original = node.requirement;
        node.requirement = source -> {
            if (original != null && original.test(source)) return true;
            return PermissionService.get().hasPermission(source, "customperm.command." + rootName);
        };

        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            rewrite(child, rootName, visited);
        }
        if (node.getRedirect() != null) {
            rewrite(node.getRedirect(), rootName, visited);
        }
    }
}
