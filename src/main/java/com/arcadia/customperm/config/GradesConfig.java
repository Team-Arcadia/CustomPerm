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
     * UUID string -> chat prefix carried by that player alone, above whatever their grades give, like a
     * prefix set on a LuckPerms user. Colour codes use {@code &}; see {@code chat/NameDecoration}.
     */
    public Map<String, String> userPrefixes = new HashMap<>();
    /** UUID string -> chat suffix carried by that player alone. */
    public Map<String, String> userSuffixes = new HashMap<>();

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
     * Nodes that apply in one context only. At the same specificity and from the same holder, one of these
     * outranks the same node without a context, like a contextual node in LuckPerms.
     */
    public static class Scoped {
        public Set<String> permissions = new HashSet<>();
        public Set<String> deniedPermissions = new HashSet<>();

        public boolean isEmpty() {
            return permissions.isEmpty() && deniedPermissions.isEmpty();
        }
    }

    /** What one player holds in one context: nodes, and grades that apply to them only there. */
    public static class UserScoped extends Scoped {
        public List<String> grades = new ArrayList<>();

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && grades.isEmpty();
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
         * their name, when name decoration is on. Among several grades the heaviest decides, as for a node;
         * absent from a file, it is null, which is no prefix.
         */
        public String prefix;
        /** Shown after the name, resolved like {@link #prefix}. */
        public String suffix;
        /** ALLOW node -> when it expires, in epoch seconds; a node absent here is permanent. */
        public Map<String, Long> permissionExpiries = new HashMap<>();
        /** DENY node -> when it expires. */
        public Map<String, Long> deniedPermissionExpiries = new HashMap<>();
        /** Context -> the nodes this grade gives in that context only. */
        public Map<String, Scoped> contexts = new HashMap<>();
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
            g.prefix = emptyToNull(g.prefix);
            g.suffix = emptyToNull(g.suffix);
            g.permissionExpiries = keepFor(g.permissionExpiries, g.permissions);
            g.deniedPermissionExpiries = keepFor(g.deniedPermissionExpiries, g.deniedPermissions);
            g.contexts = normalizeScopes(g.contexts, Scoped::new);
        }
        normalizeUserGrades(userGrades);
        if (userDeniedGrades == null) userDeniedGrades = new HashMap<>();
        normalizeUserGrades(userDeniedGrades);
        if (userPermissions == null) userPermissions = new HashMap<>();
        if (userDeniedPermissions == null) userDeniedPermissions = new HashMap<>();
        normalizeUserNodes(userPermissions);
        normalizeUserNodes(userDeniedPermissions);
        if (userPrefixes == null) userPrefixes = new HashMap<>();
        if (userSuffixes == null) userSuffixes = new HashMap<>();
        normalizeUserTexts(userPrefixes);
        normalizeUserTexts(userSuffixes);
        userPermissionExpiries = keepForUsers(userPermissionExpiries, userPermissions);
        userDeniedPermissionExpiries = keepForUsers(userDeniedPermissionExpiries, userDeniedPermissions);
        userGradeExpiries = keepForUsers(userGradeExpiries, userGrades);
        userDeniedGradeExpiries = keepForUsers(userDeniedGradeExpiries, userDeniedGrades);
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
            if (scope instanceof UserScoped user && user.grades != null) {
                List<String> grades = ((UserScoped) target).grades;
                user.grades.forEach(grade -> {
                    if (grade != null && !grades.contains(grade)) grades.add(grade);
                });
            }
        });
        clean.values().removeIf(Scoped::isEmpty);
        return clean;
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

    /** An empty prefix is no prefix, stored as absent so the file does not carry it. */
    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    private static void normalizeUserTexts(Map<String, String> texts) {
        texts.keySet().removeIf(java.util.Objects::isNull);
        texts.values().removeIf(text -> text == null || text.isEmpty());
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
