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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public record ExportPlan(List<Group> groups, List<Player> players, List<Track> tracks, String defaultGrade,
                         List<String> refused,
                         List<String> notes, Set<String> existing, int dropped) {

    /** LuckPerms' own group, which every player is in unless moved out of it. */
    public static final String LP_DEFAULT = "default";

    /**
     * A group name LuckPerms keeps as it is. It lowercases whatever it is given, so an uppercase grade
     * would come back renamed: refused and reported instead, like a name it would reject outright.
     */
    private static final Pattern LP_NAME = Pattern.compile("[a-z0-9_.\\-]{1,36}");

    /**
     * One grade as the group it would become; {@code chat} holds its prefixes and suffixes, and
     * {@code expiries} holds its temporary entries keyed {@code allow:<node>}, {@code deny:<node>},
     * {@code grade:<parent>} or {@code refuse:<parent>}, and
     * {@code scoped} its entries limited to a context, written with LuckPerms' contexts.
     */
    public record Group(String name, int weight, List<String> parents, List<String> deniedParents,
                        Set<String> allow, Set<String> deny, List<ChatGrant> chat,
                        Map<String, Long> expiries, List<ScopedGrant> scoped, List<MetaGrant> meta) {

        int entries() {
            return parents.size() + deniedParents.size() + allow.size() + deny.size() + scoped.size() + chat.size()
                    + meta.size();
        }
    }

    /**
     * One player as the LuckPerms user they would become, keyed by UUID; {@code expiries} is keyed
     * {@code allow:}, {@code deny:}, {@code grade:} or {@code refuse:}; {@code scoped} holds the nodes and
     * grades limited to a world.
     */
    public record Player(String uuid, List<String> grades, List<String> deniedGrades,
                         Set<String> allow, Set<String> deny, List<ChatGrant> chat,
                         Map<String, Long> expiries, List<ScopedGrant> scoped, List<MetaGrant> meta) {

        int entries() {
            return grades.size() + deniedGrades.size() + allow.size() + deny.size() + scoped.size() + chat.size()
                    + meta.size();
        }
    }

    /** One track as the LuckPerms track it would become, its groups lowest first. */
    public record Track(String name, List<String> groups) {
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
        long now = com.arcadia.customperm.perm.Expiry.now();
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
            Map<String, Long> expiries = new HashMap<>();
            groups.add(new Group(name, grade.weight,
                    live(kept(grade.parents, exported, config, dropped, notes, "grade " + name),
                            grade.parentExpiries, "grade:", expiries, now),
                    live(kept(grade.deniedParents, exported, config, dropped, notes, "grade " + name),
                            grade.deniedParentExpiries, "refuse:", expiries, now),
                    live(grade.permissions, grade.permissionExpiries, "allow:", expiries, now),
                    live(grade.deniedPermissions, grade.deniedPermissionExpiries, "deny:", expiries, now),
                    chatWorldOnly(ChatGrant.of(grade.prefixes, grade.suffixes, grade.contexts, now), dropped, notes,
                            "grade " + name), Map.copyOf(expiries), worldOnly(ScopedGrant.of(grade.contexts, now),
                    exported, config, dropped, notes, "grade " + name),
                    metaExportable(MetaGrant.of(grade.meta, grade.metaExpiries, grade.contexts, now), dropped, notes,
                            "grade " + name)));
        }

        Set<String> holders = new TreeSet<>();
        holders.addAll(config.userGrades.keySet());
        holders.addAll(config.userDeniedGrades.keySet());
        holders.addAll(config.userPermissions.keySet());
        holders.addAll(config.userDeniedPermissions.keySet());
        holders.addAll(config.userPrefixEntries.keySet());
        holders.addAll(config.userSuffixEntries.keySet());
        holders.addAll(config.userContexts.keySet());
        holders.addAll(config.userMeta.keySet());
        List<Player> players = new ArrayList<>();
        for (String uuid : holders) {
            if (!isUuid(uuid)) {
                dropped[0]++;
                notes.add("Not exported, " + uuid + " is not a player identifier LuckPerms can store.");
                continue;
            }
            String who = "player " + uuid;
            Map<String, Long> expiries = new HashMap<>();
            List<String> held = kept(live(config.userGrades.getOrDefault(uuid, List.of()),
                    config.userGradeExpiries.get(uuid), "grade:", expiries, now), exported, config, dropped, notes, who);
            List<String> refusing = kept(live(config.userDeniedGrades.getOrDefault(uuid, List.of()),
                    config.userDeniedGradeExpiries.get(uuid), "refuse:", expiries, now), exported, config, dropped, notes, who);
            // An entry named in expiries but left out above would be written for nothing: keep only the used ones.
            expiries.keySet().removeIf(key -> (key.startsWith("grade:") && !held.contains(key.substring(6)))
                    || (key.startsWith("refuse:") && !refusing.contains(key.substring(7))));
            Player player = new Player(uuid, held, refusing,
                    live(config.userPermissions.getOrDefault(uuid, Set.of()), config.userPermissionExpiries.get(uuid),
                            "allow:", expiries, now),
                    live(config.userDeniedPermissions.getOrDefault(uuid, Set.of()),
                            config.userDeniedPermissionExpiries.get(uuid), "deny:", expiries, now),
                    chatWorldOnly(ChatGrant.of(config.userPrefixEntries.get(uuid), config.userSuffixEntries.get(uuid),
                            config.userContexts.get(uuid), now), dropped, notes, who),
                    Map.copyOf(expiries),
                    worldOnly(ScopedGrant.of(config.userContexts.getOrDefault(uuid, Map.of()), now), exported, config,
                            dropped, notes, who),
                    metaExportable(MetaGrant.of(config.userMeta.get(uuid), config.userMetaExpiries.get(uuid),
                            config.userContexts.get(uuid), now), dropped, notes, who));
            if (player.entries() > 0) players.add(player);
        }

        List<Track> tracks = new ArrayList<>();
        for (var entry : new TreeMap<>(config.tracks).entrySet()) {
            String who = "track " + entry.getKey();
            if (!validName(entry.getKey())) {
                dropped[0]++;
                notes.add("Not exported, LuckPerms would refuse or rename the track " + entry.getKey() + ".");
                continue;
            }
            tracks.add(new Track(entry.getKey(), kept(entry.getValue(), exported, config, dropped, notes, who)));
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
        return new ExportPlan(List.copyOf(groups), List.copyOf(players), List.copyOf(tracks), exportedDefault,
                List.copyOf(refused),
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

    /**
     * The contextual entries LuckPerms can hold: one limited to a context this version reads, and a grade
     * held there only when that grade is exported. Any other context was written by hand for a newer version
     * and has no LuckPerms form here.
     */
    private static List<ScopedGrant> worldOnly(List<ScopedGrant> entries, Set<String> exported, GradesConfig config,
                                               int[] dropped, List<String> notes, String holder) {
        List<ScopedGrant> kept = new ArrayList<>();
        for (ScopedGrant entry : entries) {
            if (!exportable(entry.context())) {
                dropped[0]++;
                notes.add("Left out on " + holder + ": " + entry.value() + " is limited to " + entry.context()
                        + ", which this version does not read.");
                continue;
            }
            if (entry.namesGrade()) {
                if (kept(List.of(entry.value()), exported, config, dropped, notes, holder).isEmpty()) continue;
            }
            kept.add(entry);
        }
        return List.copyOf(kept);
    }

    /** The prefixes that apply everywhere or in a context this version reads; one limited otherwise has no LuckPerms form here. */
    private static List<ChatGrant> chatWorldOnly(List<ChatGrant> grants, int[] dropped, List<String> notes, String holder) {
        List<ChatGrant> kept = new ArrayList<>();
        for (ChatGrant grant : grants) {
            if (!grant.context().isEmpty() && !exportable(grant.context())) {
                dropped[0]++;
                notes.add("Left out on " + holder + ": the " + (grant.suffix() ? "suffix " : "prefix ") + grant.text()
                        + " is limited to " + grant.context() + ", which this version does not read.");
                continue;
            }
            kept.add(grant);
        }
        return List.copyOf(kept);
    }

    /** The meta that applies everywhere or in a context this version reads. */
    private static List<MetaGrant> metaExportable(List<MetaGrant> grants, int[] dropped, List<String> notes, String holder) {
        List<MetaGrant> kept = new ArrayList<>();
        for (MetaGrant grant : grants) {
            if (!grant.context().isEmpty() && !exportable(grant.context())) {
                dropped[0]++;
                notes.add("Left out on " + holder + ": the meta " + grant.key() + " is limited to " + grant.context()
                        + ", which this version does not read.");
                continue;
            }
            kept.add(grant);
        }
        return List.copyOf(kept);
    }

    /**
     * Whether a stored context has a LuckPerms form: one this version reads. A key written by hand for a newer
     * version, {@code server} until cluster mode, has none and matches nothing here either.
     */
    private static boolean exportable(String raw) {
        return com.arcadia.customperm.perm.Contexts.parse(raw) != null;
    }

    /**
     * The entries still alive, their expiry recorded under {@code prefix}: one that has run out is not
     * exported, the resolver already treating it as gone.
     */
    private static Set<String> live(Set<String> entries, Map<String, Long> expiries, String prefix,
                                    Map<String, Long> out, long now) {
        return Set.copyOf(live(List.copyOf(entries), expiries, prefix, out, now));
    }

    private static List<String> live(List<String> entries, Map<String, Long> expiries, String prefix,
                                     Map<String, Long> out, long now) {
        List<String> alive = new ArrayList<>();
        for (String entry : entries) {
            Long at = expiries == null ? null : expiries.get(entry);
            if (at != null && at <= now) continue;
            alive.add(entry);
            if (at != null) out.put(prefix + entry, at);
        }
        return alive;
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
        return new ExportPlan(groups, players, tracks, defaultGrade, refused, notes, Set.copyOf(existing), dropped);
    }

    public boolean isEmpty() {
        return groups.isEmpty() && players.isEmpty() && tracks.isEmpty();
    }

    /**
     * Whether the LuckPerms default group gets the default grade as a parent. Not when there is none, and
     * not when the default grade is named {@code default}: it is then that group itself.
     */
    public boolean carriesDefault() {
        return !defaultGrade.isEmpty() && !defaultGrade.equals(LP_DEFAULT);
    }

    /** Groups, the default group when a default grade is carried to it, players, then tracks: what gets written. */
    public int holders() {
        return groups.size() + (carriesDefault() ? 1 : 0) + players.size() + tracks.size();
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
            source.chat().forEach(chat -> chat.addTo(grade));
            source.expiries().forEach((key, at) -> {
                if (key.startsWith("allow:")) grade.permissionExpiries.put(key.substring(6), at);
                else if (key.startsWith("deny:")) grade.deniedPermissionExpiries.put(key.substring(5), at);
                else if (key.startsWith("grade:")) grade.parentExpiries.put(key.substring(6), at);
                else if (key.startsWith("refuse:")) grade.deniedParentExpiries.put(key.substring(7), at);
            });
            source.scoped().forEach(entry -> entry.addTo(Scopes.of(grade, entry.context())));
            source.meta().forEach(meta -> meta.addTo(grade));
            config.grades.put(grade.name, grade);
        }
        for (Track track : tracks) config.tracks.put(track.name(), new ArrayList<>(track.groups()));
        for (Player player : players) {
            if (!player.grades().isEmpty()) config.userGrades.put(player.uuid(), new ArrayList<>(player.grades()));
            if (!player.deniedGrades().isEmpty()) {
                config.userDeniedGrades.put(player.uuid(), new ArrayList<>(player.deniedGrades()));
            }
            if (!player.allow().isEmpty()) config.userPermissions.put(player.uuid(), new HashSet<>(player.allow()));
            if (!player.deny().isEmpty()) config.userDeniedPermissions.put(player.uuid(), new HashSet<>(player.deny()));
            player.expiries().forEach((key, at) -> {
                int colon = key.indexOf(':');
                Map<String, Map<String, Long>> byUser = switch (key.substring(0, colon)) {
                    case "allow" -> config.userPermissionExpiries;
                    case "deny" -> config.userDeniedPermissionExpiries;
                    case "grade" -> config.userGradeExpiries;
                    default -> config.userDeniedGradeExpiries;
                };
                byUser.computeIfAbsent(player.uuid(), k -> new HashMap<>()).put(key.substring(colon + 1), at);
            });
            UUID id = UUID.fromString(player.uuid());
            player.scoped().forEach(entry -> entry.addTo(Scopes.of(config, id, entry.context())));
            player.meta().forEach(meta -> meta.addTo(config, id));
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
        if (!tracks.isEmpty()) {
            lines.add(tracks.size() + " track(s) written with their rungs. Adding keeps a track LuckPerms already has "
                    + "as it is; replacing sets its groups to these.");
        }
        if (!existing.isEmpty()) {
            lines.add("Already in LuckPerms: " + String.join(", ", existing) + ". Adding keeps what they hold; "
                    + "replacing clears their customperm nodes and parents first, and their prefix or suffix "
                    + "only where the grade has one, never the nodes of other mods. Meta is written by key: replacing "
                    + "clears only the keys the grade sets.");
        }
        if (groups.stream().anyMatch(g -> !g.scoped().isEmpty()) || players.stream().anyMatch(p -> !p.scoped().isEmpty())) {
            lines.add("Entries limited to a world are written with LuckPerms' dimension-type context, which is "
                    + "the dimension on NeoForge (its world context is the save's name): the_nether for a vanilla "
                    + "world, the full id for a modded one. A game mode and a static context go as they are.");
        }
        if (groups.stream().anyMatch(g -> !g.chat().isEmpty()) || players.stream().anyMatch(p -> !p.chat().isEmpty())) {
            lines.add("Prefixes and suffixes are written with their own priority, temporary ones temporary. Adding "
                    + "keeps one LuckPerms already has at the same priority.");
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
