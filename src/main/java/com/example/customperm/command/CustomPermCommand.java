package com.example.customperm.command;

import com.example.customperm.CustomPerm;
import com.example.customperm.config.GradesConfig;
import com.example.customperm.perm.LuckPermsService;
import com.example.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /customperm command add|remove <name>             # expose / hide a vanilla or modded command
 *             command list                          # show currently exposed commands
 * /customperm grade   create|delete <name>
 *                     addperm|removeperm <grade> <node>
 *                     assign|unassign <player> <grade>
 *                     list
 * /customperm alias   add <name> <cmd1[; cmd2; ...]>    # macro: split on ';'
 *                     addstep <name> <cmd>              # append a step to existing alias
 *                     removestep <name> <index>         # 0-based
 *                     steps <name>                      # show steps
 *                     remove <name>
 *                     list
 * /customperm test    <player> <node>                   # debug: report grant/deny + backend
 * /customperm reload
 *
 * The mod ships with NO commands pre-exposed. Each admin chooses what to expose via
 * /customperm command add. Until exposed, every command keeps its vanilla op-only behaviour.
 *
 * Always requires op level 2 — this is the management command.
 * When LuckPerms is the active backend, grade subcommands print a hint to use `/lp` instead.
 */
public class CustomPermCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("customperm")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("grade")
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::gradeCreate)))
                    .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::gradeDelete)))
                    .then(Commands.literal("addperm")
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::gradeAddPerm))))
                    .then(Commands.literal("removeperm")
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::gradeRemovePerm))))
                    .then(Commands.literal("assign")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .executes(CustomPermCommand::gradeAssign))))
                    .then(Commands.literal("unassign")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .executes(CustomPermCommand::gradeUnassign))))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::gradeList)))
                .then(Commands.literal("alias")
                    .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("commands", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::aliasAdd))))
                    .then(Commands.literal("addstep")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::aliasAddStep))))
                    .then(Commands.literal("removestep")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes(CustomPermCommand::aliasRemoveStep))))
                    .then(Commands.literal("steps")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::aliasSteps)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::aliasRemove)))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::aliasList)))
                .then(Commands.literal("command")
                    .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::commandAdd)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::commandRemove)))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::commandList)))
                .then(Commands.literal("test")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("node", StringArgumentType.greedyString())
                            .executes(CustomPermCommand::testPerm))))
                .then(Commands.literal("debug")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("command", StringArgumentType.word())
                            .executes(CustomPermCommand::debugCheck))))
                .then(Commands.literal("status")
                    .executes(CustomPermCommand::status))
                .then(Commands.literal("scan")
                    .executes(CustomPermCommand::scanAll)
                    .then(Commands.argument("pattern", StringArgumentType.word())
                        .executes(CustomPermCommand::scanPattern)))
                .then(Commands.literal("reload")
                    .executes(CustomPermCommand::reload))
        );
    }

    // ---------------- command exposure ----------------

    private static int commandAdd(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (name.equals("customperm")) {
            ctx.getSource().sendFailure(Component.literal("Cannot expose /customperm itself."));
            return 0;
        }
        var server = ctx.getSource().getServer();
        boolean exists = server != null && server.getCommands().getDispatcher().getRoot().getChildren()
            .stream().anyMatch(n -> n.getName().equals(name));
        if (!exists) {
            ctx.getSource().sendFailure(Component.literal(
                "Command /" + name + " does not exist on this server. Check the spelling and that the providing mod is loaded."));
            return 0;
        }
        boolean added = CustomPerm.configManager.getCommands().grantedCommands.add(name);
        if (!added) {
            ctx.getSource().sendFailure(Component.literal("Command /" + name + " is already exposed."));
            return 0;
        }
        CustomPerm.configManager.save();
        resyncCommands(ctx);
        success(ctx, "Exposed /" + name + " to the permission system. Grant `customperm.command." + name + "` to authorize.");
        return 1;
    }

    private static int commandRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean removed = CustomPerm.configManager.getCommands().grantedCommands.remove(name);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("Command /" + name + " is not currently exposed."));
            return 0;
        }
        CustomPerm.configManager.save();
        resyncCommands(ctx);
        success(ctx, "/" + name + " is no longer exposed. Reverts to its original (vanilla) authorisation.");
        return 1;
    }

    private static int commandList(CommandContext<CommandSourceStack> ctx) {
        var commands = CustomPerm.configManager.getCommands().grantedCommands;
        if (commands.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "No commands exposed. Use /customperm command add <name> to expose one."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Exposed commands: " + String.join(", ", commands)), false);
        }
        return 1;
    }

    // ---------------- grade ----------------

    private static int gradeCreate(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        GradesConfig g = CustomPerm.configManager.getGrades();
        if (g.grades.containsKey(name)) {
            ctx.getSource().sendFailure(Component.literal("Grade already exists: " + name));
            return 0;
        }
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        g.grades.put(name, grade);
        CustomPerm.configManager.save();
        success(ctx, "Created grade " + name);
        return 1;
    }

    private static int gradeDelete(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        GradesConfig g = CustomPerm.configManager.getGrades();
        if (g.grades.remove(name) == null) {
            ctx.getSource().sendFailure(Component.literal("No such grade: " + name));
            return 0;
        }
        g.userGrades.values().forEach(list -> list.remove(name));
        CustomPerm.configManager.save();
        resyncCommands(ctx);
        success(ctx, "Deleted grade " + name);
        return 1;
    }

    private static int gradeAddPerm(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        String gradeName = StringArgumentType.getString(ctx, "grade");
        String node = StringArgumentType.getString(ctx, "node");
        GradesConfig.Grade grade = CustomPerm.configManager.getGrades().grades.get(gradeName);
        if (grade == null) {
            ctx.getSource().sendFailure(Component.literal("No such grade: " + gradeName));
            return 0;
        }
        grade.permissions.add(node);
        CustomPerm.configManager.save();
        resyncCommands(ctx);
        success(ctx, "Added " + node + " -> " + gradeName);
        return 1;
    }

    private static int gradeRemovePerm(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        String gradeName = StringArgumentType.getString(ctx, "grade");
        String node = StringArgumentType.getString(ctx, "node");
        GradesConfig.Grade grade = CustomPerm.configManager.getGrades().grades.get(gradeName);
        if (grade == null) {
            ctx.getSource().sendFailure(Component.literal("No such grade: " + gradeName));
            return 0;
        }
        grade.permissions.remove(node);
        CustomPerm.configManager.save();
        resyncCommands(ctx);
        success(ctx, "Removed " + node + " from " + gradeName);
        return 1;
    }

    private static int gradeAssign(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (warnIfLuckPerms(ctx)) return 0;
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String gradeName = StringArgumentType.getString(ctx, "grade");
        if (!CustomPerm.configManager.getGrades().grades.containsKey(gradeName)) {
            ctx.getSource().sendFailure(Component.literal("No such grade: " + gradeName));
            return 0;
        }
        CustomPerm.configManager.getGrades()
            .userGrades.computeIfAbsent(player.getUUID().toString(), k -> new ArrayList<>())
            .add(gradeName);
        CustomPerm.configManager.save();
        resyncPlayer(ctx, player);
        success(ctx, "Assigned " + gradeName + " -> " + player.getGameProfile().getName());
        return 1;
    }

    private static int gradeUnassign(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (warnIfLuckPerms(ctx)) return 0;
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String gradeName = StringArgumentType.getString(ctx, "grade");
        var list = CustomPerm.configManager.getGrades().userGrades.get(player.getUUID().toString());
        if (list != null) list.remove(gradeName);
        CustomPerm.configManager.save();
        resyncPlayer(ctx, player);
        success(ctx, "Unassigned " + gradeName + " from " + player.getGameProfile().getName());
        return 1;
    }

    private static int gradeList(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        Set<String> names = CustomPerm.configManager.getGrades().grades.keySet();
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No grades defined."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("Grades: " + String.join(", ", names)), false);
        }
        return 1;
    }

    // ---------------- alias ----------------

    private static int aliasAdd(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String raw = StringArgumentType.getString(ctx, "commands");
        if (name.equals("customperm")) {
            ctx.getSource().sendFailure(Component.literal("Reserved name."));
            return 0;
        }
        List<String> steps = Arrays.stream(raw.split(";"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
        if (steps.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No commands provided."));
            return 0;
        }

        // Collision warning: if an existing real (non-alias) command has this name,
        // adding the alias will shadow it. The admin can still proceed.
        var server = ctx.getSource().getServer();
        boolean shadowsExisting = false;
        if (server != null && !CustomPerm.configManager.getAliases().aliases.containsKey(name)) {
            shadowsExisting = server.getCommands().getDispatcher().getRoot().getChildren()
                .stream().anyMatch(n -> n.getName().equals(name));
        }

        CustomPerm.configManager.getAliases().aliases.put(name, steps);
        CustomPerm.configManager.save();
        refreshAlias(ctx, name);
        resyncCommands(ctx);

        if (shadowsExisting) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "WARNING: /" + name + " shadows an existing command. Players will need `customperm.alias." + name +
                "` (not the command's own perm) to use it."
            ).withStyle(ChatFormatting.YELLOW), true);
        }
        success(ctx, "Alias /" + name + " set with " + steps.size() + " step(s).");
        ctx.getSource().sendSuccess(() -> Component.literal(
            "  Permission node: customperm.alias." + name + "  |  Steps run with op-level 4 — only grant to trusted users."
        ).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int aliasAddStep(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String cmd = StringArgumentType.getString(ctx, "command").trim();
        if (cmd.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Empty command."));
            return 0;
        }
        var aliases = CustomPerm.configManager.getAliases().aliases;
        aliases.computeIfAbsent(name, k -> new ArrayList<>()).add(cmd);
        CustomPerm.configManager.save();
        refreshAlias(ctx, name);
        resyncCommands(ctx);
        success(ctx, "Appended step #" + (aliases.get(name).size() - 1) + " to /" + name + ": " + cmd);
        return 1;
    }

    private static int aliasRemoveStep(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int index = IntegerArgumentType.getInteger(ctx, "index");
        var steps = CustomPerm.configManager.getAliases().aliases.get(name);
        if (steps == null) {
            ctx.getSource().sendFailure(Component.literal("No such alias: " + name));
            return 0;
        }
        if (index < 0 || index >= steps.size()) {
            ctx.getSource().sendFailure(Component.literal("Index out of range (0.." + (steps.size() - 1) + ")"));
            return 0;
        }
        String removed = steps.remove(index);
        if (steps.isEmpty()) CustomPerm.configManager.getAliases().aliases.remove(name);
        CustomPerm.configManager.save();
        refreshAlias(ctx, name);
        resyncCommands(ctx);
        success(ctx, "Removed step #" + index + " from /" + name + ": " + removed);
        return 1;
    }

    private static int aliasSteps(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        var steps = CustomPerm.configManager.getAliases().aliases.get(name);
        if (steps == null || steps.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No such alias: " + name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Steps for /" + name + ":"), false);
        for (int i = 0; i < steps.size(); i++) {
            final int idx = i;
            ctx.getSource().sendSuccess(() -> Component.literal("  #" + idx + ": /" + steps.get(idx)), false);
        }
        return 1;
    }

    private static int aliasRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (CustomPerm.configManager.getAliases().aliases.remove(name) == null) {
            ctx.getSource().sendFailure(Component.literal("No such alias: " + name));
            return 0;
        }
        CustomPerm.configManager.save();
        refreshAlias(ctx, name);
        resyncCommands(ctx);
        success(ctx, "Removed alias /" + name);
        return 1;
    }

    private static void refreshAlias(CommandContext<CommandSourceStack> ctx, String name) {
        var server = ctx.getSource().getServer();
        if (server != null) {
            AliasManager.registerOrReplace(server.getCommands().getDispatcher(), name);
        }
    }

    private static int aliasList(CommandContext<CommandSourceStack> ctx) {
        var map = CustomPerm.configManager.getAliases().aliases;
        if (map.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No aliases defined."), false);
        } else {
            map.forEach((k, v) ->
                ctx.getSource().sendSuccess(() -> Component.literal("/" + k + "  (" + v.size() + " step" + (v.size() > 1 ? "s" : "") + ")"), false));
        }
        return 1;
    }

    // ---------------- debug ----------------

    private static int debugCheck(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String cmd = StringArgumentType.getString(ctx, "command");
        CommandSourceStack source = player.createCommandSourceStack();

        boolean inGrantedList = CustomPerm.configManager.getCommands().grantedCommands.contains(cmd);
        boolean op2 = source.hasPermission(2);
        boolean op4 = source.hasPermission(4);
        String permNode = "customperm.command." + cmd;
        boolean permGranted = PermissionService.get().hasPermission(source, permNode);
        boolean shouldPass = op2 || (inGrantedList && permGranted);

        var server = ctx.getSource().getServer();
        var rootNode = server == null ? null : server.getCommands().getDispatcher().getRoot().getChildren()
            .stream().filter(n -> n.getName().equals(cmd)).findFirst().orElse(null);
        boolean actualWrapper = false;
        if (rootNode != null) {
            try {
                actualWrapper = rootNode.canUse(source);
            } catch (Throwable ignored) {}
        }

        String backend = (CustomPerm.permissions instanceof LuckPermsService) ? "LuckPerms" : "Internal";
        ctx.getSource().sendSuccess(() -> Component.literal("=== Debug for /" + cmd + " (" + player.getGameProfile().getName() + ") [backend: " + backend + "] ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Command exists in dispatcher : " + (rootNode != null)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  In granted-commands list    : " + inGrantedList), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Source has op level 2       : " + op2), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Source has op level 4       : " + op4), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  PermService says " + permNode + " : " + permGranted), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Logical decision (computed) : " + shouldPass), false);
        boolean finalActualWrapper = actualWrapper;
        ctx.getSource().sendSuccess(() -> Component.literal("  Actual wrapper canUse()     : " + finalActualWrapper).withStyle(
            finalActualWrapper == shouldPass ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        if (finalActualWrapper != shouldPass) {
            ctx.getSource().sendSuccess(() -> Component.literal("  >>> MISMATCH — wrapper does not match expected logic <<<").withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    // ---------------- test ----------------

    private static int testPerm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String node = StringArgumentType.getString(ctx, "node");
        boolean granted = PermissionService.get().hasPermission(player.createCommandSourceStack(), node);
        String backend = (CustomPerm.permissions instanceof LuckPermsService) ? "LuckPerms" : "Internal";
        ChatFormatting color = granted ? ChatFormatting.GREEN : ChatFormatting.RED;
        String verdict = granted ? "GRANTED" : "DENIED";
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[" + backend + "] " + player.getGameProfile().getName() + " :: " + node + " -> " + verdict
        ).withStyle(color), false);
        return granted ? 1 : 0;
    }

    // ---------------- status / scan ----------------

    private static int status(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        String backend = (CustomPerm.permissions instanceof LuckPermsService) ? "LuckPerms" : "Internal";
        int totalCmds = server == null ? 0 : server.getCommands().getDispatcher().getRoot().getChildren().size();
        int exposed = CustomPerm.configManager.getCommands().grantedCommands.size();
        int aliases = CustomPerm.configManager.getAliases().aliases.size();
        int grades = CustomPerm.configManager.getGrades().grades.size();
        int users = CustomPerm.configManager.getGrades().userGrades.size();

        ctx.getSource().sendSuccess(() -> Component.literal("=== CustomPerm Status ===").withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Backend            : " + backend), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Dispatcher commands: " + totalCmds + " (vanilla + mods + aliases)"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Exposed commands   : " + exposed), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Custom aliases     : " + aliases), false);
        if (!(CustomPerm.permissions instanceof LuckPermsService)) {
            ctx.getSource().sendSuccess(() -> Component.literal("  Internal grades    : " + grades), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  Users with grade   : " + users), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("  (Grades & user perms managed by /lp)").withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int scanAll(CommandContext<CommandSourceStack> ctx) {
        return scan(ctx, null);
    }

    private static int scanPattern(CommandContext<CommandSourceStack> ctx) {
        return scan(ctx, StringArgumentType.getString(ctx, "pattern"));
    }

    private static int scan(CommandContext<CommandSourceStack> ctx, String pattern) {
        var server = ctx.getSource().getServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("No server context."));
            return 0;
        }
        Set<String> exposed = CustomPerm.configManager.getCommands().grantedCommands;
        Set<String> aliasNames = CustomPerm.configManager.getAliases().aliases.keySet();

        List<String> rootNames = new ArrayList<>();
        for (var node : server.getCommands().getDispatcher().getRoot().getChildren()) {
            rootNames.add(node.getName());
        }
        rootNames.sort(String::compareTo);

        int displayed = 0;
        for (String name : rootNames) {
            if (pattern != null && !name.contains(pattern)) continue;
            String marker;
            ChatFormatting color;
            if (name.equals("customperm")) {
                marker = "[ MOD  ] "; color = ChatFormatting.LIGHT_PURPLE;
            } else if (aliasNames.contains(name)) {
                marker = "[ALIAS ] "; color = ChatFormatting.AQUA;
            } else if (exposed.contains(name)) {
                marker = "[EXPO  ] "; color = ChatFormatting.GREEN;
            } else {
                marker = "[      ] "; color = ChatFormatting.GRAY;
            }
            final String row = marker + "/" + name;
            final ChatFormatting c = color;
            ctx.getSource().sendSuccess(() -> Component.literal(row).withStyle(c), false);
            displayed++;
        }
        if (displayed == 0) {
            ctx.getSource().sendFailure(Component.literal("No commands matched" + (pattern != null ? " '" + pattern + "'." : ".")));
            return 0;
        }
        final int finalCount = displayed;
        ctx.getSource().sendSuccess(() -> Component.literal("--- " + finalCount + " command(s) shown. Legend: EXPO=exposed, ALIAS=custom, MOD=this mod ---").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ---------------- reload ----------------

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CustomPerm.configManager.load();
        resyncCommands(ctx);
        success(ctx, "Configuration reloaded. Run /reload to rebuild the command tree if aliases changed.");
        return 1;
    }

    // ---------------- helpers ----------------

    private static boolean warnIfLuckPerms(CommandContext<CommandSourceStack> ctx) {
        if (CustomPerm.permissions instanceof LuckPermsService) {
            ctx.getSource().sendFailure(Component.literal(
                "LuckPerms is active — manage groups/permissions with /lp instead. The internal grade store is unused."));
            return true;
        }
        return false;
    }

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
    }

    private static void resyncCommands(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        if (server != null) server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p));
    }

    private static void resyncPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        var server = ctx.getSource().getServer();
        if (server != null) server.getCommands().sendCommands(p);
    }
}
