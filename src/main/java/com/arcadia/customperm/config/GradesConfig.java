/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GradesConfig {
    public Map<String, Grade> grades = new HashMap<>();
    /** UUID string -> list of grade names (ordered, but order is informational only). */
    public Map<String, List<String>> userGrades = new HashMap<>();
    /**
     * UUID string -> grades this player refuses, even when one of their grades inherits it. The way to say
     * "everything that grade gives, except what it takes from this one", like a LuckPerms inheritance node
     * set to false on a user.
     */
    public Map<String, List<String>> userDeniedGrades = new HashMap<>();
    /**
     * UUID string -> ALLOW nodes carried by that player alone, above every grade they hold. This is the
     * exception a single player gets without inventing a grade for them, like a node set on a LuckPerms
     * user rather than on one of their groups.
     */
    public Map<String, Set<String>> userPermissions = new HashMap<>();
    /** UUID string -> DENY nodes carried by that player alone. */
    public Map<String, Set<String>> userDeniedPermissions = new HashMap<>();
    /**
     * UUID string -> chat prefixes carried by that player alone, like prefix nodes set on a LuckPerms user.
     * Colour codes use {@code &}; see {@link ChatEntry} for how they rank and {@code chat/NameDecoration}.
     */
    public Map<String, List<ChatEntry>> userPrefixEntries = new HashMap<>();
    /** UUID string -> chat suffixes carried by that player alone. */
    public Map<String, List<ChatEntry>> userSuffixEntries = new HashMap<>();
    /**
     * Files written before priorities: UUID string -> the one prefix a player carried. Read, moved into
     * {@link #userPrefixEntries} at priority 0 by {@link #normalize()}, and never written again.
     */
    public Map<String, String> userPrefixes;
    /** The same for suffixes, moved into {@link #userSuffixEntries}. */
    public Map<String, String> userSuffixes;

    /*
     * Expiries, in epoch seconds, beside the collections they belong to: a node, a grade held or a grade
     * refused that is absent from these maps is permanent, which is what every file written before them
     * reads as. The resolver ignores an entry whose time has passed; the sweep then removes it for good.
     */

    /** UUID string -> own ALLOW node -> when it expires. */
    public Map<String, Map<String, Long>> userPermissionExpiries = new HashMap<>();
    /** UUID string -> own DENY node -> when it expires. */
    public Map<String, Map<String, Long>> userDeniedPermissionExpiries = new HashMap<>();
    /** UUID string -> grade held -> when the player stops holding it. */
    public Map<String, Map<String, Long>> userGradeExpiries = new HashMap<>();
    /** UUID string -> grade refused -> when the refusal ends. */
    public Map<String, Map<String, Long>> userDeniedGradeExpiries = new HashMap<>();

    /**
     * UUID string -> meta key -> value carried by that player alone, like a meta node on a LuckPerms user. See
     * {@link Grade#meta} for what meta is for.
     */
    public Map<String, Map<String, String>> userMeta = new HashMap<>();
    /** UUID string -> meta key -> when it expires. */
    public Map<String, Map<String, Long>> userMetaExpiries = new HashMap<>();

    /**
     * UUID string -> context -> the nodes and grades that player holds in that context only, such as
     * {@code world=minecraft:the_nether}. See {@code perm/Contexts} for how a context is written.
     */
    public Map<String, Map<String, UserScoped>> userContexts = new HashMap<>();

    /**
     * Track name -> its grades, lowest rung first, like a LuckPerms track. A track grants nothing: it is the
     * ladder promote and demote move a player along, one rung at a time. A grade may sit on several tracks.
     */
    public Map<String, List<String>> tracks = new HashMap<>();

    /**
     * One chat prefix or suffix. The highest priority reachable shows, like LuckPerms' prefix nodes; at equal
     * priority the player's own beats a grade's, a heavier grade a lighter one, and a nearer ancestor a
     * farther one. A holder carries at most one per priority, so a priority names the entry to remove.
     */
    public static class ChatEntry {
        public int priority;
        public String text;
        /** When it ends, in epoch seconds; 0 for good, which is what an entry without the field reads as. */
        public long expires;

        public ChatEntry() {
        }

        public ChatEntry(int priority, String text, long expires) {
            this.priority = priority;
            this.text = text;
            this.expires = expires;
        }

        public boolean alive(long now) {
            return expires <= 0 || expires > now;
        }

        /** A deep copy of a holder's entries: they are mutable, and a copy must not share them. */
        public static List<ChatEntry> copy(List<ChatEntry> entries) {
            List<ChatEntry> copy = new ArrayList<>();
            if (entries != null) entries.forEach(e -> copy.add(new ChatEntry(e.priority, e.text, e.expires)));
            return copy;
        }

        public static Map<String, List<ChatEntry>> copyUsers(Map<String, List<ChatEntry>> byUser) {
            Map<String, List<ChatEntry>> copy = new HashMap<>();
            byUser.forEach((uuid, entries) -> copy.put(uuid, copy(entries)));
            return copy;
        }
    }

    /**
     * What applies in one context only. At the same specificity and from the same holder, a node here
     * outranks the same node without a context, like a contextual node in LuckPerms; a prefix here outranks
     * the holder's global one at the same priority. A grade refused here is refused only there. Each
     * collection keeps its expiries beside it, as the global ones do: an entry absent from them is permanent.
     */
    public static class Scoped {
        public Set<String> permissions = new HashSet<>();
        public Set<String> deniedPermissions = new HashSet<>();
        /** Grades refused in this context: by a grade in its own chain, by a player wherever they come from. */
        public List<String> refused = new ArrayList<>();
        public List<ChatEntry> prefixes = new ArrayList<>();
        public List<ChatEntry> suffixes = new ArrayList<>();
        /** ALLOW node -> when it expires, in epoch seconds. */
        public Map<String, Long> permissionExpiries = new HashMap<>();
        /** DENY node -> when it expires. */
        public Map<String, Long> deniedPermissionExpiries = new HashMap<>();
        /** Refused grade -> when the refusal ends. */
        public Map<String, Long> refusedExpiries = new HashMap<>();
        /** Meta key -> value in this context. */
        public Map<String, String> meta = new java.util.TreeMap<>();
        /** Meta key -> when it expires. */
        public Map<String, Long> metaExpiries = new HashMap<>();

        public boolean isEmpty() {
            return permissions.isEmpty() && deniedPermissions.isEmpty() && refused.isEmpty()
                    && prefixes.isEmpty() && suffixes.isEmpty() && meta.isEmpty();
        }

        /**
         * The expiries of the entries a listing calls {@code kind}: {@code allow}, {@code deny} and
         * {@code refused} here, {@code parent} or {@code grade} on the holder's own kind of scope. Empty for
         * any other kind.
         */
        public Map<String, Long> expiries(String kind) {
            return switch (kind) {
                case "allow" -> permissionExpiries;
                case "deny" -> deniedPermissionExpiries;
                case "refused" -> refusedExpiries;
                case "meta" -> metaExpiries;
                default -> Map.of();
            };
        }
    }

    /** What one grade gives in one context: {@link Scoped}, and grades it inherits only there. */
    public static class GradeScoped extends Scoped {
        public List<String> parents = new ArrayList<>();
        /** Parent -> when this grade stops inheriting it here. */
        public Map<String, Long> parentExpiries = new HashMap<>();

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && parents.isEmpty();
        }

        @Override
        public Map<String, Long> expiries(String kind) {
            return kind.equals("parent") ? parentExpiries : super.expiries(kind);
        }
    }

    /** What one player holds in one context: {@link Scoped}, and grades that apply to them only there. */
    public static class UserScoped extends Scoped {
        public List<String> grades = new ArrayList<>();
        /** Grade held here -> when the player stops holding it here. */
        public Map<String, Long> gradeExpiries = new HashMap<>();

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && grades.isEmpty();
        }

        @Override
        public Map<String, Long> expiries(String kind) {
            return kind.equals("grade") ? gradeExpiries : super.expiries(kind);
        }
    }

    public static class Grade {
        public String name;
        public Set<String> permissions = new HashSet<>();        // ALLOW nodes
        public Set<String> deniedPermissions = new HashSet<>();  // DENY nodes (H2.1)
        /**
         * Grades this one inherits from, like a LuckPerms group parent. A parent's entry applies where
         * this grade says nothing as precise about the node, so an entry here overrides the same entry
         * inherited from a parent. A name that matches no grade is ignored, and a cycle stops at the
         * grade it comes back to rather than looping.
         */
        public List<String> parents = new ArrayList<>();
        /**
         * Grades this one refuses to inherit, even when one of its parents inherits them. Like a parent
         * entry, it is read where it is declared: a grade nearer to the holder decides before a farther
         * one, so a grade refuses only for its own chain, never for another grade the player holds.
         */
        public List<String> deniedParents = new ArrayList<>();
        /**
         * Tie-break between two grades held by the same player, like a LuckPerms group weight. It is read
         * only when they cover a node at the same specificity: the heaviest grade decides, and a DENY still
         * wins between equal weights. Absent from a file, it deserializes to 0, which is the behaviour that
         * predates the field.
         */
        public int weight = 0;
        /**
         * Shown before the name of the players who hold this grade, in chat and wherever the game shows
         * their name, when name decoration is on. See {@link ChatEntry} for how several rank.
         */
        public List<ChatEntry> prefixes = new ArrayList<>();
        /** Shown after the name, ranked like {@link #prefixes}. */
        public List<ChatEntry> suffixes = new ArrayList<>();
        /** Files written before priorities: the one prefix, moved into {@link #prefixes} at priority 0. */
        public String prefix;
        /** The same for the suffix, moved into {@link #suffixes}. */
        public String suffix;
        /** ALLOW node -> when it expires, in epoch seconds; a node absent here is permanent. */
        public Map<String, Long> permissionExpiries = new HashMap<>();
        /** DENY node -> when it expires. */
        public Map<String, Long> deniedPermissionExpiries = new HashMap<>();
        /** Context -> the nodes this grade gives in that context only. */
        public Map<String, GradeScoped> contexts = new HashMap<>();
        /** Parent -> when this grade stops inheriting it, in epoch seconds; a parent absent here is permanent. */
        public Map<String, Long> parentExpiries = new HashMap<>();
        /** Refused grade -> when this grade stops refusing it. */
        public Map<String, Long> deniedParentExpiries = new HashMap<>();
        /**
         * Meta key -> value, like LuckPerms meta: data a grade carries for other mods to read rather than a
         * permission. A mod that declares a number or text permission node reads it here, the node's name
         * being the key, the way LuckPerms answers those nodes. A player's own value wins, then the heaviest
         * grade's, then the nearest ancestor's.
         */
        public Map<String, String> meta = new java.util.TreeMap<>();
        /** Meta key -> when it expires. */
        public Map<String, Long> metaExpiries = new HashMap<>();
    }

    public void normalize() {
        if (grades == null) grades = new HashMap<>();
        if (userGrades == null) userGrades = new HashMap<>();
        // A hand-edited "grade": null or "uuid": null parses fine, but every permission check
        // iterates these maps from a Brigadier requires() predicate, where an NPE breaks the
        // command tree sent to the player.
        grades.values().removeIf(java.util.Objects::isNull);
        for (Grade g : grades.values()) {
            if (g.permissions == null) g.permissions = new HashSet<>();
            if (g.deniedPermissions == null) g.deniedPermissions = new HashSet<>();
            g.permissions.remove(null);
            g.deniedPermissions.remove(null);
            if (g.parents == null) g.parents = new ArrayList<>();
            g.parents.removeIf(java.util.Objects::isNull);
            // A grade inheriting from itself is a no-op the resolver would have to guard anyway, and a
            // name repeated twice costs a second walk for nothing.
            g.parents.removeIf(parent -> parent.equals(g.name));
            java.util.Set<String> seen = new java.util.LinkedHashSet<>(g.parents);
            if (seen.size() != g.parents.size()) g.parents = new ArrayList<>(seen);
            if (g.deniedParents == null) g.deniedParents = new ArrayList<>();
            g.deniedParents.removeIf(java.util.Objects::isNull);
            g.deniedParents.removeIf(parent -> parent.equals(g.name));
            java.util.Set<String> denied = new java.util.LinkedHashSet<>(g.deniedParents);
            if (denied.size() != g.deniedParents.size()) g.deniedParents = new ArrayList<>(denied);
            g.prefixes = normalizeChat(g.prefixes, g.prefix);
            g.suffixes = normalizeChat(g.suffixes, g.suffix);
            g.prefix = null;
            g.suffix = null;
            g.permissionExpiries = keepFor(g.permissionExpiries, g.permissions);
            g.deniedPermissionExpiries = keepFor(g.deniedPermissionExpiries, g.deniedPermissions);
            g.parentExpiries = keepFor(g.parentExpiries, g.parents);
            g.deniedParentExpiries = keepFor(g.deniedParentExpiries, g.deniedParents);
            g.meta = normalizeMeta(g.meta);
            g.metaExpiries = keepFor(g.metaExpiries, g.meta.keySet());
            g.contexts = normalizeScopes(g.contexts, GradeScoped::new);
            // A grade inheriting or refusing itself in one world means nothing, as it does everywhere.
            g.contexts.values().forEach(scope -> {
                scope.parents.remove(g.name);
                scope.refused.remove(g.name);
                scope.parentExpiries.remove(g.name);
                scope.refusedExpiries.remove(g.name);
            });
            g.contexts.values().removeIf(Scoped::isEmpty);
        }
        normalizeUserGrades(userGrades);
        if (userDeniedGrades == null) userDeniedGrades = new HashMap<>();
        normalizeUserGrades(userDeniedGrades);
        if (userPermissions == null) userPermissions = new HashMap<>();
        if (userDeniedPermissions == null) userDeniedPermissions = new HashMap<>();
        normalizeUserNodes(userPermissions);
        normalizeUserNodes(userDeniedPermissions);
        userPrefixEntries = normalizeUserChat(userPrefixEntries, userPrefixes);
        userSuffixEntries = normalizeUserChat(userSuffixEntries, userSuffixes);
        userPrefixes = null;
        userSuffixes = null;
        userPermissionExpiries = keepForUsers(userPermissionExpiries, userPermissions);
        userDeniedPermissionExpiries = keepForUsers(userDeniedPermissionExpiries, userDeniedPermissions);
        userGradeExpiries = keepForUsers(userGradeExpiries, userGrades);
        userDeniedGradeExpiries = keepForUsers(userDeniedGradeExpiries, userDeniedGrades);
        Map<String, Map<String, String>> meta = new HashMap<>();
        if (userMeta != null) userMeta.forEach((uuid, values) -> {
            Map<String, String> clean = normalizeMeta(values);
            if (uuid != null && !clean.isEmpty()) meta.put(uuid, clean);
        });
        userMeta = meta;
        Map<String, java.util.Set<String>> metaKeys = new HashMap<>();
        userMeta.forEach((uuid, values) -> metaKeys.put(uuid, values.keySet()));
        userMetaExpiries = keepForUsers(userMetaExpiries, metaKeys);
        if (userContexts == null) userContexts = new HashMap<>();
        userContexts.keySet().removeIf(java.util.Objects::isNull);
        userContexts.replaceAll((uuid, scopes) -> normalizeScopes(scopes, UserScoped::new));
        userContexts.values().removeIf(Map::isEmpty);
        if (tracks == null) tracks = new HashMap<>();
        tracks.keySet().removeIf(java.util.Objects::isNull);
        // An empty track is kept: it is one being built. A grade named twice would make its rung ambiguous.
        tracks.replaceAll((name, rungs) -> rungs == null ? new ArrayList<>()
                : new ArrayList<>(new java.util.LinkedHashSet<>(rungs.stream().filter(java.util.Objects::nonNull).toList())));
    }

    /**
     * Scopes keyed by their stored form, so {@code world=the_nether} and {@code world=minecraft:the_nether}
     * written by hand end up as one entry. A key that does not parse is kept as it is: it may be one a newer
     * version reads, and it matches nothing here. Empty scopes are dropped.
     */
    private static <S extends Scoped> Map<String, S> normalizeScopes(Map<String, S> scopes,
                                                                     java.util.function.Supplier<S> blank) {
        Map<String, S> clean = new HashMap<>();
        if (scopes == null) return clean;
        scopes.forEach((raw, scope) -> {
            if (raw == null || scope == null) return;
            String parsed = com.arcadia.customperm.perm.Contexts.parse(raw);
            S target = clean.computeIfAbsent(parsed == null ? raw : parsed, k -> blank.get());
            if (scope.permissions != null) scope.permissions.forEach(node -> {
                if (node != null) target.permissions.add(node);
            });
            if (scope.deniedPermissions != null) scope.deniedPermissions.forEach(node -> {
                if (node != null) target.deniedPermissions.add(node);
            });
            addNames(target.refused, scope.refused);
            target.prefixes = normalizeChat(concat(target.prefixes, scope.prefixes), null);
            target.suffixes = normalizeChat(concat(target.suffixes, scope.suffixes), null);
            addExpiries(target.permissionExpiries, scope.permissionExpiries);
            addExpiries(target.deniedPermissionExpiries, scope.deniedPermissionExpiries);
            addExpiries(target.refusedExpiries, scope.refusedExpiries);
            target.meta.putAll(normalizeMeta(scope.meta));
            addExpiries(target.metaExpiries, scope.metaExpiries);
            if (scope instanceof UserScoped user) {
                addNames(((UserScoped) target).grades, user.grades);
                addExpiries(((UserScoped) target).gradeExpiries, user.gradeExpiries);
            }
            if (scope instanceof GradeScoped grade) {
                addNames(((GradeScoped) target).parents, grade.parents);
                addExpiries(((GradeScoped) target).parentExpiries, grade.parentExpiries);
            }
        });
        clean.values().removeIf(Scoped::isEmpty);
        clean.values().forEach(GradesConfig::keepScopedExpiries);
        return clean;
    }

    /** The expiries of one scope that still name one of its entries, as {@link #keepFor} does for a holder. */
    private static void keepScopedExpiries(Scoped scope) {
        scope.permissionExpiries = keepFor(scope.permissionExpiries, scope.permissions);
        scope.deniedPermissionExpiries = keepFor(scope.deniedPermissionExpiries, scope.deniedPermissions);
        scope.refusedExpiries = keepFor(scope.refusedExpiries, scope.refused);
        scope.metaExpiries = keepFor(scope.metaExpiries, scope.meta.keySet());
        if (scope instanceof UserScoped user) user.gradeExpiries = keepFor(user.gradeExpiries, user.grades);
        if (scope instanceof GradeScoped grade) grade.parentExpiries = keepFor(grade.parentExpiries, grade.parents);
    }

    /** Two spellings of one context merged: of two expiries for one entry, the later one. */
    private static void addExpiries(Map<String, Long> into, Map<String, Long> expiries) {
        if (expiries == null) return;
        expiries.forEach((key, at) -> {
            if (key != null && at != null) into.merge(key, at, Math::max);
        });
    }

    /** Meta with its keys lowercased, as the permission nodes they answer are; an empty key or a null value is dropped. */
    private static Map<String, String> normalizeMeta(Map<String, String> meta) {
        Map<String, String> clean = new java.util.TreeMap<>();
        if (meta == null) return clean;
        meta.forEach((key, value) -> {
            if (key == null || value == null) return;
            String k = key.trim().toLowerCase(java.util.Locale.ROOT);
            if (!k.isEmpty()) clean.put(k, value);
        });
        return clean;
    }

    private static void addNames(List<String> into, List<String> names) {
        if (names == null) return;
        names.forEach(name -> {
            if (name != null && !into.contains(name)) into.add(name);
        });
    }

    private static List<ChatEntry> concat(List<ChatEntry> first, List<ChatEntry> second) {
        List<ChatEntry> all = new ArrayList<>();
        if (first != null) all.addAll(first);
        if (second != null) all.addAll(second);
        return all;
    }

    /** Whether any grade or player holds an entry limited to a context: the only case worth a resync on a world change. */
    public boolean hasContextualEntries() {
        if (!userContexts.isEmpty()) return true;
        for (Grade grade : grades.values()) {
            if (!grade.contexts.isEmpty()) return true;
        }
        return false;
    }

    /**
     * The expiries that still name an entry of {@code entries}: one left behind by a hand edit or a removal
     * would otherwise make a permanent entry of the same name expire the day it is added back.
     */
    private static Map<String, Long> keepFor(Map<String, Long> expiries, java.util.Collection<String> entries) {
        Map<String, Long> kept = new HashMap<>();
        if (expiries == null) return kept;
        expiries.forEach((key, at) -> {
            if (key != null && at != null && at > 0 && entries.contains(key)) kept.put(key, at);
        });
        return kept;
    }

    private static <C extends java.util.Collection<String>> Map<String, Map<String, Long>> keepForUsers(
            Map<String, Map<String, Long>> expiries, Map<String, C> entries) {
        Map<String, Map<String, Long>> kept = new HashMap<>();
        if (expiries == null) return kept;
        expiries.forEach((uuid, byKey) -> {
            C held = uuid == null ? null : entries.get(uuid);
            if (held == null || byKey == null) return;
            Map<String, Long> live = keepFor(byKey, held);
            if (!live.isEmpty()) kept.put(uuid, live);
        });
        return kept;
    }

    /**
     * One holder's prefixes, with the legacy single one moved in at priority 0 unless that priority is taken.
     * An empty text is no prefix, and of two at one priority the first is kept: a priority names one entry.
     */
    private static List<ChatEntry> normalizeChat(List<ChatEntry> entries, String legacy) {
        List<ChatEntry> clean = new ArrayList<>();
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        if (entries != null) {
            for (ChatEntry entry : entries) {
                if (entry == null || entry.text == null || entry.text.isEmpty() || !taken.add(entry.priority)) continue;
                if (entry.expires < 0) entry.expires = 0;
                clean.add(entry);
            }
        }
        if (legacy != null && !legacy.isEmpty() && taken.add(0)) clean.add(new ChatEntry(0, legacy, 0));
        clean.sort(java.util.Comparator.comparingInt((ChatEntry e) -> e.priority).reversed());
        return clean;
    }

    private static Map<String, List<ChatEntry>> normalizeUserChat(Map<String, List<ChatEntry>> entries,
                                                                  Map<String, String> legacy) {
        Map<String, List<ChatEntry>> clean = new HashMap<>();
        if (entries != null) entries.forEach((uuid, list) -> {
            if (uuid != null) clean.put(uuid, normalizeChat(list, null));
        });
        if (legacy != null) legacy.forEach((uuid, text) -> {
            if (uuid != null) clean.put(uuid, normalizeChat(clean.get(uuid), text));
        });
        clean.values().removeIf(List::isEmpty);
        return clean;
    }

    private static void normalizeUserGrades(Map<String, List<String>> assignments) {
        assignments.values().removeIf(java.util.Objects::isNull);
        assignments.values().forEach(list -> list.removeIf(java.util.Objects::isNull));
        assignments.values().removeIf(List::isEmpty);
    }

    /** An empty entry is dropped rather than kept: it would show a player as carrying nodes they do not have. */
    private static void normalizeUserNodes(Map<String, Set<String>> nodes) {
        nodes.values().removeIf(java.util.Objects::isNull);
        nodes.values().forEach(set -> set.remove(null));
        nodes.values().removeIf(Set::isEmpty);
    }

    public boolean userHasPermission(UUID uuid, String node) {
        return com.arcadia.customperm.perm.PermissionResolver.resolve(this, uuid, node);
    }
}
