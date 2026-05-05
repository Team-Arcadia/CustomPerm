package com.example.customperm.command;

import com.example.customperm.CustomPerm;
import com.example.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Field;
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
 * Brigadier's CommandNode#requirement field is private and has no setter, so we use reflection.
 * (NeoForge's Access Transformers only target Minecraft classes, not Mojang's separately-published Brigadier lib.)
 */
public class CommandTreeRewriter {

    private static final Field REQUIREMENT_FIELD;
    static {
        try {
            REQUIREMENT_FIELD = CommandNode.class.getDeclaredField("requirement");
            REQUIREMENT_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Brigadier API changed: CommandNode.requirement not found", e);
        }
    }

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

        try {
            @SuppressWarnings("unchecked")
            Predicate<CommandSourceStack> original = (Predicate<CommandSourceStack>) REQUIREMENT_FIELD.get(node);
            Predicate<CommandSourceStack> wrapped = source -> {
                if (original != null && original.test(source)) return true;
                return PermissionService.get().hasPermission(source, "customperm.command." + rootName);
            };
            REQUIREMENT_FIELD.set(node, wrapped);
        } catch (IllegalAccessException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Failed to rewrite requirement on /{} subnode {}", rootName, node.getName());
        }

        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            rewrite(child, rootName, visited);
        }
        if (node.getRedirect() != null) {
            rewrite(node.getRedirect(), rootName, visited);
        }
    }
}
