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
import com.arcadia.customperm.perm.Contexts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The context side of adding and removing an entry, shared by {@link GradeAdmin} and {@link UserAdmin}. A
 * context arrives as the admin typed it ({@code world=the_nether}) and is stored in its parsed form; blank
 * means everywhere.
 */
final class Scopes {

    /** Returned by {@link #parse} for a context that cannot be read. */
    static final String INVALID = "\0";

    private Scopes() {
    }

    /**
     * The stored form of {@code raw}, {@code null} for everywhere, {@link #INVALID} when it is not a context
     * or names a key nothing on this server sets, which would make the entry apply nowhere.
     */
    static String parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String parsed = Contexts.parse(raw);
        if (parsed == null) return INVALID;
        return Contexts.undeclared(parsed, com.arcadia.customperm.CustomPerm.configManager.getSettings().staticContexts) == null
                ? parsed : INVALID;
    }

    static AdminResult invalid(String raw) {
        String parsed = Contexts.parse(raw);
        if (parsed != null) {
            String key = Contexts.undeclared(parsed, com.arcadia.customperm.CustomPerm.configManager.getSettings().staticContexts);
            return AdminResult.fail("Nothing on this server sets '" + key + "': an entry limited to it would apply "
                    + "nowhere. Set it first with /customperm contexts set " + key + " <value>.");
        }
        return AdminResult.fail("Invalid context '" + raw.trim() + "': use world=<dimension>, gamemode=<mode> or a "
                + "static context, several joined with a comma, such as world=the_nether,gamemode=creative; two "
                + "values of one key are either one (world=the_nether,world=the_end).");
    }

    /** Seconds left on the entry {@code kind:value} of {@code scope}, 0 for a permanent or absent one. */
    static long remaining(GradesConfig.Scoped scope, String kind, String value) {
        Long at = scope == null ? null : scope.expiries(kind).get(value);
        return at == null ? 0 : Math.max(1, at - com.arcadia.customperm.perm.Expiry.now());
    }

    /** {@code " in the_nether"}, or nothing for an entry that applies everywhere: the tail of a confirmation. */
    static String span(String context) {
        return context == null ? "" : " in " + Contexts.describe(context);
    }

    /** One grade's scope for {@code context}, created when absent. */
    static GradesConfig.GradeScoped of(GradesConfig.Grade grade, String context) {
        return grade.contexts.computeIfAbsent(context, k -> new GradesConfig.GradeScoped());
    }

    /** One grade's scope for {@code context}, or {@code null}. */
    static GradesConfig.GradeScoped find(GradesConfig.Grade grade, String context) {
        return grade.contexts.get(context);
    }

    /** One player's scope for {@code context}, created when absent. */
    static GradesConfig.UserScoped of(GradesConfig config, UUID uuid, String context) {
        return config.userContexts.computeIfAbsent(uuid.toString(), k -> new HashMap<>())
                .computeIfAbsent(context, k -> new GradesConfig.UserScoped());
    }

    /** One player's scope for {@code context}, or {@code null}. */
    static GradesConfig.UserScoped find(GradesConfig config, UUID uuid, String context) {
        Map<String, GradesConfig.UserScoped> scopes = config.userContexts.get(uuid.toString());
        return scopes == null ? null : scopes.get(context);
    }

    /** Drops the scopes a removal emptied, so the file does not keep empty entries. */
    static void tidy(GradesConfig.Grade grade) {
        grade.contexts.values().removeIf(GradesConfig.Scoped::isEmpty);
    }

    static void tidy(GradesConfig config, UUID uuid) {
        Map<String, GradesConfig.UserScoped> scopes = config.userContexts.get(uuid.toString());
        if (scopes == null) return;
        scopes.values().removeIf(GradesConfig.Scoped::isEmpty);
        if (scopes.isEmpty()) config.userContexts.remove(uuid.toString());
    }

    static Map<String, GradesConfig.GradeScoped> copy(Map<String, GradesConfig.GradeScoped> scopes) {
        Map<String, GradesConfig.GradeScoped> copy = new HashMap<>();
        scopes.forEach((context, scope) -> {
            GradesConfig.GradeScoped c = new GradesConfig.GradeScoped();
            copyInto(scope, c);
            c.parents.addAll(scope.parents);
            c.parentExpiries.putAll(scope.parentExpiries);
            copy.put(context, c);
        });
        return copy;
    }

    private static void copyInto(GradesConfig.Scoped from, GradesConfig.Scoped to) {
        to.permissions.addAll(from.permissions);
        to.deniedPermissions.addAll(from.deniedPermissions);
        to.refused.addAll(from.refused);
        to.prefixes = GradesConfig.ChatEntry.copy(from.prefixes);
        to.suffixes = GradesConfig.ChatEntry.copy(from.suffixes);
        to.permissionExpiries.putAll(from.permissionExpiries);
        to.deniedPermissionExpiries.putAll(from.deniedPermissionExpiries);
        to.refusedExpiries.putAll(from.refusedExpiries);
    }

    static Map<String, Map<String, GradesConfig.UserScoped>> copyUsers(
            Map<String, Map<String, GradesConfig.UserScoped>> byUser) {
        Map<String, Map<String, GradesConfig.UserScoped>> copy = new HashMap<>();
        byUser.forEach((uuid, scopes) -> {
            Map<String, GradesConfig.UserScoped> mine = new HashMap<>();
            scopes.forEach((context, scope) -> {
                GradesConfig.UserScoped c = new GradesConfig.UserScoped();
                copyInto(scope, c);
                c.grades.addAll(scope.grades);
                c.gradeExpiries.putAll(scope.gradeExpiries);
                mine.put(context, c);
            });
            copy.put(uuid, mine);
        });
        return copy;
    }
}
