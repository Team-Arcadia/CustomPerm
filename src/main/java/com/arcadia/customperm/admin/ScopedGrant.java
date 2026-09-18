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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One entry limited to a context, as {@link ImportPlan} and {@link ExportPlan} carry it: plain Java, like
 * them.
 *
 * @param context the stored form, such as {@code world=minecraft:the_nether}
 * @param kind    {@code "allow"}, {@code "deny"} or {@code "grade"} (a grade held there, on a player only)
 * @param value   the node, or the grade name
 */
public record ScopedGrant(String context, String kind, String value) {

    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String GRADE = "grade";

    /** A grade's contextual nodes, sorted, so two reads of the same file give the same plan. */
    static List<ScopedGrant> of(Map<String, ? extends GradesConfig.Scoped> scopes) {
        List<ScopedGrant> out = new ArrayList<>();
        new TreeMap<>(scopes).forEach((context, scope) -> {
            new TreeSet<>(scope.permissions).forEach(node -> out.add(new ScopedGrant(context, ALLOW, node)));
            new TreeSet<>(scope.deniedPermissions).forEach(node -> out.add(new ScopedGrant(context, DENY, node)));
            if (scope instanceof GradesConfig.UserScoped user) {
                new TreeSet<>(user.grades).forEach(grade -> out.add(new ScopedGrant(context, GRADE, grade)));
            }
        });
        return List.copyOf(out);
    }

    /** Adds this entry to {@code scope}; true when it was not there. */
    boolean addTo(GradesConfig.Scoped scope) {
        return switch (kind) {
            case ALLOW -> scope.permissions.add(value);
            case DENY -> scope.deniedPermissions.add(value);
            default -> scope instanceof GradesConfig.UserScoped user && !user.grades.contains(value)
                    && user.grades.add(value);
        };
    }
}
