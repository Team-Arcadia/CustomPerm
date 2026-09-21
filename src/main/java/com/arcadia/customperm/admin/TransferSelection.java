/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What an import from LuckPerms or an export to it carries: who (groups or grades, players, tracks) and what
 * (the kinds of entries). Applied to a plan once it is read, so the admin sees in the report exactly what the
 * selection keeps before anything is written.
 *
 * <p>A reference to a holder left out, a parent or a player's grade, is kept when its target exists on the
 * other side already or is selected too; otherwise it is dropped and the report says so. Nothing is added to
 * the selection behind the admin's back.
 *
 * <p>Pure Java, no Minecraft or LuckPerms class, so it is unit tested directly.
 *
 * @param groups  the groups (import) or grades (export) carried; {@code null} for every one
 * @param players the players carried, by UUID; {@code null} for every one
 * @param tracks  the tracks carried; {@code null} for every one
 * @param kinds   the kinds of entries carried
 */
public record TransferSelection(Set<String> groups, Set<String> players, Set<String> tracks, Set<Kind> kinds) {

    /** A kind of entry a holder carries. */
    public enum Kind {
        /** Allowed and denied nodes. */
        NODES,
        /** A grade's parents and refused parents; a player's grades and refused grades. */
        PARENTS,
        /** Prefixes and suffixes. */
        CHAT,
        /** Meta values. */
        META,
        /** Entries limited to a context, of any of the kinds above. */
        CONTEXTUAL,
        /** Entries with an expiry, of any of the kinds above. */
        TEMPORARY,
        /** Import only: exposing the commands the imported nodes need. */
        COMMANDS;

        /** The word the text commands and the interface use: {@code nodes}, {@code contexts}... */
        public String word() {
            return this == CONTEXTUAL ? "contexts" : name().toLowerCase(Locale.ROOT);
        }

        public static Kind of(String word) {
            for (Kind kind : values()) {
                if (kind.word().equalsIgnoreCase(word.trim())) return kind;
            }
            return null;
        }
    }

    /** Everything, which is what an import or an export carried before selections existed. */
    public static final TransferSelection ALL = new TransferSelection(null, null, null, EnumSet.allOf(Kind.class));

    public TransferSelection {
        groups = groups == null ? null : Set.copyOf(groups);
        players = players == null ? null : Set.copyOf(players);
        tracks = tracks == null ? null : Set.copyOf(tracks);
        kinds = kinds.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kinds));
    }

    public boolean everything() {
        return groups == null && players == null && tracks == null && kinds.containsAll(EnumSet.allOf(Kind.class));
    }

    public boolean has(Kind kind) {
        return kinds.contains(kind);
    }

    public TransferSelection withGroups(Set<String> chosen) {
        return new TransferSelection(chosen, players, tracks, kinds);
    }

    public TransferSelection withPlayers(Set<String> chosen) {
        return new TransferSelection(groups, chosen, tracks, kinds);
    }

    public TransferSelection withTracks(Set<String> chosen) {
        return new TransferSelection(groups, players, chosen, kinds);
    }

    public TransferSelection withKind(Kind kind, boolean on) {
        EnumSet<Kind> next = kinds.isEmpty() ? EnumSet.noneOf(Kind.class) : EnumSet.copyOf(kinds);
        if (on) next.add(kind);
        else next.remove(kind);
        return new TransferSelection(groups, players, tracks, next);
    }

    // ------------------------------------------------------------------ editing

    /**
     * What a selection chooses among: the groups or grades, the players by UUID with the name shown for them, and
     * the tracks of the plan read.
     */
    public record Candidates(List<String> groups, Map<String, String> players, List<String> tracks) {
    }

    /** The selection after an edit, or the reason it was refused (then {@code next} is the one before). */
    public record Edit(TransferSelection next, String problem) {
    }

    /**
     * {@code what} ({@code groups}, {@code players}, {@code tracks} or {@code kinds}), changed by {@code op}
     * ({@code set}, {@code add} or {@code remove}) with {@code value}: {@code all}, {@code none}, or names separated
     * by spaces or commas. A player is named by their name or their UUID. An unknown name refuses the whole edit,
     * so a typo never silently selects less than the admin meant.
     */
    public Edit edit(String what, String op, String value, Candidates candidates) {
        String action = op.trim().toLowerCase(Locale.ROOT);
        if (!action.equals("set") && !action.equals("add") && !action.equals("remove")) {
            return new Edit(this, "Unknown operation '" + op + "': set, add or remove.");
        }
        String raw = value == null ? "" : value.trim();
        boolean all = raw.equalsIgnoreCase("all");
        boolean none = raw.equalsIgnoreCase("none") || raw.isEmpty();
        if ((all || none) && !action.equals("set")) {
            return new Edit(this, "'all' and 'none' go with set.");
        }
        List<String> words = all || none ? List.of() : List.of(raw.split("[,\\s]+"));
        switch (what.trim().toLowerCase(Locale.ROOT)) {
            case "kinds" -> {
                EnumSet<Kind> chosen = EnumSet.noneOf(Kind.class);
                List<String> unknown = new ArrayList<>();
                for (String word : words) {
                    Kind kind = Kind.of(word);
                    if (kind == null) unknown.add(word);
                    else chosen.add(kind);
                }
                if (!unknown.isEmpty()) {
                    List<String> known = new ArrayList<>();
                    for (Kind kind : Kind.values()) known.add(kind.word());
                    return new Edit(this, "Unknown kind(s): " + String.join(", ", unknown) + ". Kinds: "
                            + String.join(", ", known) + ".");
                }
                EnumSet<Kind> next = kinds.isEmpty() ? EnumSet.noneOf(Kind.class) : EnumSet.copyOf(kinds);
                if (action.equals("set")) next = all ? EnumSet.allOf(Kind.class) : chosen;
                else if (action.equals("add")) next.addAll(chosen);
                else next.removeAll(chosen);
                return new Edit(new TransferSelection(groups, players, tracks, next), null);
            }
            case "groups" -> {
                Edit edit = names(groups, action, all, words, candidates.groups(), Map.of(), "group");
                return edit.problem() != null ? edit : new Edit(withGroups(edit.next().groups()), null);
            }
            case "tracks" -> {
                Edit edit = names(tracks, action, all, words, candidates.tracks(), Map.of(), "track");
                return edit.problem() != null ? edit : new Edit(withTracks(edit.next().groups()), null);
            }
            case "players" -> {
                Edit edit = names(players, action, all, words, List.copyOf(candidates.players().keySet()),
                        candidates.players(), "player");
                return edit.problem() != null ? edit : new Edit(withPlayers(edit.next().groups()), null);
            }
            default -> {
                return new Edit(this, "Unknown part '" + what + "': groups, players, tracks or kinds.");
            }
        }
    }

    /** The edited set, carried in {@code groups} of the returned selection; {@code null} for every candidate. */
    private Edit names(Set<String> current, String action, boolean all, List<String> words, List<String> keys,
                       Map<String, String> labels, String what) {
        if (action.equals("set") && all) return new Edit(new TransferSelection(null, null, null, kinds), null);
        Set<String> chosen = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String word : words) {
            String key = match(word, keys, labels);
            if (key == null) unknown.add(word);
            else chosen.add(key);
        }
        if (!unknown.isEmpty()) {
            return new Edit(this, "Unknown " + what + "(s) in what was read: " + String.join(", ", unknown) + ".");
        }
        Set<String> next = new LinkedHashSet<>(current == null ? keys : current);
        if (action.equals("set")) next = chosen;
        else if (action.equals("add")) next.addAll(chosen);
        else next.removeAll(chosen);
        // Every candidate chosen is every one: a plan read again later with one more is then taken whole too.
        boolean everyOne = !keys.isEmpty() && next.containsAll(keys);
        return new Edit(new TransferSelection(everyOne ? null : next, null, null, kinds), null);
    }

    private static String match(String word, List<String> keys, Map<String, String> labels) {
        for (String key : keys) if (key.equalsIgnoreCase(word)) return key;
        for (Map.Entry<String, String> label : labels.entrySet()) {
            if (label.getValue() != null && label.getValue().equalsIgnoreCase(word)) return label.getKey();
        }
        return null;
    }

    /** One line for the report: what is selected, in the admin's words. */
    public String describe(int allGroups, int allPlayers, int allTracks) {
        List<String> kindWords = new ArrayList<>();
        for (Kind kind : Kind.values()) if (kinds.contains(kind)) kindWords.add(kind.word());
        return "Selection: " + part(groups, allGroups, "group") + ", " + part(players, allPlayers, "player") + ", "
                + part(tracks, allTracks, "track") + "; kinds: " + (kindWords.isEmpty() ? "none" : String.join(", ", kindWords)) + ".";
    }

    private static String part(Set<String> chosen, int all, String what) {
        if (chosen == null) return "every " + what;
        return chosen.size() + " of " + all + " " + what + (all == 1 ? "" : "s");
    }

    // ------------------------------------------------------------------ import

    /**
     * {@code plan} as the selection keeps it. {@code existing} are the grades already here, which a kept parent or
     * player's grade may name without being imported.
     */
    public ImportPlan filter(ImportPlan plan, Collection<String> existing) {
        if (everything()) return plan;
        Set<String> targets = new LinkedHashSet<>(existing);
        List<ImportPlan.Grade> grades = new ArrayList<>();
        for (ImportPlan.Grade grade : plan.grades()) {
            if (groups == null || groups.contains(grade.name())) targets.add(grade.name());
        }
        List<String> dropped = new ArrayList<>();
        for (ImportPlan.Grade grade : plan.grades()) {
            if (groups != null && !groups.contains(grade.name())) continue;
            Map<String, Long> expiries = new LinkedHashMap<>();
            grades.add(new ImportPlan.Grade(grade.name(), grade.weight(),
                    references(grade.parents(), "grade:", grade.expiries(), expiries, targets, dropped, "parent of " + grade.name()),
                    references(grade.deniedParents(), "refuse:", grade.expiries(), expiries, targets, dropped,
                            "refused by " + grade.name()),
                    nodes(grade.allow(), "allow:", grade.expiries(), expiries),
                    nodes(grade.deny(), "deny:", grade.expiries(), expiries),
                    chat(grade.chat()), expiries, scoped(grade.scoped(), targets, dropped, grade.name()), meta(grade.meta()),
                    grade.displayName()));
        }
        List<ImportPlan.Player> players = new ArrayList<>();
        for (ImportPlan.Player player : plan.players()) {
            if (this.players != null && !this.players.contains(player.uuid())) continue;
            Map<String, Long> expiries = new LinkedHashMap<>();
            String who = player.name() == null || player.name().isEmpty() ? player.uuid() : player.name();
            players.add(new ImportPlan.Player(player.uuid(), player.name(),
                    references(player.grades(), "grade:", player.expiries(), expiries, targets, dropped, "held by " + who),
                    references(player.deniedGrades(), "refuse:", player.expiries(), expiries, targets, dropped,
                            "refused by " + who),
                    nodes(player.allow(), "allow:", player.expiries(), expiries),
                    nodes(player.deny(), "deny:", player.expiries(), expiries),
                    chat(player.chat()), expiries, scoped(player.scoped(), targets, dropped, who), meta(player.meta())));
        }
        Map<String, List<String>> tracks = new LinkedHashMap<>();
        plan.tracks().forEach((name, rungs) -> {
            if (this.tracks == null || this.tracks.contains(name)) tracks.put(name, rungs);
        });
        Set<String> expose = new TreeSet<>();
        if (has(Kind.COMMANDS)) {
            Set<String> needed = commandsNamed(grades, players);
            for (String command : plan.exposeCommands()) if (needed.contains(command)) expose.add(command);
        }
        List<String> lines = new ArrayList<>(plan.skipped());
        lines.add(0, describe(plan.grades().size(), plan.players().size(), plan.tracks().size()));
        if (!dropped.isEmpty()) {
            lines.add(dropped.size() + " reference(s) to a grade neither selected nor already here are left out: "
                    + String.join(", ", dropped) + ".");
        }
        return new ImportPlan(grades, players, tracks, expose, lines, counts(plan.counts(), grades, players, expose));
    }

    /** The commands the kept nodes name, which are the only ones worth exposing. */
    private static Set<String> commandsNamed(List<ImportPlan.Grade> grades, List<ImportPlan.Player> players) {
        Set<String> named = new TreeSet<>();
        List<Collection<String>> nodes = new ArrayList<>();
        List<ScopedGrant> scoped = new ArrayList<>();
        for (ImportPlan.Grade grade : grades) {
            nodes.add(grade.allow());
            nodes.add(grade.deny());
            scoped.addAll(grade.scoped());
        }
        for (ImportPlan.Player player : players) {
            nodes.add(player.allow());
            nodes.add(player.deny());
            scoped.addAll(player.scoped());
        }
        for (Collection<String> set : nodes) for (String node : set) addCommand(named, node);
        for (ScopedGrant entry : scoped) {
            if (entry.kind().equals(ScopedGrant.ALLOW) || entry.kind().equals(ScopedGrant.DENY)) addCommand(named, entry.value());
        }
        return named;
    }

    private static void addCommand(Set<String> into, String node) {
        String prefix = "customperm.command.";
        if (!node.startsWith(prefix)) return;
        String rest = node.substring(prefix.length());
        int dot = rest.indexOf('.');
        into.add(dot < 0 ? rest : rest.substring(0, dot));
    }

    private static ImportPlan.Counts counts(ImportPlan.Counts before, List<ImportPlan.Grade> grades,
                                            List<ImportPlan.Player> players, Set<String> expose) {
        int nodes = 0;
        int timed = 0;
        int worlds = 0;
        int translated = 0;
        for (ImportPlan.Grade grade : grades) {
            nodes += grade.allow().size() + grade.deny().size();
            timed += grade.expiries().size();
            worlds += grade.scoped().size();
            translated += translated(grade.allow(), expose) + translated(grade.deny(), expose);
        }
        for (ImportPlan.Player player : players) {
            nodes += player.allow().size() + player.deny().size();
            timed += player.expiries().size();
            worlds += player.scoped().size();
            translated += translated(player.allow(), expose) + translated(player.deny(), expose);
        }
        return new ImportPlan.Counts(grades.size(), players.size(), nodes, translated, timed, worlds, expose.size(),
                before.contextual(), before.foreign(), before.other());
    }

    private static int translated(Set<String> nodes, Set<String> expose) {
        int count = 0;
        for (String node : nodes) {
            if (node.startsWith("customperm.command.") && expose.contains(node.substring("customperm.command.".length()))) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ export

    /**
     * {@code plan} as the selection keeps it. A kept parent or player's grade may name a group LuckPerms already
     * has ({@link ExportPlan#existing}, and its {@code default} group) without it being exported.
     */
    public ExportPlan filter(ExportPlan plan) {
        if (everything()) return plan;
        Set<String> targets = new LinkedHashSet<>(plan.existing());
        targets.add(ExportPlan.LP_DEFAULT);
        for (ExportPlan.Group group : plan.groups()) {
            if (groups == null || groups.contains(group.name())) targets.add(group.name());
        }
        List<String> dropped = new ArrayList<>();
        List<ExportPlan.Group> groups = new ArrayList<>();
        for (ExportPlan.Group group : plan.groups()) {
            if (this.groups != null && !this.groups.contains(group.name())) continue;
            Map<String, Long> expiries = new LinkedHashMap<>();
            groups.add(new ExportPlan.Group(group.name(), group.weight(),
                    references(group.parents(), "grade:", group.expiries(), expiries, targets, dropped, "parent of " + group.name()),
                    references(group.deniedParents(), "refuse:", group.expiries(), expiries, targets, dropped,
                            "refused by " + group.name()),
                    nodes(group.allow(), "allow:", group.expiries(), expiries),
                    nodes(group.deny(), "deny:", group.expiries(), expiries),
                    chat(group.chat()), expiries, scoped(group.scoped(), targets, dropped, group.name()), meta(group.meta()),
                    group.displayName()));
        }
        List<ExportPlan.Player> players = new ArrayList<>();
        for (ExportPlan.Player player : plan.players()) {
            if (this.players != null && !this.players.contains(player.uuid())) continue;
            Map<String, Long> expiries = new LinkedHashMap<>();
            players.add(new ExportPlan.Player(player.uuid(),
                    references(player.grades(), "grade:", player.expiries(), expiries, targets, dropped, "held by " + player.uuid()),
                    references(player.deniedGrades(), "refuse:", player.expiries(), expiries, targets, dropped,
                            "refused by " + player.uuid()),
                    nodes(player.allow(), "allow:", player.expiries(), expiries),
                    nodes(player.deny(), "deny:", player.expiries(), expiries),
                    chat(player.chat()), expiries, scoped(player.scoped(), targets, dropped, player.uuid()), meta(player.meta())));
        }
        List<ExportPlan.Track> tracks = new ArrayList<>();
        for (ExportPlan.Track track : plan.tracks()) {
            if (this.tracks != null && !this.tracks.contains(track.name())) continue;
            // A rung naming a group LuckPerms will not have would stop the whole export when it is written.
            List<String> rungs = new ArrayList<>();
            for (String group : track.groups()) {
                if (targets.contains(group)) rungs.add(group);
                else dropped.add(group + " (rung of " + track.name() + ")");
            }
            tracks.add(new ExportPlan.Track(track.name(), rungs));
        }
        // The default group gets the default grade as a parent only when that grade goes too or is already there.
        String defaultGrade = targets.contains(plan.defaultGrade()) ? plan.defaultGrade() : "";
        List<String> notes = new ArrayList<>(plan.notes());
        notes.add(0, describe(plan.groups().size(), plan.players().size(), plan.tracks().size()));
        if (!dropped.isEmpty()) {
            notes.add(dropped.size() + " reference(s) to a group neither selected nor already in LuckPerms are left out: "
                    + String.join(", ", dropped) + ".");
        }
        return new ExportPlan(groups, players, tracks, defaultGrade, plan.refused(), notes, plan.existing(), plan.dropped());
    }

    // ------------------------------------------------------------------ kinds

    /** References kept by the selection; their expiries carried into {@code kept} under {@code prefix}. */
    private List<String> references(List<String> names, String prefix, Map<String, Long> expiries, Map<String, Long> kept,
                                    Set<String> targets, List<String> dropped, String role) {
        if (!has(Kind.PARENTS)) return List.of();
        List<String> out = new ArrayList<>();
        for (String name : names) {
            Long at = expiries.get(prefix + name);
            if (at != null && !has(Kind.TEMPORARY)) continue;
            if (!targets.contains(name)) {
                dropped.add(name + " (" + role + ")");
                continue;
            }
            out.add(name);
            if (at != null) kept.put(prefix + name, at);
        }
        return out;
    }

    private Set<String> nodes(Set<String> nodes, String prefix, Map<String, Long> expiries, Map<String, Long> kept) {
        if (!has(Kind.NODES)) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String node : nodes) {
            Long at = expiries.get(prefix + node);
            if (at != null && !has(Kind.TEMPORARY)) continue;
            out.add(node);
            if (at != null) kept.put(prefix + node, at);
        }
        return out;
    }

    private List<ChatGrant> chat(List<ChatGrant> chat) {
        if (!has(Kind.CHAT)) return List.of();
        return chat.stream().filter(grant -> keeps(grant.context(), grant.expires())).toList();
    }

    private List<MetaGrant> meta(List<MetaGrant> meta) {
        if (!has(Kind.META)) return List.of();
        return meta.stream().filter(grant -> keeps(grant.context(), grant.expires())).toList();
    }

    private List<ScopedGrant> scoped(List<ScopedGrant> scoped, Set<String> targets, List<String> dropped, String holder) {
        if (!has(Kind.CONTEXTUAL)) return List.of();
        List<ScopedGrant> out = new ArrayList<>();
        for (ScopedGrant entry : scoped) {
            if (entry.expires() > 0 && !has(Kind.TEMPORARY)) continue;
            boolean node = entry.kind().equals(ScopedGrant.ALLOW) || entry.kind().equals(ScopedGrant.DENY);
            if (node ? !has(Kind.NODES) : !has(Kind.PARENTS)) continue;
            if (!node && !targets.contains(entry.value())) {
                dropped.add(entry.value() + " (" + holder + ", " + entry.context() + ")");
                continue;
            }
            out.add(entry);
        }
        return out;
    }

    private boolean keeps(String context, long expires) {
        if (context != null && !context.isEmpty() && !has(Kind.CONTEXTUAL)) return false;
        return expires <= 0 || has(Kind.TEMPORARY);
    }
}
