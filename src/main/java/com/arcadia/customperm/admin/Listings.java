/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.Contexts;
import com.arcadia.customperm.perm.Expiry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Text listings of what a grade or a player holds, each entry on one line with where it applies and how long
 * it lasts: {@code cp.zone.staff (server=demo-a, 29d left)}. What the files say, without opening them.
 */
public final class Listings {

    private Listings() {
    }

    /** What {@code name} holds, one line per kind; null when no such grade exists. */
    public static List<String> grade(String name) {
        GradesConfig config = CustomPerm.configManager.getGrades();
        GradesConfig.Grade grade = config.grades.get(name);
        if (grade == null) return null;
        List<String> lines = new ArrayList<>();
        String isDefault = name.equals(CustomPerm.configManager.getSettings().defaultGrade) ? ", default grade" : "";
        lines.add("Grade " + grade.label(name) + ", weight " + grade.weight + isDefault);
        Map<String, GradesConfig.GradeScoped> scopes = new TreeMap<>(grade.contexts);
        lines.add("  allow: " + join(entries(grade.permissions, grade.permissionExpiries, scopes, "allow",
                scope -> scope.permissions)));
        lines.add("  deny : " + join(entries(grade.deniedPermissions, grade.deniedPermissionExpiries, scopes, "deny",
                scope -> scope.deniedPermissions)));
        List<String> parents = entries(grade.parents, grade.parentExpiries, scopes, "parent", scope -> scope.parents);
        if (!parents.isEmpty()) lines.add("  inherits: " + join(parents));
        List<String> refused = entries(grade.deniedParents, grade.deniedParentExpiries, scopes, "refused",
                scope -> scope.refused);
        if (!refused.isEmpty()) lines.add("  refuses: " + join(refused));
        return lines;
    }

    /** The nodes {@code player} carries themselves, allowed or denied, everywhere then by context. */
    public static List<String> ownNodes(UUID player, boolean deny) {
        GradesConfig config = CustomPerm.configManager.getGrades();
        String id = player.toString();
        Map<String, GradesConfig.UserScoped> scopes = new TreeMap<>(config.userContexts.getOrDefault(id, Map.of()));
        return deny
                ? entries(config.userDeniedPermissions.get(id), config.userDeniedPermissionExpiries.get(id), scopes, "deny",
                        scope -> scope.deniedPermissions)
                : entries(config.userPermissions.get(id), config.userPermissionExpiries.get(id), scopes, "allow",
                        scope -> scope.permissions);
    }

    /** {@code value}, then in parentheses where it applies and the time it has left, when either is set. */
    public static String entry(String value, String context, long secondsLeft) {
        List<String> notes = new ArrayList<>();
        if (context != null && !context.isEmpty()) notes.add(Contexts.describe(context));
        if (secondsLeft > 0) notes.add(Expiry.describe(secondsLeft) + " left");
        return notes.isEmpty() ? value : value + " (" + String.join(", ", notes) + ")";
    }

    private static <S extends GradesConfig.Scoped> List<String> entries(Collection<String> global, Map<String, Long> expiries,
                                                                       Map<String, S> scopes, String kind,
                                                                       java.util.function.Function<S, Collection<String>> inScope) {
        List<String> out = new ArrayList<>();
        if (global != null) {
            for (String value : new TreeSet<>(global)) {
                Long at = expiries == null ? null : expiries.get(value);
                out.add(entry(value, null, at == null ? 0 : Math.max(1, at - Expiry.now())));
            }
        }
        scopes.forEach((context, scope) -> {
            Collection<String> values = inScope.apply(scope);
            if (values == null) return;
            for (String value : new TreeSet<>(values)) out.add(entry(value, context, Scopes.remaining(scope, kind, value)));
        });
        return out;
    }

    private static String join(List<String> entries) {
        return entries.isEmpty() ? "none" : String.join(", ", entries);
    }
}
