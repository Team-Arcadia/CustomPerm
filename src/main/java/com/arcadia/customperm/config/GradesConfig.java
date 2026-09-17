/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

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

    public static class Grade {
        public String name;
        public Set<String> permissions = new HashSet<>();        // ALLOW nodes
        public Set<String> deniedPermissions = new HashSet<>();  // DENY nodes (H2.1)
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
        }
        userGrades.values().removeIf(java.util.Objects::isNull);
        userGrades.values().forEach(list -> list.removeIf(java.util.Objects::isNull));
        userGrades.values().removeIf(List::isEmpty);
    }

    public boolean userHasPermission(UUID uuid, String node) {
        return com.arcadia.customperm.perm.PermissionResolver.resolve(this, uuid, node);
    }
}
