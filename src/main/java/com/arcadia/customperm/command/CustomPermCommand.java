package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.LuckPermsService;
import com.arcadia.customperm.perm.PermissionService;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
                .requires(src -> {
                    // Vérifie le statut OP réel du joueur, pas le niveau virtuel de la source.
                    // AliasManager élève la source à op-4 via withPermission(4) : si on se fiait
                    // uniquement à hasPermission(2), tout joueur exécutant un alias contenant
                    // une sous-commande /customperm contournerait INVARIANT-503 (NFR6).
                    // createCommandSourceStack() recrée un stack au niveau OP réel du joueur
                    // (non-élevé) — contrairement à isOp(profile) qui ignorait le niveau requis
                    // et accordait l'accès dès le niveau OP 1 au lieu de 2 minimum.
                    if (src.getEntity() instanceof ServerPlayer player) {
                        return player.createCommandSourceStack().hasPermission(2);
                    }
                    return src.hasPermission(2); // Console, command blocks, serveur : inchangé
                })
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
                    .then(Commands.argument("player", StringArgumentType.word())
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
        boolean removed = CustomPerm.configManager.getCommands().removeCommand(name);
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
        removeGradeFromUsers(g, name);
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
        boolean added = grade.permissions.add(node);
        if (!added) {
            ctx.getSource().sendSuccess(
                () -> Component.literal(node + " is already granted to " + gradeName + " — no change."), false);
            return 1;
        }
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
        boolean removed = grade.permissions.remove(node);
        if (!removed) {
            ctx.getSource().sendSuccess(
                () -> Component.literal(node + " is not granted to " + gradeName + " — no change."), false);
            return 1;
        }
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
        List<String> list = CustomPerm.configManager.getGrades()
            .userGrades.computeIfAbsent(player.getUUID().toString(), k -> new ArrayList<>());
        if (list.contains(gradeName)) {
            final String gn = gradeName, pn = player.getGameProfile().getName();
            ctx.getSource().sendSuccess(
                () -> Component.literal(pn + " is already assigned to " + gn + " — no change."), false);
            return 1;
        }
        list.add(gradeName);
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
        boolean removed = list != null && list.remove(gradeName);
        if (!removed) {
            final String gn = gradeName, pn = player.getGameProfile().getName();
            ctx.getSource().sendSuccess(
                () -> Component.literal(pn + " is not assigned to " + gn + " — no change."), false);
            return 1;
        }
        if (list.isEmpty()) {
            CustomPerm.configManager.getGrades().userGrades.remove(player.getUUID().toString());
        }
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
        if (server != null) {
            shadowsExisting = server.getCommands().getDispatcher().getRoot().getChildren()
                .stream().anyMatch(n -> n.getName().equals(name));
        }

        CustomPerm.configManager.getAliases().aliases.put(name, steps);
        CustomPerm.configManager.save();
        refreshAlias(ctx, name);
        resyncCommands(ctx);

        if (shadowsExisting) {
            CustomPerm.LOGGER.warn("[CustomPerm] Alias '{}' shadows vanilla command '{}'", name, name);
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
        if (name.equals("customperm")) {
            ctx.getSource().sendFailure(Component.literal("Reserved name."));
            return 0;
        }
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

    private static int debugCheck(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String cmd = StringArgumentType.getString(ctx, "command");

        var server = ctx.getSource().getServer();

        // P1 : guard explicite — server null = pas de contexte serveur (distinct de joueur offline)
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("[CustomPerm] No server context available."));
            return 0;
        }

        boolean inGrantedList = CustomPerm.configManager.getCommands().grantedCommands.contains(cmd);
        boolean inDispatcher = server.getCommands().getDispatcher().getRoot()
            .getChildren().stream().anyMatch(n -> n.getName().equals(cmd));

        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);

        if (player == null) {
            // AC3 : rapport partiel — joueur hors-ligne
            ctx.getSource().sendSuccess(() -> Component.literal(
                "=== Debug for /" + cmd + " (" + playerName + ") [OFFLINE] ==="), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                "  Command exists in dispatcher : " + inDispatcher), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                "  In granted-commands list    : " + inGrantedList), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                "  [Player is offline — op-level, permission check and wrapper test unavailable]")
                .withStyle(ChatFormatting.YELLOW), false);
            return 1; // P2 : rapport partiel affiché avec succès → retourner 1
        }

        // AC1/AC2 : rapport complet — joueur en ligne
        CommandSourceStack source = player.createCommandSourceStack();
        boolean op2 = source.hasPermission(2);
        boolean op4 = source.hasPermission(4);
        String permNode = "customperm.command." + cmd;
        boolean permGranted = PermissionService.get().hasPermission(source, permNode);
        boolean preserveOriginalRequires = CustomPerm.configManager.getCommands().shouldPreserveOriginalRequires(cmd);

        var rootNode = server.getCommands().getDispatcher().getRoot().getChildren()
            .stream().filter(n -> n.getName().equals(cmd)).findFirst().orElse(null);
        boolean actualWrapper = false;
        if (rootNode != null) {
            try {
                actualWrapper = rootNode.canUse(source);
            } catch (Throwable ignored) {}
        }

        boolean customPermAllows = op2 || (inGrantedList && permGranted);
        boolean comparableDecision = !inGrantedList || !preserveOriginalRequires;
        boolean shouldPass = inGrantedList ? customPermAllows : actualWrapper;

        String backend = CustomPerm.backendLabel();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "=== Debug for /" + cmd + " (" + player.getGameProfile().getName() + ") [backend: " + backend + "] ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Command exists in dispatcher : " + (rootNode != null)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  In granted-commands list    : " + inGrantedList), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Source has op level 2       : " + op2), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Source has op level 4       : " + op4), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  PermService says " + permNode + " : " + permGranted), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Preserve original requires : " + preserveOriginalRequires), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Logical decision (computed) : "
                + (comparableDecision ? Boolean.toString(shouldPass) : "requires original predicate")), false);
        boolean finalActualWrapper = actualWrapper;
        ctx.getSource().sendSuccess(() -> Component.literal("  Actual wrapper canUse()     : " + finalActualWrapper).withStyle(
            !comparableDecision || finalActualWrapper == shouldPass ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        if (comparableDecision && finalActualWrapper != shouldPass) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "  >>> MISMATCH — wrapper does not match expected logic <<<").withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    // ---------------- test ----------------

    private static int testPerm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String node = StringArgumentType.getString(ctx, "node");
        boolean granted = PermissionService.get().hasPermission(player.createCommandSourceStack(), node);
        String backend = CustomPerm.backendLabel();
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

        // Snapshot unique pour éviter TOCTOU si isDegraded() bascule en cours d'exécution
        String backend = CustomPerm.backendLabel();

        int totalCmds = server == null ? 0 : server.getCommands().getDispatcher().getRoot().getChildren().size();
        int exposed = CustomPerm.configManager.getCommands().grantedCommands.size();
        int aliases = CustomPerm.configManager.getAliases().aliases.size();
        int grades = CustomPerm.configManager.getGrades().grades.size();
        int users = CustomPerm.configManager.getGrades().userGrades.size();
        String lpFallbackMode = CustomPerm.configManager.getSettings().luckPermsFallbackMode;

        ctx.getSource().sendSuccess(() -> Component.literal("=== CustomPerm Status ===").withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Backend            : " + backend), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  LP fallback mode   : " + lpFallbackMode), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Dispatcher commands: " + totalCmds + " (vanilla + mods + aliases)"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Exposed commands   : " + exposed), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Custom aliases     : " + aliases), false);

        // AC1 grades-fallback : afficher grades si Internal pur OU si fallback (InternalPermService actif dans les deux cas)
        boolean showGrades = !CustomPerm.isLuckPermsActive();
        if (showGrades) {
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

        // F1+F2 : hoist + Locale.ROOT pour éviter TOCTOU locale et allocations répétées
        final String patternLower = (pattern != null) ? pattern.toLowerCase(Locale.ROOT) : null;

        int displayed = 0;
        for (String name : rootNames) {
            if (patternLower != null && !name.toLowerCase(Locale.ROOT).contains(patternLower)) continue;
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
            ctx.getSource().sendFailure(Component.literal("No commands matched" + (pattern != null ? " '" + sanitizePlain(pattern) + "'." : ".")));
            return 0;
        }
        final int finalCount = displayed;
        ctx.getSource().sendSuccess(() -> Component.literal("--- " + finalCount + " command(s) shown. Legend: EXPO=exposed, ALIAS=custom, MOD=this mod ---").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ---------------- reload ----------------

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        boolean reloaded = CustomPerm.configManager.load();
        if (!reloaded) {
            String msg = CustomPerm.configManager.isReloading()
                ? "[CustomPerm] Reload already in progress — try again in a moment."
                : "[CustomPerm] Reload failed — check server logs for details (invalid JSON or disk error).";
            ctx.getSource().sendFailure(Component.literal(msg));
            return 0;
        }

        com.arcadia.customperm.config.ConfigSnapshot snapshot =
            CustomPerm.configManager.getSnapshot();

        // 1. Notifier le PermissionService du nouveau snapshot (no-op par défaut)
        com.arcadia.customperm.perm.PermissionService.get().onConfigReload(snapshot);

        // 2. Notifier le CommandTreeReloader — stub H1.3, implémentation concrète É2.6
        var server = ctx.getSource().getServer();
        CustomPerm.treeReloader.onConfigReload(snapshot, server);

        // 3. Re-push ClientboundCommandsPacket à tous les clients — INVARIANT-501 :
        //    obligatoirement via server.execute() pour garantir l'exécution sur le tick-thread,
        //    même si on est déjà sur le tick-thread (futur-proof si reload hors tick-thread).
        if (server != null) {
            server.execute(() ->
                server.getPlayerList().getPlayers()
                    .forEach(p -> server.getCommands().sendCommands(p))
            );
        }

        // 4. Log de succès
        CustomPerm.LOGGER.info("[CustomPerm] Configuration reloaded successfully");
        success(ctx, "Configuration reloaded successfully.");
        return 1;
    }

    // ---------------- helpers ----------------

    private static boolean warnIfLuckPerms(CommandContext<CommandSourceStack> ctx) {
        if (CustomPerm.isLuckPermsActive()) {
            ctx.getSource().sendFailure(Component.literal(
                "[CustomPerm] Grade commands are disabled — use /lp instead."));
            return true;
        }
        return false;
    }

    private static void removeGradeFromUsers(GradesConfig grades, String gradeName) {
        Iterator<java.util.Map.Entry<String, List<String>>> iterator = grades.userGrades.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            List<String> assigned = entry.getValue();
            if (assigned == null) {
                iterator.remove();
                continue;
            }
            assigned.removeIf(gradeName::equals);
            if (assigned.isEmpty()) {
                iterator.remove();
            }
        }
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

    private static String sanitizePlain(String input) {
        return input == null ? "" : input.replace('§', '?').replaceAll("\\p{Cntrl}", "?");
    }
}
