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
import com.arcadia.customperm.perm.Expiry;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Meta on grades and players: key to value, like LuckPerms meta. A mod that declares a number or text
 * permission node reads the meta named after it. Meta changes what mods read, never what a player may run,
 * so a change resends no command tree and is not guarded against locking the admin out. Server thread only.
 */
public final class MetaAdmin {

    /** Keys are the names of permission nodes: lowercase, dotted. */
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.:\\-]{1,128}");
    public static final int VALUE_MAX = 256;

    private MetaAdmin() {
    }

    private static GradesConfig grades() {
        return CustomPerm.configManager.getGrades();
    }

    /** One holder's meta and expiries, in one context or everywhere; created when {@code create}. */
    private record Target(Map<String, String> meta, Map<String, Long> expiries) {
    }

    public static AdminResult setOnGrade(MinecraftServer server, String gradeName, String rawKey, String value,
                                         long seconds, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        Target target = context == null ? new Target(grade.meta, grade.metaExpiries)
                : scopeTarget(Scopes.of(grade, context));
        return set(target, gradeName, rawKey, value, seconds, context, () -> Scopes.tidy(grade));
    }

    public static AdminResult unsetOnGrade(MinecraftServer server, String gradeName, String rawKey, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        GradesConfig.Scoped scope = context == null ? null : Scopes.find(grade, context);
        Target target = context == null ? new Target(grade.meta, grade.metaExpiries)
                : scope == null ? null : scopeTarget(scope);
        return unset(target, gradeName, rawKey, context, () -> Scopes.tidy(grade));
    }

    public static AdminResult setOnPlayer(MinecraftServer server, UUID uuid, String displayName, String rawKey,
                                          String value, long seconds, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        Target target = context == null
                ? new Target(grades().userMeta.computeIfAbsent(uuid.toString(), k -> new TreeMap<>()),
                        Expiries.of(grades().userMetaExpiries, uuid))
                : scopeTarget(Scopes.of(grades(), uuid, context));
        return set(target, displayName, rawKey, value, seconds, context, () -> tidy(uuid));
    }

    public static AdminResult unsetOnPlayer(MinecraftServer server, UUID uuid, String displayName, String rawKey,
                                            String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        Target target;
        if (context == null) {
            Map<String, String> meta = grades().userMeta.get(uuid.toString());
            target = meta == null ? null
                    : new Target(meta, grades().userMetaExpiries.getOrDefault(uuid.toString(), new HashMap<>()));
        } else {
            GradesConfig.Scoped scope = Scopes.find(grades(), uuid, context);
            target = scope == null ? null : scopeTarget(scope);
        }
        return unset(target, displayName, rawKey, context, () -> tidy(uuid));
    }

    private static Target scopeTarget(GradesConfig.Scoped scope) {
        return new Target(scope.meta, scope.metaExpiries);
    }

    /** Drops what a change left empty for one player, so the file keeps no empty entries. */
    private static void tidy(UUID uuid) {
        Map<String, String> meta = grades().userMeta.get(uuid.toString());
        if (meta != null && meta.isEmpty()) grades().userMeta.remove(uuid.toString());
        Expiries.tidy(grades().userMetaExpiries, uuid);
        Scopes.tidy(grades(), uuid);
    }

    private static AdminResult set(Target target, String holder, String rawKey, String value, long seconds,
                                   String context, Runnable tidy) {
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        String problem = problem(key, value);
        if (problem != null) {
            tidy.run();
            return AdminResult.fail(problem);
        }
        String before = target.meta().put(key, value);
        boolean timed = Expiries.apply(target.expiries(), key, seconds);
        tidy.run();
        String where = Scopes.span(context);
        if (value.equals(before) && !timed) {
            return AdminResult.ok(holder + " already carries " + key + "=" + value + where + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        String what = key + "=" + value + " on " + holder + where;
        if (value.equals(before)) return AdminResult.ok(what + " " + Expiries.became(seconds)).warn(warning);
        return AdminResult.ok((before == null ? "Set " : "Replaced " + key + "=" + before + " with ") + what
                + Expiries.span(seconds)).warn(warning);
    }

    private static AdminResult unset(Target target, String holder, String rawKey, String context, Runnable tidy) {
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        String removed = target == null ? null : target.meta().remove(key);
        if (removed == null) {
            return AdminResult.ok(holder + " carries no meta " + key + Scopes.span(context) + " — no change.");
        }
        target.expiries().remove(key);
        tidy.run();
        String warning = ConfigAdmin.persist();
        return AdminResult.ok("Removed " + key + "=" + removed + " from " + holder + Scopes.span(context)).warn(warning);
    }

    /** Why a key or value cannot be stored, or {@code null}. */
    static String problem(String key, String value) {
        if (!KEY.matcher(key).matches()) {
            return "Invalid meta key '" + key + "': use lowercase letters, digits and _ . : -, as a permission node is written.";
        }
        if (value == null || value.isEmpty() || value.length() > VALUE_MAX) {
            return "A meta value holds 1 to " + VALUE_MAX + " characters.";
        }
        if (value.chars().anyMatch(Character::isISOControl)) return "A meta value cannot hold a line break or a control character.";
        return null;
    }

    /** A grade's meta for a listing: {@code key=value}, with the time left and the context of each. */
    public static List<String> ofGrade(String gradeName) {
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return List.of();
        List<String> out = new ArrayList<>();
        listing(out, grade.meta, grade.metaExpiries, "");
        new TreeMap<>(grade.contexts).forEach((context, scope) -> listing(out, scope.meta, scope.metaExpiries, context));
        return out;
    }

    /** A player's own meta for a listing. */
    public static List<String> ofPlayer(UUID uuid) {
        List<String> out = new ArrayList<>();
        listing(out, grades().userMeta.get(uuid.toString()), grades().userMetaExpiries.get(uuid.toString()), "");
        Map<String, GradesConfig.UserScoped> scopes = grades().userContexts.get(uuid.toString());
        if (scopes != null) new TreeMap<>(scopes).forEach((context, scope) -> listing(out, scope.meta, scope.metaExpiries, context));
        return out;
    }

    private static void listing(List<String> out, Map<String, String> meta, Map<String, Long> expiries, String context) {
        if (meta == null) return;
        long now = Expiry.now();
        new TreeMap<>(meta).forEach((key, value) -> {
            Long at = expiries == null ? null : expiries.get(key);
            if (at != null && at <= now) return;
            out.add(key + "=" + value + (at == null ? "" : " (" + Expiry.describe(at - now) + " left)")
                    + Scopes.span(context));
        });
    }
}
