/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.config.GradesConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What an export to LuckPerms would write, and what it would leave out. The other direction of
 * {@link ImportPlan}, and just as plain: no {@code net.luckperms.api} type, so it is built and tested
 * without LuckPerms and only {@code perm/lp/LuckPermsExport} turns it into writes.
 *
 * <p>Nothing is translated. The target is CustomPerm running on the LuckPerms backend, which reads
 * {@code customperm.*} as it is, and LuckPerms expresses everything a grade can say: a grade is a group,
 * its weight a weight, its parents inheritance nodes, a refused parent or a denied node a node set to
 * false. What can be left out is a name LuckPerms would refuse, and whatever names it.
 */
public record ExportPlan(List<Group> groups, List<Player> players, String defaultGrade, List<String> refused,
                         List<String> notes, Set<String> existing, int dropped) {

    /** LuckPerms' own group, which every player is in unless moved out of it. */
    public static final String LP_DEFAULT = "default";

    /**
     * A group name LuckPerms keeps as it is. It lowercases whatever it is given, so an uppercase grade
     * would come back renamed: refused and reported instead, like a name it would reject outright.
     */
    private static final Pattern LP_NAME = Pattern.compile("[a-z0-9_.\\-]{1,36}");

    /** One grade as the group it would become. */
    public record Group(String name, int weight, List<String> parents, List<String> deniedParents,
                        Set<String> allow, Set<String> deny) {

        int entries() {
            return parents.size() + deniedParents.size() + allow.size() + deny.size();
        }
    }

    /** One player as the LuckPerms user they would become, keyed by UUID. */
    public record Player(String uuid, List<String> grades, List<String> deniedGrades,
                         Set<String> allow, Set<String> deny) {

        int entries() {
            return grades.size() + deniedGrades.size() + allow.size() + deny.size();
        }
    }

    /**
     * How a write ended. {@code stoppedAt} is {@code null} when everything was written; otherwise it names
     * the holder that failed, everything before it being written and nothing after it.
     */
    public record Outcome(int groupsWritten, int playersWritten, int kept, String stoppedAt, String error) {

        public boolean complete() {
            return stoppedAt == null;
        }
    }

    public static boolean validName(String name) {
        return name != null && LP_NAME.matcher(name).matches();
    }

    /**
     * Reads the configuration into a plan. Grades come in name order, so two previews of the same file
     * read the same and a failure part way names a point the admin can find again.
     *
     * @param defaultGrade the grade applied to every player, empty for none
     */
    public static ExportPlan of(GradesConfig config, String defaultGrade) {
        List<String> refused = new ArrayList<>();
        Set<String> exported = new HashSet<>();
        for (String name : new TreeMap<>(config.grades).keySet()) {
            if (validName(name)) exported.add(name);
            else refused.add(name);
        }

        List<String> notes = new ArrayList<>();
        int[] dropped = {0};
        List<Group> groups = new ArrayList<>();
        // The map key is the name every lookup uses, so it is the one exported rather than the field.
        for (var entry : new TreeMap<>(config.grades).entrySet()) {
            String name = entry.getKey();
            GradesConfig.Grade grade = entry.getValue();
            if (!exported.contains(name)) continue;
            groups.add(new Group(name, grade.weight,
                    kept(grade.parents, exported, config, dropped, notes, "grade " + name),
                    kept(grade.deniedParents, exported, config, dropped, notes, "grade " + name),
                    Set.copyOf(grade.permissions), Set.copyOf(grade.deniedPermissions)));
        }

        Set<String> holders = new TreeSet<>();
        holders.addAll(config.userGrades.keySet());
        holders.addAll(config.userDeniedGrades.keySet());
        holders.addAll(config.userPermissions.keySet());
        holders.addAll(config.userDeniedPermissions.keySet());
        List<Player> players = new ArrayList<>();
        for (String uuid : holders) {
            if (!isUuid(uuid)) {
                dropped[0]++;
                notes.add("Not exported, " + uuid + " is not a player identifier LuckPerms can store.");
                continue;
            }
            String who = "player " + uuid;
            Player player = new Player(uuid,
                    kept(config.userGrades.getOrDefault(uuid, List.of()), exported, config, dropped, notes, who),
                    kept(config.userDeniedGrades.getOrDefault(uuid, List.of()), exported, config, dropped, notes, who),
                    Set.copyOf(config.userPermissions.getOrDefault(uuid, Set.of())),
                    Set.copyOf(config.userDeniedPermissions.getOrDefault(uuid, Set.of())));
            if (player.entries() > 0) players.add(player);
        }

        String exportedDefault = "";
        if (defaultGrade != null && !defaultGrade.isEmpty()) {
            if (exported.contains(defaultGrade)) {
                exportedDefault = defaultGrade;
            } else {
                dropped[0]++;
                notes.add("The default grade " + defaultGrade + " is not exported, so nothing is added to the "
                        + "LuckPerms default group.");
            }
        }
        return new ExportPlan(List.copyOf(groups), List.copyOf(players), exportedDefault, List.copyOf(refused),
                List.copyOf(notes), Set.of(), dropped[0]);
    }

    /**
     * The grade names a holder refers to that are exported. One that is not would leave an inheritance
     * node to a group LuckPerms does not have, which grants nothing and reads as if it did.
     */
    private static List<String> kept(List<String> names, Set<String> exported, GradesConfig config, int[] dropped,
                                     List<String> notes, String holder) {
        List<String> kept = new ArrayList<>();
        for (String name : names) {
            if (exported.contains(name)) {
                kept.add(name);
                continue;
            }
            dropped[0]++;
            notes.add(config.grades.containsKey(name)
                    ? "Left out on " + holder + ": " + name + " is not exported."
                    : "Left out on " + holder + ": " + name + " is not a grade, so it grants nothing here either.");
        }
        return List.copyOf(kept);
    }

    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The same plan, knowing which of its groups LuckPerms already has. */
    public ExportPlan withExisting(Set<String> groupsInLuckPerms) {
        Set<String> existing = new LinkedHashSet<>();
        for (Group group : groups) {
            if (groupsInLuckPerms.contains(group.name())) existing.add(group.name());
        }
        return new ExportPlan(groups, players, defaultGrade, refused, notes, Set.copyOf(existing), dropped);
    }

    public boolean isEmpty() {
        return groups.isEmpty() && players.isEmpty();
    }

    /**
     * Whether the LuckPerms default group gets the default grade as a parent. Not when there is none, and
     * not when the default grade is named {@code default}: it is then that group itself.
     */
    public boolean carriesDefault() {
        return !defaultGrade.isEmpty() && !defaultGrade.equals(LP_DEFAULT);
    }

    /** Groups, the default group when a default grade is carried to it, then players: what gets written. */
    public int holders() {
        return groups.size() + (carriesDefault() ? 1 : 0) + players.size();
    }

    public int entries() {
        int entries = carriesDefault() ? 1 : 0;
        for (Group group : groups) entries += group.entries();
        for (Player player : players) entries += player.entries();
        return entries;
    }

    /**
     * The grades as the plan would leave them, read by the resolver to know whether an admin would keep
     * their own access once LuckPerms holds only this.
     */
    public GradesConfig asConfig() {
        GradesConfig config = new GradesConfig();
        for (Group source : groups) {
            GradesConfig.Grade grade = new GradesConfig.Grade();
            grade.name = source.name();
            grade.weight = source.weight();
            grade.parents = new ArrayList<>(source.parents());
            grade.deniedParents = new ArrayList<>(source.deniedParents());
            grade.permissions = new HashSet<>(source.allow());
            grade.deniedPermissions = new HashSet<>(source.deny());
            config.grades.put(grade.name, grade);
        }
        for (Player player : players) {
            if (!player.grades().isEmpty()) config.userGrades.put(player.uuid(), new ArrayList<>(player.grades()));
            if (!player.deniedGrades().isEmpty()) {
                config.userDeniedGrades.put(player.uuid(), new ArrayList<>(player.deniedGrades()));
            }
            if (!player.allow().isEmpty()) config.userPermissions.put(player.uuid(), new HashSet<>(player.allow()));
            if (!player.deny().isEmpty()) config.userDeniedPermissions.put(player.uuid(), new HashSet<>(player.deny()));
        }
        return config;
    }

    /** The report, as the lines the admin reads before confirming. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        if (isEmpty()) {
            lines.add("Nothing to export: no grade can become a group and no player holds anything.");
            lines.addAll(refusedLines());
            lines.addAll(notes);
            return lines;
        }
        lines.add(groups.size() + " grade(s) become LuckPerms groups, " + players.size()
                + " player(s) get what they hold.");
        lines.add(entries() + " entrie(s) written as they are: LuckPerms reads customperm nodes the way "
                + "CustomPerm does, so nothing is translated.");
        if (!defaultGrade.isEmpty()) {
            lines.add("The default grade " + defaultGrade + (defaultGrade.equals(LP_DEFAULT)
                    ? " is the LuckPerms default group itself."
                    : " becomes a parent of the LuckPerms default group, which every player is in unless "
                            + "moved out of it."));
        }
        if (!existing.isEmpty()) {
            lines.add("Already in LuckPerms: " + String.join(", ", existing) + ". Adding keeps what they hold; "
                    + "replacing clears their customperm nodes and parents first, never their prefix, suffix, "
                    + "meta or the nodes of other mods.");
        }
        lines.addAll(refusedLines());
        if (dropped > 0) lines.add(dropped + " entrie(s) left out, each named below.");
        lines.addAll(notes);
        lines.add(holders() + " holder(s) to write, about " + 2 * holders() + " storage operations: it runs "
                + "in the background and says where it is.");
        lines.add("Back LuckPerms up first with /lp export <file>. Nothing here can undo an export, and one "
                + "that fails part way leaves LuckPerms half written.");
        return lines;
    }

    private List<String> refusedLines() {
        if (refused.isEmpty()) return List.of();
        return List.of("Not exported, LuckPerms would refuse or rename the name (lowercase letters, digits, "
                + "_ . - only, 36 at most): " + String.join(", ", refused) + ".");
    }
}
