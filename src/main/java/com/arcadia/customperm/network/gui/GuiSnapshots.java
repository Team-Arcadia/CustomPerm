/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.admin.UserAdmin;
import com.arcadia.customperm.command.AliasManager;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.notify.AdminNotifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds page data from the live server state. Server thread only: the config maps are mutated by
 * commands and actions on that thread without synchronisation.
 */
public final class GuiSnapshots {

    private GuiSnapshots() {
    }

    public static GuiContext context(ServerPlayer player) {
        return new GuiContext(CustomPerm.backendKind(), GuiAccess.editMask(player),
                AdminNotifier.activeAlerts().size(), CustomPerm.isLuckPermsPresent());
    }

    public static GuiPageData page(GuiPage page, ServerPlayer player) {
        return switch (page) {
            case DASHBOARD -> dashboard(player.getServer());
            case COMMANDS -> commands(player.getServer());
            case ALIASES -> aliases();
            case RATE_LIMITS -> rateLimits();
            case GRADES -> grades(player.getServer());
            case PLAYERS -> players(player.getServer());
            case LUCKPERMS -> new LuckPermsData(LuckPermsData.GROUPS);
            case LOGS -> logs();
        };
    }

    /**
     * Everyone who holds something of their own, plus everyone online: a player with nothing yet is
     * reached by typing their name, not by scrolling a list of every account the server has ever seen.
     */
    static PlayersData players(MinecraftServer server) {
        var config = CustomPerm.configManager.getGrades();
        java.util.Set<String> uuids = new java.util.LinkedHashSet<>(UserAdmin.knownHolders());
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                uuids.add(online.getUUID().toString());
            }
        }

        List<PlayersData.Player> players = new ArrayList<>();
        for (String rawUuid : uuids) {
            if (players.size() == GuiCodecs.SERVER_LIST_MAX) break;
            java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                continue;
            }
            boolean online = server != null && server.getPlayerList().getPlayer(uuid) != null;
            String name = server == null ? rawUuid : GradeAdmin.displayName(server, uuid);
            players.add(new PlayersData.Player(rawUuid, name, online,
                    List.copyOf(config.userGrades.getOrDefault(rawUuid, List.of())),
                    UserAdmin.nodes(uuid, false).stream().limit(PlayersData.NODES_MAX).toList(),
                    UserAdmin.nodes(uuid, true).stream().limit(PlayersData.NODES_MAX).toList()));
        }
        players.sort(java.util.Comparator.comparing((PlayersData.Player p) -> !p.online())
                .thenComparing(PlayersData.Player::name, String.CASE_INSENSITIVE_ORDER));

        List<String> known = server == null ? List.of()
                : GradeAdmin.knownPlayerNames(server).stream().limit(GuiCodecs.SERVER_LIST_MAX).toList();
        return new PlayersData(players, known, CustomPerm.configManager.getSettings().luckPermsFallbackMode);
    }

    static GradesData grades(MinecraftServer server) {
        var config = CustomPerm.configManager.getGrades();
        java.util.Map<String, List<GradesData.Member>> members = new java.util.HashMap<>();
        config.userGrades.forEach((rawUuid, assigned) -> {
            java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                return;
            }
            boolean online = server != null && server.getPlayerList().getPlayer(uuid) != null;
            String name = server == null ? rawUuid : GradeAdmin.displayName(server, uuid);
            for (String grade : assigned) {
                members.computeIfAbsent(grade, k -> new ArrayList<>()).add(new GradesData.Member(rawUuid, name, online));
            }
        });

        // Heaviest first, then by name: the order in which two grades covering a node just as specifically
        // break the tie, so the list itself reads as the precedence.
        List<String> ordered = new ArrayList<>(config.grades.keySet());
        ordered.sort(java.util.Comparator.comparingInt((String n) -> -config.grades.get(n).weight)
                .thenComparing(java.util.function.Function.identity(), String.CASE_INSENSITIVE_ORDER));

        List<GradesData.Grade> grades = new ArrayList<>();
        for (String name : ordered) {
            if (grades.size() == GuiCodecs.SERVER_LIST_MAX) break;
            var grade = config.grades.get(name);
            List<GradesData.Member> assigned = members.getOrDefault(name, new ArrayList<>());
            assigned.sort(java.util.Comparator.comparing(GradesData.Member::name, String.CASE_INSENSITIVE_ORDER));
            grades.add(new GradesData.Grade(name, grade.weight,
                    new TreeSet<>(grade.permissions).stream().limit(GradesData.NODES_MAX).toList(),
                    new TreeSet<>(grade.deniedPermissions).stream().limit(GradesData.NODES_MAX).toList(),
                    assigned.stream().limit(GuiCodecs.SERVER_LIST_MAX).toList()));
        }
        List<String> known = server == null ? List.of()
                : GradeAdmin.knownPlayerNames(server).stream().limit(GuiCodecs.SERVER_LIST_MAX).toList();
        var settings = CustomPerm.configManager.getSettings();
        return new GradesData(grades, known, settings.luckPermsFallbackMode, settings.defaultGrade,
                CustomPerm.gatesAllCommands());
    }

    static LogsData logs() {
        var settings = CustomPerm.configManager.getSettings();
        return new LogsData(entries(LogKind.ADMIN), entries(LogKind.PLAYERS), settings.playerCommandLog,
                settings.maskPlayerCommandArguments, settings.logRetentionDays);
    }

    private static List<LogsData.Entry> entries(LogKind kind) {
        return ActivityLog.recent(kind, LogsData.ENTRIES_MAX).stream()
                .map(e -> new LogsData.Entry(e.time(), e.actor(), e.source(), e.action(), e.success(), e.result()))
                .toList();
    }

    static RateLimitsData rateLimits() {
        ConfigManager config = CustomPerm.configManager;
        Set<String> exposed = config.getCommands().grantedCommands;
        Set<String> aliases = config.getAliases().aliases.keySet();
        var rules = config.getRateLimits().rules;

        List<RateLimitsData.Rule> rows = new ArrayList<>();
        for (String name : new TreeSet<>(rules.keySet())) {
            if (rows.size() == GuiCodecs.SERVER_LIST_MAX) break;
            var rule = rules.get(name);
            RateLimitsData.Target target = aliases.contains(name) ? RateLimitsData.Target.ALIAS
                    : exposed.contains(name) ? RateLimitsData.Target.EXPOSED_COMMAND : RateLimitsData.Target.NONE;
            rows.add(new RateLimitsData.Rule(name, rule.maxExecutions, rule.windowSeconds, rule.enabled,
                    rule.persistsImmediately(), target));
        }
        Set<String> candidates = new TreeSet<>(exposed);
        candidates.addAll(aliases);
        candidates.removeAll(rules.keySet());
        List<String> unlimited = candidates.stream().limit(GuiCodecs.SERVER_LIST_MAX).toList();
        return new RateLimitsData(rows, unlimited);
    }

    static AliasesData aliases() {
        ConfigManager config = CustomPerm.configManager;
        var rules = config.getRateLimits().rules;
        List<AliasesData.Alias> aliases = new ArrayList<>();
        new TreeSet<>(config.getAliases().aliases.keySet()).forEach(name -> {
            if (aliases.size() == GuiCodecs.SERVER_LIST_MAX) return;
            List<String> steps = config.getAliases().aliases.get(name);
            var rule = rules.get(name);
            aliases.add(new AliasesData.Alias(name,
                    List.copyOf(steps.subList(0, Math.min(steps.size(), AliasesData.STEPS_MAX))),
                    AliasManager.shadowsCommand(name),
                    rule == null ? 0 : rule.maxExecutions, rule == null ? 0 : rule.windowSeconds,
                    rule != null && rule.enabled));
        });
        return new AliasesData(aliases);
    }

    static CommandsData commands(MinecraftServer server) {
        ConfigManager config = CustomPerm.configManager;
        Set<String> exposed = config.getCommands().grantedCommands;
        Set<String> aliases = config.getAliases().aliases.keySet();
        var rules = config.getRateLimits().rules;

        Set<String> dispatcher = new TreeSet<>();
        if (server != null) {
            server.getCommands().getDispatcher().getRoot().getChildren().forEach(node -> dispatcher.add(node.getName()));
        }
        dispatcher.remove("customperm");
        Set<String> names = new TreeSet<>(dispatcher);
        names.addAll(exposed);

        List<CommandsData.Row> rows = new ArrayList<>();
        for (String name : names) {
            if (rows.size() == GuiCodecs.SERVER_LIST_MAX) break;
            var rule = rules.get(name);
            rows.add(new CommandsData.Row(name, exposed.contains(name),
                    config.getCommands().shouldPreserveOriginalRequires(name), aliases.contains(name),
                    rule != null && rule.enabled, !dispatcher.contains(name)));
        }
        return new CommandsData(rows, names.size() > rows.size(), config.getSettings().gateAllCommands);
    }

    static DashboardData dashboard(MinecraftServer server) {
        ConfigManager config = CustomPerm.configManager;
        int dispatcher = server == null ? 0 : server.getCommands().getDispatcher().getRoot().getChildren().size();
        var rules = config.getRateLimits().rules;
        int enabledRules = (int) rules.values().stream().filter(rule -> rule.enabled).count();

        List<DashboardData.Alert> alerts = new ArrayList<>();
        AdminNotifier.activeAlerts().forEach((key, message) -> {
            if (alerts.size() < GuiCodecs.ALERTS_MAX) alerts.add(new DashboardData.Alert(key.name(), message));
        });

        return new DashboardData(
                config.getSettings().luckPermsFallbackMode,
                CustomPerm.isLuckPermsPresent(),
                dispatcher,
                CustomPerm.isDirectCommandExposureEnabled() ? config.getCommands().grantedCommands.size() : 0,
                config.getAliases().aliases.size(),
                rules.size(),
                enabledRules,
                config.getGrades().grades.size(),
                config.getGrades().userGrades.size(),
                !config.isDiskWritable(),
                alerts);
    }
}
