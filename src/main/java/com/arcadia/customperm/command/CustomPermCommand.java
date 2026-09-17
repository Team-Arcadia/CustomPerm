/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.AdminResult;
import com.arcadia.customperm.admin.AliasAdmin;
import com.arcadia.customperm.admin.CommandAdmin;
import com.arcadia.customperm.admin.ConfigAdmin;
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.admin.RateLimitAdmin;
import com.arcadia.customperm.admin.UserAdmin;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.admin.LogAdmin;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.LuckPermsData;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.AdminAccess;
import com.arcadia.customperm.perm.LuckPermsService;
import com.arcadia.customperm.perm.PermissionNodes;
import com.arcadia.customperm.perm.Tristate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * /customperm command add|remove <name>             # expose / hide a vanilla or modded command
 *             command preserve <name> <true|false>  # also keep the command's original requirement
 *             command gateall <true|false>          # every command reads its node, not only exposed ones
 *             command list                          # show currently exposed commands
 * /customperm grade   create|delete <name>
 *                     addperm|removeperm <grade> <node>
 *                     adddeny|removedeny <grade> <node>   # most specific entry wins, DENY on a tie
 *                     weight <grade> <weight>             # breaks ties at the same specificity
 *                     parent add|remove <grade> <parent>  # inherit another grade, nearest entry wins
 *                     parent adddeny|removedeny <grade> <parent>  # refuse a grade, wherever it is inherited
 *                     parent list <grade>
 *                     assign|unassign <player> <grade>    # online, or joined the server before
 *                     setdefault <grade> | cleardefault   # grade applied to every player
 *                     list
 * /customperm user    addperm|removeperm <player> <node>  # nodes carried by one player, above their grades
 *                     adddeny|removedeny <player> <node>
 *                     denygrade|undenygrade <player> <grade>  # refuse a grade for one player
 *                     list <player>                       # grades held, refused, and own nodes
 * /customperm alias   add <name> <cmd1[; cmd2; ...]>    # macro: split on ';'
 *                     addstep <name> <cmd>              # append a step to existing alias
 *                     removestep <name> <index>         # 0-based
 *                     movestep <name> <from> <to>       # reorder, 0-based
 *                     setstep <name> <index> <cmd>      # replace one step
 *                     steps <name>                      # show steps
 *                     remove <name>
 *                     list
 * /customperm ratelimit set <name> <max> <windowSeconds>   # cap executions per player per window
 *                     persistence <name> <world_save|immediate>  # when usage history is written
 *                     enable <name>                        # re-enable a previously configured limit
 *                     disable <name>                       # keep the limit's numbers, stop enforcing it
 *                     remove <name>                        # delete the limit entirely
 *                     list                                 # show configured limits
 * /customperm test    <player> <node>                   # debug: report grant/deny + backend
 * /customperm reload
 * /customperm log admin|players [count]            # latest admin changes / player commands
 *             log record|mask <true|false>          # player command log, argument masking
 * /customperm gui [page]                            # open the admin interface (CustomPerm needed client-side)
 * /customperm gui luckperms [groups|players|tracks]  # LuckPerms editor, only when LuckPerms is installed
 *
 * The mod ships with NO commands pre-exposed. Each admin chooses what to expose via
 * /customperm command add. Until exposed, every command keeps its vanilla op-only behaviour.
 *
 * Players need op level 2 and customperm.admin; changes also need customperm.manage.<area>. The console always may.
 * When LuckPerms is the active backend, grade subcommands print a hint to use `/lp` instead.
 */
public class CustomPermCommand {

    // ---------------- suggestion providers ----------------
    // Toutes les données proviennent de la config vivante (configManager) ou du dispatcher :
    // recalculées à chaque frappe, donc toujours à jour après add/remove/reload.

    /** Grades existants. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRADES =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            CustomPerm.configManager.getGrades().grades.keySet(), builder);

    /** Alias existants. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALIASES =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            CustomPerm.configManager.getAliases().aliases.keySet(), builder);

    /** Limites de débit configurées. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_RATE_LIMITS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            CustomPerm.configManager.getRateLimits().rules.keySet(), builder);

    /** Commandes actuellement exposées (pour dé-exposer). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EXPOSED_COMMANDS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            CustomPerm.configManager.getCommands().grantedCommands, builder);

    /** Racines du dispatcher pas encore exposées et hors /customperm (pour exposer). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EXPOSABLE_COMMANDS =
        (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            if (server == null) return builder.buildFuture();
            Set<String> exposed = CustomPerm.configManager.getCommands().grantedCommands;
            List<String> names = server.getCommands().getDispatcher().getRoot().getChildren().stream()
                .map(com.mojang.brigadier.tree.CommandNode::getName)
                .filter(n -> !n.equals("customperm") && !exposed.contains(n))
                .collect(Collectors.toList());
            return SharedSuggestionProvider.suggest(names, builder);
        };

    /** Union commandes exposées + alias — cibles plausibles d'une limite de débit. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EXPOSED_AND_ALIASES =
        (ctx, builder) -> {
            Set<String> union = new TreeSet<>(CustomPerm.configManager.getCommands().grantedCommands);
            union.addAll(CustomPerm.configManager.getAliases().aliases.keySet());
            return SharedSuggestionProvider.suggest(union, builder);
        };

    /** Toutes les racines du dispatcher (debug/scan : inspection arbitraire). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALL_COMMANDS =
        (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            if (server == null) return builder.buildFuture();
            List<String> names = server.getCommands().getDispatcher().getRoot().getChildren().stream()
                .map(com.mojang.brigadier.tree.CommandNode::getName)
                .collect(Collectors.toList());
            return SharedSuggestionProvider.suggest(names, builder);
        };

    /** Joueurs en ligne, par nom (debug conserve StringArgument pour supporter l'offline). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ONLINE_PLAYERS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            ctx.getSource().getOnlinePlayerNames(), builder);

    /**
     * Known permission nodes: customperm.command.* and customperm.alias.* for what is configured, the
     * admin interface write nodes, and every node (allowed or denied) already used by a grade.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_KNOWN_NODES =
        (ctx, builder) -> {
            Set<String> nodes = new TreeSet<>(PermissionNodes.all());
            for (String c : CustomPerm.configManager.getCommands().grantedCommands) {
                nodes.add("customperm.command." + c);
            }
            var server = ctx.getSource().getServer();
            if (CustomPerm.gatesAllCommands() && server != null) {
                server.getCommands().getDispatcher().getRoot().getChildren().forEach(node -> {
                    if (!node.getName().equals("customperm")) nodes.add("customperm.command." + node.getName());
                });
            }
            for (String a : CustomPerm.configManager.getAliases().aliases.keySet()) {
                nodes.add("customperm.alias." + a);
            }
            for (GradesConfig.Grade g : CustomPerm.configManager.getGrades().grades.values()) {
                nodes.addAll(g.permissions);
                nodes.addAll(g.deniedPermissions);
            }
            return SharedSuggestionProvider.suggest(nodes, builder);
        };

    /** Perms déjà attribuées au grade nommé par l'argument "grade" (pour removeperm). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRADE_PERMS =
        (ctx, builder) -> {
            String gradeName = StringArgumentType.getString(ctx, "grade");
            GradesConfig.Grade grade = CustomPerm.configManager.getGrades().grades.get(gradeName);
            if (grade == null) return builder.buildFuture();
            return SharedSuggestionProvider.suggest(grade.permissions, builder);
        };

    /** Grades assigned to the player named by the "player" argument (for unassign). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYER_GRADES =
        (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            if (server == null) return builder.buildFuture();
            return GradeAdmin.resolvePlayer(server, StringArgumentType.getString(ctx, "player")).profile()
                .map(profile -> CustomPerm.configManager.getGrades().userGrades.get(profile.getId().toString()))
                .map(list -> SharedSuggestionProvider.suggest(list, builder))
                .orElseGet(builder::buildFuture);
        };

    /** Nodes denied by the grade named by the "grade" argument (for removedeny). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRADE_DENIES =
        (ctx, builder) -> {
            GradesConfig.Grade grade = CustomPerm.configManager.getGrades().grades.get(StringArgumentType.getString(ctx, "grade"));
            if (grade == null) return builder.buildFuture();
            return SharedSuggestionProvider.suggest(grade.deniedPermissions, builder);
        };

    /** Grades the grade named by the "grade" argument already inherits (for parent remove). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRADE_PARENTS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            GradeAdmin.parents(StringArgumentType.getString(ctx, "grade")), builder);

    /** Grades that could become a parent: every other grade it does not already inherit. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PARENT_CANDIDATES =
        (ctx, builder) -> {
            String gradeName = StringArgumentType.getString(ctx, "grade");
            List<String> parents = GradeAdmin.parents(gradeName);
            return SharedSuggestionProvider.suggest(CustomPerm.configManager.getGrades().grades.keySet().stream()
                .filter(name -> !name.equals(gradeName) && !parents.contains(name))
                .toList(), builder);
        };

    /** Grades the grade named by the "grade" argument already refuses (for parent removedeny). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRADE_DENIED_PARENTS =
        (ctx, builder) -> SharedSuggestionProvider.suggest(
            GradeAdmin.deniedParents(StringArgumentType.getString(ctx, "grade")), builder);

    /** Grades that could be refused: every other grade it does not inherit directly or refuse already. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_REFUSABLE_PARENTS =
        (ctx, builder) -> {
            String gradeName = StringArgumentType.getString(ctx, "grade");
            List<String> parents = GradeAdmin.parents(gradeName);
            List<String> refused = GradeAdmin.deniedParents(gradeName);
            return SharedSuggestionProvider.suggest(CustomPerm.configManager.getGrades().grades.keySet().stream()
                .filter(name -> !name.equals(gradeName) && !parents.contains(name) && !refused.contains(name))
                .toList(), builder);
        };

    /** Grades the player named by the "player" argument refuses (for undenygrade). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYER_REFUSED_GRADES =
        (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            if (server == null) return builder.buildFuture();
            return GradeAdmin.resolvePlayer(server, StringArgumentType.getString(ctx, "player")).profile()
                .map(profile -> SharedSuggestionProvider.suggest(UserAdmin.refusedGrades(profile.getId()), builder))
                .orElseGet(builder::buildFuture);
        };

    /** Nodes the player named by the "player" argument carries themselves (for the remove subcommands). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_USER_PERMS = userNodes(false);
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_USER_DENIES = userNodes(true);

    private static SuggestionProvider<CommandSourceStack> userNodes(boolean deny) {
        return (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            if (server == null) return builder.buildFuture();
            return GradeAdmin.resolvePlayer(server, StringArgumentType.getString(ctx, "player")).profile()
                .map(profile -> SharedSuggestionProvider.suggest(UserAdmin.nodes(profile.getId(), deny), builder))
                .orElseGet(builder::buildFuture);
        };
    }

    /** Players online or who joined before: grades can be assigned to offline players. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_KNOWN_PLAYERS =
        (ctx, builder) -> {
            var server = ctx.getSource().getServer();
            if (server == null) return builder.buildFuture();
            return SharedSuggestionProvider.suggest(GradeAdmin.knownPlayerNames(server), builder);
        };

    /** Indices de steps valides (0..n-1) pour l'alias nommé par l'argument "name". */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ALIAS_STEP_INDEX =
        (ctx, builder) -> {
            String name = StringArgumentType.getString(ctx, "name");
            List<String> steps = CustomPerm.configManager.getAliases().aliases.get(name);
            if (steps == null) return builder.buildFuture();
            List<String> indices = new ArrayList<>(steps.size());
            for (int i = 0; i < steps.size(); i++) {
                indices.add(Integer.toString(i));
            }
            return SharedSuggestionProvider.suggest(indices, builder);
        };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("customperm")
                // Op level 2 and an explicit customperm.admin, checked on the player's real op level, never the
                // elevated level an alias step runs at (INVARIANT-503, NFR6). Subcommands that change an area also
                // need its customperm.manage.<area> node. The console is never asked for a node.
                .requires(AdminAccess::canAdminister)
                .then(Commands.literal("grade")
                    .then(Commands.literal("create").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(CustomPermCommand::gradeCreate)))
                    .then(Commands.literal("delete").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .executes(CustomPermCommand::gradeDelete)))
                    .then(Commands.literal("addperm").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_KNOWN_NODES)
                                .executes(CustomPermCommand::gradeAddPerm))))
                    .then(Commands.literal("removeperm").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_GRADE_PERMS)
                                .executes(CustomPermCommand::gradeRemovePerm))))
                    .then(Commands.literal("adddeny").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_KNOWN_NODES)
                                .executes(CustomPermCommand::gradeAddDeny))))
                    .then(Commands.literal("removedeny").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_GRADE_DENIES)
                                .executes(CustomPermCommand::gradeRemoveDeny))))
                    .then(Commands.literal("weight").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("weight", IntegerArgumentType.integer())
                                .executes(CustomPermCommand::gradeWeight))))
                    .then(Commands.literal("parent")
                        .then(Commands.literal("add").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .then(Commands.argument("parent", StringArgumentType.word())
                                    .suggests(SUGGEST_PARENT_CANDIDATES)
                                    .executes(CustomPermCommand::gradeParentAdd))))
                        .then(Commands.literal("remove").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .then(Commands.argument("parent", StringArgumentType.word())
                                    .suggests(SUGGEST_GRADE_PARENTS)
                                    .executes(CustomPermCommand::gradeParentRemove))))
                        .then(Commands.literal("adddeny").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .then(Commands.argument("parent", StringArgumentType.word())
                                    .suggests(SUGGEST_REFUSABLE_PARENTS)
                                    .executes(CustomPermCommand::gradeParentDeny))))
                        .then(Commands.literal("removedeny").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .then(Commands.argument("parent", StringArgumentType.word())
                                    .suggests(SUGGEST_GRADE_DENIED_PARENTS)
                                    .executes(CustomPermCommand::gradeParentAllow))))
                        .then(Commands.literal("list")
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .executes(CustomPermCommand::gradeParentList))))
                    .then(Commands.literal("assign").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .executes(CustomPermCommand::gradeAssign))))
                    .then(Commands.literal("unassign").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYER_GRADES)
                                .executes(CustomPermCommand::gradeUnassign))))
                    .then(Commands.literal("setdefault").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .executes(CustomPermCommand::gradeSetDefault)))
                    .then(Commands.literal("cleardefault").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .executes(CustomPermCommand::gradeClearDefault))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::gradeList)))
                .then(Commands.literal("user")
                    .then(Commands.literal("addperm").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_KNOWN_NODES)
                                .executes(ctx -> userNode(ctx, false, true)))))
                    .then(Commands.literal("removeperm").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_USER_PERMS)
                                .executes(ctx -> userNode(ctx, false, false)))))
                    .then(Commands.literal("adddeny").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_KNOWN_NODES)
                                .executes(ctx -> userNode(ctx, true, true)))))
                    .then(Commands.literal("removedeny").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_USER_DENIES)
                                .executes(ctx -> userNode(ctx, true, false)))))
                    .then(Commands.literal("denygrade").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .executes(ctx -> userGradeRefusal(ctx, true)))))
                    .then(Commands.literal("undenygrade").requires(AdminAccess.manage(PermissionNodes.MANAGE_GRADES))
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYER_REFUSED_GRADES)
                                .executes(ctx -> userGradeRefusal(ctx, false)))))
                    .then(Commands.literal("list")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(SUGGEST_KNOWN_PLAYERS)
                            .executes(CustomPermCommand::userList))))
                .then(Commands.literal("alias")
                    .then(Commands.literal("add").requires(AdminAccess.manage(PermissionNodes.MANAGE_ALIASES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("commands", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::aliasAdd))))
                    .then(Commands.literal("addstep").requires(AdminAccess.manage(PermissionNodes.MANAGE_ALIASES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::aliasAddStep))))
                    .then(Commands.literal("removestep").requires(AdminAccess.manage(PermissionNodes.MANAGE_ALIASES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                .executes(CustomPermCommand::aliasRemoveStep))))
                    .then(Commands.literal("movestep").requires(AdminAccess.manage(PermissionNodes.MANAGE_ALIASES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("from", IntegerArgumentType.integer(0))
                                .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                .then(Commands.argument("to", IntegerArgumentType.integer(0))
                                    .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                    .executes(CustomPermCommand::aliasMoveStep)))))
                    .then(Commands.literal("setstep").requires(AdminAccess.manage(PermissionNodes.MANAGE_ALIASES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                    .executes(CustomPermCommand::aliasSetStep)))))
                    .then(Commands.literal("steps")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .executes(CustomPermCommand::aliasSteps)))
                    .then(Commands.literal("remove").requires(AdminAccess.manage(PermissionNodes.MANAGE_ALIASES))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .executes(CustomPermCommand::aliasRemove)))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::aliasList)))
                .then(Commands.literal("command")
                    .then(Commands.literal("add").requires(AdminAccess.manage(PermissionNodes.MANAGE_COMMANDS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSABLE_COMMANDS)
                            .executes(CustomPermCommand::commandAdd)))
                    .then(Commands.literal("remove").requires(AdminAccess.manage(PermissionNodes.MANAGE_COMMANDS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSED_COMMANDS)
                            .executes(CustomPermCommand::commandRemove)))
                    .then(Commands.literal("preserve").requires(AdminAccess.manage(PermissionNodes.MANAGE_COMMANDS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSED_COMMANDS)
                            .then(Commands.argument("keepOriginal", BoolArgumentType.bool())
                                .executes(CustomPermCommand::commandPreserve))))
                    .then(Commands.literal("gateall").requires(AdminAccess.manage(PermissionNodes.MANAGE_COMMANDS))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(ctx -> report(ctx, CommandAdmin.setGateAll(ctx.getSource().getServer(),
                                BoolArgumentType.getBool(ctx, "enabled"))))))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::commandList)))
                .then(Commands.literal("ratelimit")
                    .then(Commands.literal("set").requires(AdminAccess.manage(PermissionNodes.MANAGE_RATELIMITS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSED_AND_ALIASES)
                            .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .then(Commands.argument("windowSeconds", IntegerArgumentType.integer(1))
                                    .executes(CustomPermCommand::rateLimitSet)))))
                    .then(Commands.literal("persistence").requires(AdminAccess.manage(PermissionNodes.MANAGE_RATELIMITS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    List.of(RateLimitsConfig.PERSISTENCE_WORLD_SAVE, RateLimitsConfig.PERSISTENCE_IMMEDIATE), builder))
                                .executes(CustomPermCommand::rateLimitPersistence))))
                    .then(Commands.literal("enable").requires(AdminAccess.manage(PermissionNodes.MANAGE_RATELIMITS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .executes(CustomPermCommand::rateLimitEnable)))
                    .then(Commands.literal("disable").requires(AdminAccess.manage(PermissionNodes.MANAGE_RATELIMITS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .executes(CustomPermCommand::rateLimitDisable)))
                    .then(Commands.literal("remove").requires(AdminAccess.manage(PermissionNodes.MANAGE_RATELIMITS))
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .executes(CustomPermCommand::rateLimitRemove)))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::rateLimitList)))
                .then(Commands.literal("test")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("node", StringArgumentType.greedyString())
                            .suggests(SUGGEST_KNOWN_NODES)
                            .executes(CustomPermCommand::testPerm))))
                .then(Commands.literal("debug")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_ONLINE_PLAYERS)
                        .then(Commands.argument("command", StringArgumentType.word())
                            .suggests(SUGGEST_ALL_COMMANDS)
                            .executes(CustomPermCommand::debugCheck))))
                .then(Commands.literal("status")
                    .executes(CustomPermCommand::status))
                .then(Commands.literal("scan")
                    .executes(CustomPermCommand::scanAll)
                    .then(Commands.argument("pattern", StringArgumentType.word())
                        .suggests(SUGGEST_ALL_COMMANDS)
                        .executes(CustomPermCommand::scanPattern)))
                .then(Commands.literal("reload").requires(AdminAccess.manage(PermissionNodes.MANAGE_CONFIG))
                    .executes(CustomPermCommand::reload))
                .then(logCommand())
                .then(guiCommand())
        );
    }

    // ---------------- activity log ----------------

    private static final int LOG_LINES_DEFAULT = 10;
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** {@code /customperm log admin|players [count]}, {@code log record|mask <true|false>}. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> logCommand() {
        return Commands.literal("log")
            .then(Commands.literal("admin")
                .executes(ctx -> showLog(ctx, LogKind.ADMIN, LOG_LINES_DEFAULT))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> showLog(ctx, LogKind.ADMIN, IntegerArgumentType.getInteger(ctx, "count")))))
            .then(Commands.literal("players")
                .executes(ctx -> showLog(ctx, LogKind.PLAYERS, LOG_LINES_DEFAULT))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> showLog(ctx, LogKind.PLAYERS, IntegerArgumentType.getInteger(ctx, "count")))))
            .then(Commands.literal("record").requires(AdminAccess.manage(PermissionNodes.MANAGE_LOGS))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> report(ctx, LogAdmin.setPlayerLog(BoolArgumentType.getBool(ctx, "enabled"))))))
            .then(Commands.literal("mask").requires(AdminAccess.manage(PermissionNodes.MANAGE_LOGS))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> report(ctx, LogAdmin.setMasking(BoolArgumentType.getBool(ctx, "enabled"))))));
    }

    /** Prints the latest entries oldest first, so the newest ends up just above the chat input. */
    private static int showLog(CommandContext<CommandSourceStack> ctx, LogKind kind, int count) {
        List<LogEntry> entries = new ArrayList<>(ActivityLog.recent(kind, count));
        java.util.Collections.reverse(entries);
        var settings = CustomPerm.configManager.getSettings();
        if (kind == LogKind.PLAYERS && !settings.playerCommandLog) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "The player command log is off. /customperm log record true turns it on.").withStyle(ChatFormatting.GRAY), false);
        }
        if (entries.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No entry recorded yet."), false);
            return 1;
        }
        for (LogEntry entry : entries) {
            String when = LOG_TIME.format(Instant.ofEpochMilli(entry.time()).atZone(ZoneId.systemDefault()));
            String line = "[" + when + "] " + entry.actor()
                + (kind == LogKind.ADMIN ? " (" + entry.source() + ")" : "") + " " + sanitizePlain(entry.action())
                + (entry.result().isEmpty() ? "" : " -> " + sanitizePlain(entry.result()));
            ChatFormatting color = entry.success() ? ChatFormatting.WHITE : ChatFormatting.RED;
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(color), false);
        }
        return 1;
    }

    // ---------------- admin interface ----------------

    /** {@code /customperm gui [page]}: opens the admin interface on the player's client. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> guiCommand() {
        var gui = Commands.literal("gui").executes(ctx -> openGui(ctx, GuiPage.DASHBOARD));
        for (GuiPage page : GuiPage.values()) {
            if (page == GuiPage.LUCKPERMS) continue;
            gui.then(Commands.literal(page.id()).executes(ctx -> openGui(ctx, page)));
        }
        var luckperms = Commands.literal(GuiPage.LUCKPERMS.id())
            .executes(ctx -> openLuckPermsEditor(ctx, LuckPermsData.GROUPS));
        for (String section : List.of(LuckPermsData.GROUPS, LuckPermsData.PLAYERS, LuckPermsData.TRACKS)) {
            luckperms.then(Commands.literal(section).executes(ctx -> openLuckPermsEditor(ctx, section)));
        }
        return gui.then(luckperms);
    }

    private static int openLuckPermsEditor(CommandContext<CommandSourceStack> ctx, String section) {
        if (!CustomPerm.isLuckPermsPresent()) {
            ctx.getSource().sendFailure(Component.literal(
                "The LuckPerms editor is only available when LuckPerms is installed. Permissions here come from CustomPerm grades: /customperm gui grades."));
            return 0;
        }
        ServerPlayer player = guiPlayer(ctx);
        if (player == null) return 0;
        GuiRequestHandler.openLuckPerms(player, section);
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx, GuiPage page) {
        ServerPlayer player = guiPlayer(ctx);
        if (player == null) return 0;
        GuiRequestHandler.open(player, page);
        return 1;
    }

    /** The player an interface command opens on, or {@code null} after telling the source why not. */
    private static ServerPlayer guiPlayer(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(
                "The admin interface opens on a player's screen. From the console, use the text commands."));
            return null;
        }
        if (!GuiRequestHandler.clientSupportsInterface(player)) {
            ctx.getSource().sendFailure(Component.literal(
                "The admin interface needs CustomPerm installed on your client. Every setting is also available through the /customperm text commands."));
            return null;
        }
        return player;
    }

    // ---------------- command exposure ----------------

    private static int commandAdd(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, CommandAdmin.expose(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name")));
    }

    private static int commandRemove(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, CommandAdmin.hide(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name")));
    }

    private static int commandPreserve(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, CommandAdmin.setPreserveOriginal(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "keepOriginal")));
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

    // ---------------- rate limits ----------------

    private static int rateLimitSet(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, RateLimitAdmin.set(StringArgumentType.getString(ctx, "name"),
            IntegerArgumentType.getInteger(ctx, "max"), IntegerArgumentType.getInteger(ctx, "windowSeconds")));
    }

    private static int rateLimitPersistence(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, RateLimitAdmin.setPersistence(StringArgumentType.getString(ctx, "name"),
            StringArgumentType.getString(ctx, "mode")));
    }

    private static int rateLimitEnable(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, RateLimitAdmin.enable(StringArgumentType.getString(ctx, "name")));
    }

    private static int rateLimitDisable(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, RateLimitAdmin.disable(StringArgumentType.getString(ctx, "name")));
    }

    private static int rateLimitRemove(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, RateLimitAdmin.remove(StringArgumentType.getString(ctx, "name")));
    }

    private static int rateLimitList(CommandContext<CommandSourceStack> ctx) {
        var rules = CustomPerm.configManager.getRateLimits().rules;
        if (rules.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "No rate limits configured. Use /customperm ratelimit set <name> <max> <windowSeconds>."), false);
            return 1;
        }
        rules.forEach((name, rule) -> {
            String status = rule.enabled ? "enabled" : "disabled";
            ChatFormatting color = rule.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            ctx.getSource().sendSuccess(() -> Component.literal(
                "/" + name + "  " + rule.maxExecutions + " per " + rule.windowSeconds + "s  [" + status + "]"
                    + "  persistence=" + rule.persistence
            ).withStyle(color), false);
        });
        return 1;
    }

    // ---------------- grade ----------------

    private static int gradeCreate(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, GradeAdmin.create(StringArgumentType.getString(ctx, "name")));
    }

    private static int gradeDelete(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.delete(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"))));
    }

    private static int gradeAddPerm(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.addNode(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "node"), false)));
    }

    private static int gradeRemovePerm(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.removeNode(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "node"), false)));
    }

    private static int gradeAddDeny(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.addNode(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "node"), true)));
    }

    private static int gradeRemoveDeny(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.removeNode(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "node"), true)));
    }

    private static int gradeWeight(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.setWeight(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), IntegerArgumentType.getInteger(ctx, "weight"))));
    }

    private static int gradeParentAdd(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.addParent(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "parent"))));
    }

    private static int gradeParentRemove(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.removeParent(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "parent"))));
    }

    private static int gradeParentDeny(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.denyParent(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "parent"))));
    }

    private static int gradeParentAllow(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.allowParent(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"), StringArgumentType.getString(ctx, "parent"))));
    }

    private static int gradeParentList(CommandContext<CommandSourceStack> ctx) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        String gradeName = StringArgumentType.getString(ctx, "grade");
        if (!CustomPerm.configManager.getGrades().grades.containsKey(gradeName)) {
            return report(ctx, AdminResult.fail("No such grade: " + gradeName));
        }
        List<String> parents = GradeAdmin.parents(gradeName);
        List<String> refused = GradeAdmin.deniedParents(gradeName);
        ctx.getSource().sendSuccess(() -> Component.literal(parents.isEmpty()
            ? gradeName + " inherits nothing."
            : gradeName + " inherits: " + String.join(", ", parents)), false);
        if (!refused.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  refuses: " + String.join(", ", refused)), false);
        }
        return 1;
    }

    private static int gradeSetDefault(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.setDefault(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "grade"))));
    }

    private static int gradeClearDefault(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, guarded(ctx, () -> GradeAdmin.setDefault(ctx.getSource().getServer(), "")));
    }

    /** Refuses a grade change that would lock the admin running it out of /customperm. */
    private static AdminResult guarded(CommandContext<CommandSourceStack> ctx, java.util.function.Supplier<AdminResult> change) {
        return GradeAdmin.guarded(ctx.getSource(), ctx.getSource().getServer(), change);
    }

    private static int gradeAssign(CommandContext<CommandSourceStack> ctx) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        var server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        if (server == null) return 0;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(server, name);
        var profile = resolution.profile();
        if (profile.isEmpty()) return report(ctx, AdminResult.fail(resolution.problem()));
        return report(ctx, guarded(ctx, () -> GradeAdmin.assign(server, profile.get(), StringArgumentType.getString(ctx, "grade"))));
    }

    private static int gradeUnassign(CommandContext<CommandSourceStack> ctx) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        var server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "player");
        if (server == null) return 0;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(server, name);
        var profile = resolution.profile();
        if (profile.isEmpty()) return report(ctx, AdminResult.fail(resolution.problem()));
        return report(ctx, guarded(ctx, () -> GradeAdmin.unassign(server, profile.get().getId(), profile.get().getName(),
            StringArgumentType.getString(ctx, "grade"))));
    }

    private static int gradeList(CommandContext<CommandSourceStack> ctx) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        Map<String, GradesConfig.Grade> defined = CustomPerm.configManager.getGrades().grades;
        if (defined.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No grades defined."), false);
        } else {
            // Heaviest first: that is the order in which they break a tie on the same node.
            String list = defined.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, GradesConfig.Grade> e) -> -e.getValue().weight)
                    .thenComparing(Map.Entry::getKey))
                .map(e -> e.getValue().weight == 0 ? e.getKey() : e.getKey() + " (weight " + e.getValue().weight + ")")
                .collect(Collectors.joining(", "));
            ctx.getSource().sendSuccess(() -> Component.literal("Grades: " + list), false);
        }
        return 1;
    }

    // ---------------- user ----------------

    /** Adds or removes one node carried by a player themselves, above their grades. */
    private static int userNode(CommandContext<CommandSourceStack> ctx, boolean deny, boolean add) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        var server = ctx.getSource().getServer();
        if (server == null) return 0;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(server, StringArgumentType.getString(ctx, "player"));
        var profile = resolution.profile();
        if (profile.isEmpty()) return report(ctx, AdminResult.fail(resolution.problem()));
        String node = StringArgumentType.getString(ctx, "node");
        return report(ctx, guarded(ctx, () -> add
            ? UserAdmin.addNode(server, profile.get().getId(), profile.get().getName(), node, deny)
            : UserAdmin.removeNode(server, profile.get().getId(), profile.get().getName(), node, deny)));
    }

    /** Makes a player refuse a grade, or stop refusing it. */
    private static int userGradeRefusal(CommandContext<CommandSourceStack> ctx, boolean refuse) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        var server = ctx.getSource().getServer();
        if (server == null) return 0;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(server, StringArgumentType.getString(ctx, "player"));
        var profile = resolution.profile();
        if (profile.isEmpty()) return report(ctx, AdminResult.fail(resolution.problem()));
        String grade = StringArgumentType.getString(ctx, "grade");
        return report(ctx, guarded(ctx, () -> refuse
            ? UserAdmin.refuseGrade(server, profile.get().getId(), profile.get().getName(), grade)
            : UserAdmin.acceptGrade(server, profile.get().getId(), profile.get().getName(), grade)));
    }

    /** What one player holds: their grades, then the nodes they carry themselves. */
    private static int userList(CommandContext<CommandSourceStack> ctx) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return report(ctx, refusal);
        var server = ctx.getSource().getServer();
        if (server == null) return 0;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(server, StringArgumentType.getString(ctx, "player"));
        var profile = resolution.profile();
        if (profile.isEmpty()) return report(ctx, AdminResult.fail(resolution.problem()));
        String name = profile.get().getName();
        java.util.UUID uuid = profile.get().getId();
        List<String> assigned = CustomPerm.configManager.getGrades().userGrades
            .getOrDefault(uuid.toString(), List.of());
        List<String> refused = UserAdmin.refusedGrades(uuid);
        ctx.getSource().sendSuccess(() -> Component.literal(name + " — grades: " + join(assigned)), false);
        if (!refused.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  refuses: " + String.join(", ", refused)), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("  own allow: " + join(UserAdmin.nodes(uuid, false))), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  own deny : " + join(UserAdmin.nodes(uuid, true))), false);
        return 1;
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    // ---------------- alias ----------------

    private static int aliasAdd(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "commands");
        return report(ctx, AliasAdmin.define(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "name"), Arrays.asList(raw.split(";"))));
    }

    private static int aliasAddStep(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, AliasAdmin.addStep(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "command")));
    }

    private static int aliasRemoveStep(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, AliasAdmin.removeStep(ctx.getSource().getServer(),
            StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "index")));
    }

    private static int aliasMoveStep(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, AliasAdmin.moveStep(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"),
            IntegerArgumentType.getInteger(ctx, "from"), IntegerArgumentType.getInteger(ctx, "to")));
    }

    private static int aliasSetStep(CommandContext<CommandSourceStack> ctx) {
        return report(ctx, AliasAdmin.setStep(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"),
            IntegerArgumentType.getInteger(ctx, "index"), StringArgumentType.getString(ctx, "command")));
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
        return report(ctx, AliasAdmin.remove(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name")));
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

        boolean directCommandsEnabled = CustomPerm.isDirectCommandExposureEnabled();
        boolean configuredAsGranted = CustomPerm.configManager.getCommands().grantedCommands.contains(cmd);
        boolean inGrantedList = directCommandsEnabled && configuredAsGranted;
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
        Tristate explicitValue = AdminAccess.explicit(source, permNode);
        boolean preserveOriginalRequires = CustomPerm.configManager.getCommands().shouldPreserveOriginalRequires(cmd);

        var rootNode = server.getCommands().getDispatcher().getRoot().getChildren()
            .stream().filter(n -> n.getName().equals(cmd)).findFirst().orElse(null);
        boolean actualWrapper = false;
        if (rootNode != null) {
            try {
                actualWrapper = rootNode.canUse(source);
            } catch (Throwable ignored) {}
        }

        // What CommandTreeRewriter.decide should answer, or null when the command's own requirement decides.
        boolean gated = inGrantedList || CustomPerm.gatesAllCommands();
        boolean keepOriginal = inGrantedList && preserveOriginalRequires;
        Boolean expected = !gated ? null : switch (explicitValue) {
            case DENY -> false;
            case ALLOW -> keepOriginal ? null : Boolean.TRUE;
            case UNSET -> !inGrantedList ? null
                : op2 ? (keepOriginal ? null : Boolean.TRUE)
                : (CustomPerm.isLuckPermsPresent() ? null : Boolean.FALSE);
        };
        boolean comparableDecision = expected != null;
        boolean shouldPass = comparableDecision && expected;
        String gating = CustomPerm.isLuckPermsPresent() ? "exposed commands (LuckPerms checks the others)"
            : CustomPerm.gatesAllCommands() ? "every command (gateAllCommands)" : "exposed commands only";

        String backend = CustomPerm.backendLabel();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "=== Debug for /" + cmd + " (" + player.getGameProfile().getName() + ") [backend: " + backend + "] ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Command exists in dispatcher : " + (rootNode != null)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Direct command exposure     : "
                + (directCommandsEnabled ? "enabled" : "disabled (LuckPerms installed)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  In granted-commands list    : " + inGrantedList), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  CustomPerm gates            : " + gating), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Source has op level 2       : " + op2), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Source has op level 4       : " + op4), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Explicit value of " + permNode + " : " + explicitValue), false);
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
        CommandSourceStack source = player.createCommandSourceStack();
        Tristate value = AdminAccess.explicit(source, node);
        boolean granted = value == Tristate.ALLOW || (value == Tristate.UNSET && source.hasPermission(2));
        String backend = CustomPerm.backendLabel();
        ChatFormatting color = granted ? ChatFormatting.GREEN : ChatFormatting.RED;
        String verdict = granted ? "GRANTED" : "DENIED";
        String reason = value != Tristate.UNSET ? " (explicit " + value + ")"
            : granted ? " (not set, operator)" : " (not set)";
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[" + backend + "] " + player.getGameProfile().getName() + " :: " + node + " -> " + verdict + reason
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
        ctx.getSource().sendSuccess(() -> Component.literal("  Exposed commands   : " + exposed
            + (CustomPerm.isLuckPermsActive() ? " (nodes resolved by LuckPerms)" : "")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Custom aliases     : " + aliases), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Command gating     : " + (CustomPerm.isLuckPermsPresent()
            ? "exposed commands (LuckPerms checks every command)"
            : CustomPerm.gatesAllCommands() ? "every command (gateAllCommands)" : "exposed commands only")), false);

        // AC1 grades-fallback : afficher grades si Internal pur OU si fallback (InternalPermService actif dans les deux cas)
        boolean showGrades = !CustomPerm.isLuckPermsActive();
        if (showGrades) {
            ctx.getSource().sendSuccess(() -> Component.literal("  Internal grades    : " + grades), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  Users with grade   : " + users), false);
            String defaultGrade = CustomPerm.configManager.getSettings().defaultGrade;
            ctx.getSource().sendSuccess(() -> Component.literal("  Default grade      : "
                + (defaultGrade.isEmpty() ? "none" : defaultGrade)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("  (Grades & user perms managed by /lp)").withStyle(ChatFormatting.GRAY), false);
        }

        var alerts = AdminNotifier.activeAlerts();
        if (alerts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  Admin alerts       : none"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("  Admin alerts       : " + alerts.size()).withStyle(ChatFormatting.RED), false);
            alerts.values().forEach(message ->
                ctx.getSource().sendSuccess(() -> AdminNotifier.alertLine(message), false));
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
        return report(ctx, ConfigAdmin.reload(ctx.getSource().getServer()));
    }

    // ---------------- helpers ----------------

    /**
     * Saves the config and tells the admin when the change could not be written. The change
     * stays live in memory either way; what the admin must know is that it will not survive a
     * restart, and that a reload will discard it.
     */
    private static void persist(CommandContext<CommandSourceStack> ctx) {
        String warning = ConfigAdmin.persist();
        if (warning != null) ctx.getSource().sendFailure(Component.literal(warning));
    }

    /** Prints a shared admin result: warnings in red, then the message as success or failure. */
    private static int report(CommandContext<CommandSourceStack> ctx, AdminResult result) {
        ActivityLog.admin(ctx.getSource(), LogEntry.SOURCE_COMMAND, "/" + ctx.getInput(), result);
        result.warnings().forEach(warning -> ctx.getSource().sendFailure(Component.literal(warning)));
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        success(ctx, result.message());
        result.notes().forEach(note ->
            ctx.getSource().sendSuccess(() -> Component.literal(note).withStyle(ChatFormatting.GRAY), false));
        return 1;
    }

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
    }

    private static void resyncCommands(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        if (server != null) server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p));
    }

    private static String sanitizePlain(String input) {
        return input == null ? "" : input.replace('§', '?').replaceAll("\\p{Cntrl}", "?");
    }
}
