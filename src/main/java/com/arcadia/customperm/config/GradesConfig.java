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
    }

    public void normalize() {
        if (grades == null) grades = new HashMap<>();
        if (userGrades == null) userGrades = new HashMap<>();
        for (Grade g : grades.values()) {
            if (g != null) {
                if (g.permissions == null) g.permissions = new HashSet<>();
                if (g.deniedPermissions == null) g.deniedPermissions = new HashSet<>();
            }
        }
    }

    public boolean userHasPermission(UUID uuid, String node) {
        return com.arcadia.customperm.perm.PermissionResolver.resolve(this, uuid, node);
    }
}
