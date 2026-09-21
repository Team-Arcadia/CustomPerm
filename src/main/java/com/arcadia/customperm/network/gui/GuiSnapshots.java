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
            case PLAYERS -> players(player.getServer(), player);
            case LUCKPERMS -> new LuckPermsData(LuckPermsData.GROUPS);
            case IMPORT -> importPage(player);
            case LOGS -> logs();
            case HELP -> HelpData.INSTANCE;
        };
    }

    /** What the interface's fields propose; see {@link GuiVocabulary}. */
    public static GuiVocabulary vocabulary(MinecraftServer server) {
        var config = CustomPerm.configManager;
        List<String> metaValues = new ArrayList<>();
        com.arcadia.customperm.admin.KnownNames.metaValues().forEach((key, values) ->
                values.forEach(value -> metaValues.add(key + "\n" + value)));
        return new GuiVocabulary(
                List.copyOf(com.arcadia.customperm.admin.KnownNames.nodes(server)),
                List.copyOf(new TreeSet<>(config.getGrades().grades.keySet())),
                server == null ? List.of() : GradeAdmin.knownPlayerNames(server),
                com.arcadia.customperm.admin.KnownNames.contexts(server),
                com.arcadia.customperm.admin.KnownNames.commandRoots(server),
                List.copyOf(new TreeSet<>(config.getAliases().aliases.keySet())),
                com.arcadia.customperm.cluster.Cluster.memberNames(),
                List.copyOf(com.arcadia.customperm.admin.KnownNames.metaKeys()),
                metaValues,
                List.copyOf(com.arcadia.customperm.admin.KnownNames.chatTexts()));
    }

    /**
     * Everyone who holds something of their own, plus everyone online: a player with nothing yet is
     * reached by typing their name, not by scrolling a list of every account the server has ever seen.
     */
    static PlayersData players(MinecraftServer server, ServerPlayer viewer) {
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
                    new PlayersData.Held(List.copyOf(config.userGrades.getOrDefault(rawUuid, List.of())),
                            List.copyOf(config.userDeniedGrades.getOrDefault(rawUuid, List.of())),
                            ChatLine.of(config.userPrefixEntries.get(rawUuid), config.userSuffixEntries.get(rawUuid),
                                    config.userContexts.get(rawUuid)),
                            timers(config.userPermissionExpiries.get(rawUuid),
                                    config.userDeniedPermissionExpiries.get(rawUuid)),
                            userScoped(uuid),
                            MetaLine.of(config.userMeta.get(rawUuid), config.userMetaExpiries.get(rawUuid),
                                    config.userContexts.get(rawUuid))),
                    UserAdmin.nodes(uuid, false).stream().limit(PlayersData.NODES_MAX).toList(),
                    UserAdmin.nodes(uuid, true).stream().limit(PlayersData.NODES_MAX).toList()));
        }
        players.sort(java.util.Comparator.comparing((PlayersData.Player p) -> !p.online())
                .thenComparing(PlayersData.Player::name, String.CASE_INSENSITIVE_ORDER));

        List<String> known = server == null ? List.of()
                : GradeAdmin.knownPlayerNames(server).stream().limit(GuiCodecs.SERVER_LIST_MAX).toList();
        List<PlayersData.Track> tracks = com.arcadia.customperm.admin.TrackAdmin.names().stream()
                .limit(GuiCodecs.SERVER_LIST_MAX)
                .map(name -> new PlayersData.Track(name, com.arcadia.customperm.admin.TrackAdmin.rungs(name),
                        viewer == null || com.arcadia.customperm.perm.AdminAccess.canMoveOn(
                                viewer.createCommandSourceStack(), name)))
                .toList();
        List<PlayersData.GradeName> gradeNames = new java.util.TreeMap<>(config.grades).entrySet().stream()
                .filter(e -> e.getValue().displayName != null)
                .limit(GuiCodecs.SERVER_LIST_MAX)
                .map(e -> new PlayersData.GradeName(e.getKey(), e.getValue().displayName))
                .toList();
        // Players listed first: the page can only show a nickname on a row it has.
        java.util.Set<String> listed = new java.util.HashSet<>();
        players.forEach(p -> listed.add(p.uuid()));
        List<PlayersData.GradeName> nicknames = new java.util.TreeMap<>(config.userNicknames).entrySet().stream()
                .filter(e -> listed.contains(e.getKey()))
                .limit(GuiCodecs.SERVER_LIST_MAX)
                .map(e -> new PlayersData.GradeName(e.getKey(), e.getValue()))
                .toList();
        return new PlayersData(players, known, CustomPerm.configManager.getSettings().luckPermsFallbackMode,
                nameSettings(), tracks, new PlayersData.Labels(gradeNames, nicknames));
    }

    static GradesData grades(MinecraftServer server) {
        var config = CustomPerm.configManager.getGrades();
        java.util.Map<String, List<GradesData.Member>> members = new java.util.HashMap<>();
        java.util.Map<String, List<GradesData.Member>> refusers = new java.util.HashMap<>();
        byGrade(server, config.userGrades, config.userGradeExpiries, members);
        byGradeScoped(server, config.userContexts, members, false);
        byGrade(server, config.userDeniedGrades, config.userDeniedGradeExpiries, refusers);
        byGradeScoped(server, config.userContexts, refusers, true);

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
            List<GradesData.Member> refusing = refusers.getOrDefault(name, new ArrayList<>());
            refusing.sort(java.util.Comparator.comparing(GradesData.Member::name, String.CASE_INSENSITIVE_ORDER));
            grades.add(new GradesData.Grade(new GradesData.Header(name,
                    grade.displayName == null ? "" : grade.displayName, grade.weight,
                    ChatLine.of(grade.prefixes, grade.suffixes, grade.contexts)),
                    new GradesData.Inheritance(List.copyOf(grade.parents), List.copyOf(grade.deniedParents)),
                    new TreeSet<>(grade.permissions).stream().limit(GradesData.NODES_MAX).toList(),
                    new TreeSet<>(grade.deniedPermissions).stream().limit(GradesData.NODES_MAX).toList(),
                    new GradesData.Members(assigned.stream().limit(GuiCodecs.SERVER_LIST_MAX).toList(),
                            refusing.stream().limit(GuiCodecs.SERVER_LIST_MAX).toList()),
                    new GradesData.Details(gradeTimers(grade), gradeScoped(grade),
                            MetaLine.of(grade.meta, grade.metaExpiries, grade.contexts))));
        }
        List<String> known = server == null ? List.of()
                : GradeAdmin.knownPlayerNames(server).stream().limit(GuiCodecs.SERVER_LIST_MAX).toList();
        var settings = CustomPerm.configManager.getSettings();
        return new GradesData(grades, known, settings.luckPermsFallbackMode, settings.defaultGrade,
                CustomPerm.gatesAllCommands(), nameSettings());
    }

    /** A grade's temporary nodes, then its temporary parents ({@code parent:}) and refusals ({@code refusedParent:}). */
    private static List<Remaining> gradeTimers(com.arcadia.customperm.config.GradesConfig.Grade grade) {
        List<Remaining> timers = new ArrayList<>(timers(grade.permissionExpiries, grade.deniedPermissionExpiries));
        grade.parentExpiries.forEach((parent, at) -> timers.add(new Remaining("parent:" + parent, left(at))));
        grade.deniedParentExpiries.forEach((parent, at) -> timers.add(new Remaining("refusedParent:" + parent, left(at))));
        return timers.size() > GuiCodecs.SERVER_LIST_MAX ? timers.subList(0, GuiCodecs.SERVER_LIST_MAX) : timers;
    }

    /** The nodes of one holder that are temporary, with the seconds each has left. */
    private static List<Remaining> timers(java.util.Map<String, Long> allow, java.util.Map<String, Long> deny) {
        List<Remaining> timers = new ArrayList<>();
        if (allow != null) allow.forEach((node, at) -> timers.add(new Remaining("allow:" + node, left(at))));
        if (deny != null) deny.forEach((node, at) -> timers.add(new Remaining("deny:" + node, left(at))));
        return timers.size() > GuiCodecs.SERVER_LIST_MAX ? timers.subList(0, GuiCodecs.SERVER_LIST_MAX) : timers;
    }

    /** A grade's nodes limited to a world, sorted by world then node. */
    private static List<ScopedEntry> gradeScoped(com.arcadia.customperm.config.GradesConfig.Grade grade) {
        List<ScopedEntry> entries = new ArrayList<>();
        new java.util.TreeMap<>(grade.contexts).forEach((context, scope) -> {
            new TreeSet<>(scope.deniedPermissions).forEach(node -> entries.add(scoped(scope, context, "deny", node)));
            new TreeSet<>(scope.permissions).forEach(node -> entries.add(scoped(scope, context, "allow", node)));
            scope.parents.forEach(parent -> entries.add(scoped(scope, context, "parent", parent)));
            scope.refused.forEach(parent -> entries.add(scoped(scope, context, "refused", parent)));
        });
        return entries.size() > GuiCodecs.SERVER_LIST_MAX ? entries.subList(0, GuiCodecs.SERVER_LIST_MAX) : entries;
    }

    /** What one player holds limited to a world, grades included. */
    private static List<ScopedEntry> userScoped(java.util.UUID uuid) {
        List<ScopedEntry> entries = new ArrayList<>();
        UserAdmin.scoped(uuid).forEach((context, held) -> held.forEach(entry -> {
            int colon = entry.indexOf(':');
            String kind = entry.substring(0, colon);
            String value = entry.substring(colon + 1);
            entries.add(new ScopedEntry(context, kind, value, UserAdmin.remaining(uuid, context, kind, value)));
        }));
        return entries.size() > GuiCodecs.SERVER_LIST_MAX ? entries.subList(0, GuiCodecs.SERVER_LIST_MAX) : entries;
    }

    /** One entry of {@code scope}, with the time it has left. */
    private static ScopedEntry scoped(com.arcadia.customperm.config.GradesConfig.Scoped scope, String context,
                                      String kind, String value) {
        return new ScopedEntry(context, kind, value, left(scope.expiries(kind).get(value)));
    }

    /** Seconds left before {@code at}, 0 for no expiry, and at least 1 for one not swept yet. */
    private static long left(Long at) {
        return at == null ? 0 : Math.max(1, at - com.arcadia.customperm.perm.Expiry.now());
    }

    static NameSettings nameSettings() {
        var settings = CustomPerm.configManager.getSettings();
        return new NameSettings(settings.decorateNames, settings.nameFormat,
                NameSettings.Stack.of(settings.prefixStack), NameSettings.Stack.of(settings.suffixStack));
    }

    /** Turns a UUID-to-grade-names map inside out: one entry per grade, with the players resolved. */
    private static void byGrade(MinecraftServer server, java.util.Map<String, List<String>> assignments,
                                java.util.Map<String, java.util.Map<String, Long>> expiries,
                                java.util.Map<String, List<GradesData.Member>> byGrade) {
        assignments.forEach((rawUuid, names) -> {
            java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                return;
            }
            boolean online = server != null && server.getPlayerList().getPlayer(uuid) != null;
            String name = server == null ? rawUuid : GradeAdmin.displayName(server, uuid);
            java.util.Map<String, Long> timed = expiries.getOrDefault(rawUuid, java.util.Map.of());
            for (String grade : names) {
                byGrade.computeIfAbsent(grade, k -> new ArrayList<>())
                        .add(new GradesData.Member(rawUuid, name, online, left(timed.get(grade)), ""));
            }
        });
    }

    /** The players who hold, or refuse, a grade in one world only, one entry per grade and world. */
    private static void byGradeScoped(MinecraftServer server,
                                      java.util.Map<String, java.util.Map<String, com.arcadia.customperm.config.GradesConfig.UserScoped>> scopes,
                                      java.util.Map<String, List<GradesData.Member>> byGrade, boolean refused) {
        scopes.forEach((rawUuid, byContext) -> {
            java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(rawUuid);
            } catch (IllegalArgumentException e) {
                return;
            }
            boolean online = server != null && server.getPlayerList().getPlayer(uuid) != null;
            String name = server == null ? rawUuid : GradeAdmin.displayName(server, uuid);
            byContext.forEach((context, scope) -> (refused ? scope.refused : scope.grades).forEach(grade -> byGrade
                    .computeIfAbsent(grade, k -> new ArrayList<>())
                    .add(new GradesData.Member(rawUuid, name, online,
                            left((refused ? scope.refusedExpiries : scope.gradeExpiries).get(grade)), context))));
        });
    }

    /** The import page, both ways: what was previewed, if anything. The plans never leave the server. */
    static ImportData importPage(ServerPlayer player) {
        String key = player.getUUID().toString();
        var plan = com.arcadia.customperm.admin.ImportAdmin.previewed(key);
        var export = com.arcadia.customperm.admin.ExportAdmin.previewed(key);
        var progress = com.arcadia.customperm.admin.ExportAdmin.progress();
        return new ImportData(CustomPerm.isLuckPermsActive(), plan != null,
                com.arcadia.customperm.admin.ImportAdmin.previewedWithCommands(key),
                plan == null ? List.of() : plan.report().stream().limit(ImportData.REPORT_MAX).toList(),
                new ImportData.Export(export != null,
                        export == null ? List.of() : export.report().stream().limit(ImportData.REPORT_MAX).toList(),
                        progress.running(), progress.done(), progress.total()));
    }

    static LogsData logs() {
        var settings = CustomPerm.configManager.getSettings();
        return new LogsData(entries(LogKind.ADMIN), entries(LogKind.PLAYERS), settings.playerCommandLog,
                settings.maskPlayerCommandArguments, settings.logRetentionDays);
    }

    private static List<LogsData.Entry> entries(LogKind kind) {
        return ActivityLog.recent(kind, LogsData.ENTRIES_MAX).stream()
                .map(e -> new LogsData.Entry(e.time(), e.actor(), e.source(), e.action(), e.success(), e.result(), e.server()))
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
                    rule.persistsImmediately(), target, rule.scope));
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
                    config.getAliases().parameters(name).stream()
                            .limit(AliasesData.PARAMS_MAX)
                            .map(parameter -> new AliasesData.Param(parameter.name, parameter.type,
                                    parameter.optional, parameter.fallback(),
                                    parameter.min == null ? Integer.MIN_VALUE : parameter.min,
                                    parameter.max == null ? Integer.MAX_VALUE : parameter.max,
                                    List.copyOf(parameter.choices), parameter.allowSelectors))
                            .toList(),
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
                alerts,
                com.arcadia.customperm.cluster.Cluster.summary());
    }
}
