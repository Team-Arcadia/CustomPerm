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
        }
        normalizeUserGrades(userGrades);
        if (userDeniedGrades == null) userDeniedGrades = new HashMap<>();
        normalizeUserGrades(userDeniedGrades);
        if (userPermissions == null) userPermissions = new HashMap<>();
        if (userDeniedPermissions == null) userDeniedPermissions = new HashMap<>();
        normalizeUserNodes(userPermissions);
        normalizeUserNodes(userDeniedPermissions);
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
