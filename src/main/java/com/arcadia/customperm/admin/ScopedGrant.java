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
 * @param expires when it ends, in epoch seconds; 0 for good
 */
public record ScopedGrant(String context, String kind, String value, long expires) {

    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String GRADE = "grade";
    public static final String PARENT = "parent";
    public static final String REFUSED = "refused";

    /** One for good. */
    public ScopedGrant(String context, String kind, String value) {
        this(context, kind, value, 0);
    }

    /** Whether {@link #value} names a grade, which an export must find among the grades it writes. */
    public boolean namesGrade() {
        return kind.equals(GRADE) || kind.equals(PARENT) || kind.equals(REFUSED);
    }

    /**
     * A holder's contextual entries still alive at {@code now}, with their expiry, sorted, so two reads of the
     * same file give the same plan.
     */
    static List<ScopedGrant> of(Map<String, ? extends GradesConfig.Scoped> scopes, long now) {
        List<ScopedGrant> out = new ArrayList<>();
        new TreeMap<>(scopes).forEach((context, scope) -> {
            new TreeSet<>(scope.permissions).forEach(node -> add(out, scope, context, ALLOW, node, now));
            new TreeSet<>(scope.deniedPermissions).forEach(node -> add(out, scope, context, DENY, node, now));
            if (scope instanceof GradesConfig.UserScoped user) {
                new TreeSet<>(user.grades).forEach(grade -> add(out, scope, context, GRADE, grade, now));
            }
            if (scope instanceof GradesConfig.GradeScoped grade) {
                grade.parents.forEach(parent -> add(out, scope, context, PARENT, parent, now));
            }
            scope.refused.forEach(refused -> add(out, scope, context, REFUSED, refused, now));
        });
        return List.copyOf(out);
    }

    private static void add(List<ScopedGrant> out, GradesConfig.Scoped scope, String context, String kind,
                            String value, long now) {
        Long at = scope.expiries(kind).get(value);
        if (at != null && at <= now) return;
        out.add(new ScopedGrant(context, kind, value, at == null ? 0 : at));
    }

    /**
     * The same entry read twice, as LuckPerms can hold it for good and for a while in one world: the permanent
     * one wins, as it does there, and of two temporary ones the one that lasts longer. Order kept.
     */
    public static List<ScopedGrant> merged(List<ScopedGrant> entries) {
        Map<String, ScopedGrant> kept = new java.util.LinkedHashMap<>();
        for (ScopedGrant entry : entries) {
            kept.merge(entry.context() + "|" + entry.kind() + "|" + entry.value(), entry, (a, b) ->
                    a.expires() == 0 || b.expires() == 0 ? new ScopedGrant(a.context(), a.kind(), a.value(), 0)
                            : a.expires() >= b.expires() ? a : b);
        }
        return List.copyOf(kept.values());
    }

    /**
     * Adds this entry to {@code scope}; true when it was not there. Its expiry comes with it only when it is
     * added: an entry already there, for good or not, is what was chosen here.
     */
    boolean addTo(GradesConfig.Scoped scope) {
        boolean added = switch (kind) {
            case ALLOW -> scope.permissions.add(value);
            case DENY -> scope.deniedPermissions.add(value);
            case PARENT -> scope instanceof GradesConfig.GradeScoped grade && !grade.parents.contains(value)
                    && grade.parents.add(value);
            case REFUSED -> !scope.refused.contains(value) && scope.refused.add(value);
            default -> scope instanceof GradesConfig.UserScoped user && !user.grades.contains(value)
                    && user.grades.add(value);
        };
        // Added means the kind fits this scope, so its expiries are the scope's own map.
        if (added && expires > 0) scope.expiries(kind).put(value, expires);
        return added;
    }
}
