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
import com.arcadia.customperm.admin.RateLimitAdmin;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.LuckPermsService;
import com.arcadia.customperm.perm.PermissionService;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * /customperm command add|remove <name>             # expose / hide a vanilla or modded command
 *             command preserve <name> <true|false>  # also keep the command's original requirement
 *             command list                          # show currently exposed commands
 * /customperm grade   create|delete <name>
 *                     addperm|removeperm <grade> <node>
 *                     assign|unassign <player> <grade>
 *                     list
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
 * /customperm gui [page]                            # open the admin interface (CustomPerm needed client-side)
 *
 * The mod ships with NO commands pre-exposed. Each admin chooses what to expose via
 * /customperm command add. Until exposed, every command keeps its vanilla op-only behaviour.
 *
 * Always requires op level 2 — this is the management command.
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

    /** Nodes de permission connus : customperm.command.*, customperm.alias.* et perms de grades. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_KNOWN_NODES =
        (ctx, builder) -> {
            Set<String> nodes = new TreeSet<>();
            for (String c : CustomPerm.configManager.getCommands().grantedCommands) {
                nodes.add("customperm.command." + c);
            }
            for (String a : CustomPerm.configManager.getAliases().aliases.keySet()) {
                nodes.add("customperm.alias." + a);
            }
            for (GradesConfig.Grade g : CustomPerm.configManager.getGrades().grades.values()) {
                nodes.addAll(g.permissions);
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

    /** Grades déjà assignés au joueur nommé par l'argument "player" (pour unassign). */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYER_GRADES =
        (ctx, builder) -> {
            try {
                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                List<String> list = CustomPerm.configManager.getGrades()
                    .userGrades.get(player.getUUID().toString());
                if (list == null) return builder.buildFuture();
                return SharedSuggestionProvider.suggest(list, builder);
            } catch (CommandSyntaxException e) {
                return builder.buildFuture();
            }
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
                            .suggests(SUGGEST_GRADES)
                            .executes(CustomPermCommand::gradeDelete)))
                    .then(Commands.literal("addperm")
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_KNOWN_NODES)
                                .executes(CustomPermCommand::gradeAddPerm))))
                    .then(Commands.literal("removeperm")
                        .then(Commands.argument("grade", StringArgumentType.word())
                            .suggests(SUGGEST_GRADES)
                            .then(Commands.argument("node", StringArgumentType.greedyString())
                                .suggests(SUGGEST_GRADE_PERMS)
                                .executes(CustomPermCommand::gradeRemovePerm))))
                    .then(Commands.literal("assign")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_GRADES)
                                .executes(CustomPermCommand::gradeAssign))))
                    .then(Commands.literal("unassign")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("grade", StringArgumentType.word())
                                .suggests(SUGGEST_PLAYER_GRADES)
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
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(CustomPermCommand::aliasAddStep))))
                    .then(Commands.literal("removestep")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                .executes(CustomPermCommand::aliasRemoveStep))))
                    .then(Commands.literal("movestep")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .then(Commands.argument("from", IntegerArgumentType.integer(0))
                                .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                .then(Commands.argument("to", IntegerArgumentType.integer(0))
                                    .suggests(SUGGEST_ALIAS_STEP_INDEX)
                                    .executes(CustomPermCommand::aliasMoveStep)))))
                    .then(Commands.literal("setstep")
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
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_ALIASES)
                            .executes(CustomPermCommand::aliasRemove)))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::aliasList)))
                .then(Commands.literal("command")
                    .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSABLE_COMMANDS)
                            .executes(CustomPermCommand::commandAdd)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSED_COMMANDS)
                            .executes(CustomPermCommand::commandRemove)))
                    .then(Commands.literal("preserve")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSED_COMMANDS)
                            .then(Commands.argument("keepOriginal", BoolArgumentType.bool())
                                .executes(CustomPermCommand::commandPreserve))))
                    .then(Commands.literal("list")
                        .executes(CustomPermCommand::commandList)))
                .then(Commands.literal("ratelimit")
                    .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_EXPOSED_AND_ALIASES)
                            .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .then(Commands.argument("windowSeconds", IntegerArgumentType.integer(1))
                                    .executes(CustomPermCommand::rateLimitSet)))))
                    .then(Commands.literal("persistence")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    List.of(RateLimitsConfig.PERSISTENCE_WORLD_SAVE, RateLimitsConfig.PERSISTENCE_IMMEDIATE), builder))
                                .executes(CustomPermCommand::rateLimitPersistence))))
                    .then(Commands.literal("enable")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .executes(CustomPermCommand::rateLimitEnable)))
                    .then(Commands.literal("disable")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(SUGGEST_RATE_LIMITS)
                            .executes(CustomPermCommand::rateLimitDisable)))
                    .then(Commands.literal("remove")
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
                .then(Commands.literal("reload")
                    .executes(CustomPermCommand::reload))
                .then(guiCommand())
        );
    }

    // ---------------- admin interface ----------------

    /** {@code /customperm gui [page]}: opens the admin interface on the player's client. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> guiCommand() {
        var gui = Commands.literal("gui").executes(ctx -> openGui(ctx, GuiPage.DASHBOARD));
        for (GuiPage page : GuiPage.values()) {
            gui.then(Commands.literal(page.id()).executes(ctx -> openGui(ctx, page)));
        }
        return gui;
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx, GuiPage page) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(
                "The admin interface opens on a player's screen. From the console, use the text commands."));
            return 0;
        }
        if (!GuiRequestHandler.clientSupportsInterface(player)) {
            ctx.getSource().sendFailure(Component.literal(
                "The admin interface needs CustomPerm installed on your client. Every setting is also available through the /customperm text commands."));
            return 0;
        }
        GuiRequestHandler.open(player, page);
        return 1;
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
        persist(ctx);
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
        persist(ctx);
        resyncCommands(ctx);
        success(ctx, "Deleted grade " + name);
        return 1;
    }

    private static int gradeAddPerm(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        String gradeName = StringArgumentType.getString(ctx, "grade");
        // trim : greedyString peut embarquer des espaces résiduels — un node " x.y" ne matcherait jamais.
        String node = StringArgumentType.getString(ctx, "node").trim();
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
        persist(ctx);
        resyncCommands(ctx);
        success(ctx, "Added " + node + " -> " + gradeName);
        return 1;
    }

    private static int gradeRemovePerm(CommandContext<CommandSourceStack> ctx) {
        if (warnIfLuckPerms(ctx)) return 0;
        String gradeName = StringArgumentType.getString(ctx, "grade");
        String node = StringArgumentType.getString(ctx, "node").trim();
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
        persist(ctx);
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
        persist(ctx);
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
        persist(ctx);
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
        ctx.getSource().sendSuccess(() -> Component.literal("  Direct command exposure     : "
                + (directCommandsEnabled ? "enabled" : "disabled (LuckPerms installed)")), false);
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
        ctx.getSource().sendSuccess(() -> Component.literal("  Exposed commands   : " + exposed
            + (CustomPerm.isLuckPermsActive() ? " (nodes resolved by LuckPerms)" : "")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Custom aliases     : " + aliases), false);

        // AC1 grades-fallback : afficher grades si Internal pur OU si fallback (InternalPermService actif dans les deux cas)
        boolean showGrades = !CustomPerm.isLuckPermsActive();
        if (showGrades) {
            ctx.getSource().sendSuccess(() -> Component.literal("  Internal grades    : " + grades), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  Users with grade   : " + users), false);
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

    private static void resyncPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        var server = ctx.getSource().getServer();
        if (server != null) server.getCommands().sendCommands(p);
    }

    private static String sanitizePlain(String input) {
        return input == null ? "" : input.replace('§', '?').replaceAll("\\p{Cntrl}", "?");
    }
}
