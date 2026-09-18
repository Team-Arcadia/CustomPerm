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
 * @param kind    {@code "allow"}, {@code "deny"}, {@code "grade"} (a grade held there, on a player only),
 *                {@code "parent"} (a grade inherited there, on a grade only) or {@code "refused"} (a grade
 *                refused there)
 * @param value   the node, or the grade name
 */
public record ScopedGrant(String context, String kind, String value) {

    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String GRADE = "grade";
    public static final String PARENT = "parent";
    public static final String REFUSED = "refused";

    /** Whether {@link #value} names a grade, which an export must find among the grades it writes. */
    public boolean namesGrade() {
        return kind.equals(GRADE) || kind.equals(PARENT) || kind.equals(REFUSED);
    }

    /** A grade's contextual nodes, sorted, so two reads of the same file give the same plan. */
    static List<ScopedGrant> of(Map<String, ? extends GradesConfig.Scoped> scopes) {
        List<ScopedGrant> out = new ArrayList<>();
        new TreeMap<>(scopes).forEach((context, scope) -> {
            new TreeSet<>(scope.permissions).forEach(node -> out.add(new ScopedGrant(context, ALLOW, node)));
            new TreeSet<>(scope.deniedPermissions).forEach(node -> out.add(new ScopedGrant(context, DENY, node)));
            if (scope instanceof GradesConfig.UserScoped user) {
                new TreeSet<>(user.grades).forEach(grade -> out.add(new ScopedGrant(context, GRADE, grade)));
            }
            if (scope instanceof GradesConfig.GradeScoped grade) {
                grade.parents.forEach(parent -> out.add(new ScopedGrant(context, PARENT, parent)));
            }
            scope.refused.forEach(refused -> out.add(new ScopedGrant(context, REFUSED, refused)));
        });
        return List.copyOf(out);
    }

    /** Adds this entry to {@code scope}; true when it was not there. */
    boolean addTo(GradesConfig.Scoped scope) {
        return switch (kind) {
            case ALLOW -> scope.permissions.add(value);
            case DENY -> scope.deniedPermissions.add(value);
            case PARENT -> scope instanceof GradesConfig.GradeScoped grade && !grade.parents.contains(value)
                    && grade.parents.add(value);
            case REFUSED -> !scope.refused.contains(value) && scope.refused.add(value);
            default -> scope instanceof GradesConfig.UserScoped user && !user.grades.contains(value)
                    && user.grades.add(value);
        };
    }
}
