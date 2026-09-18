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
import com.arcadia.customperm.config.CommandsConfig;
import com.arcadia.customperm.config.GradesConfig;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Writes an {@link ImportPlan} into the configuration. Server thread only, and deliberately not behind
 * {@link GradeAdmin#unavailable()}: every other grade operation refuses while LuckPerms is the active
 * backend, but an import happens precisely then, LuckPerms being what it reads. What the admin must be
 * told instead is that the grades it writes decide nothing until LuckPerms is removed.
 *
 * <p>A timestamped backup of every config file is taken before anything is written, so the way back is
 * a file copy rather than an undo nobody wrote.
 */
public final class ImportAdmin {

    /**
     * How long a preview stays good for. An import is applied against what was read, not against what
     * LuckPerms says now: past this, the report an admin is about to confirm may no longer describe
     * anything, so it is read again rather than trusted.
     */
    private static final Duration PREVIEW_KEEPS = Duration.ofMinutes(10);

    /** One preview per admin, so two of them cannot confirm each other's plan. */
    private static final Map<String, Preview> PREVIEWS = new HashMap<>();

    private record Preview(ImportPlan plan, boolean exposeCommands, Instant read) {
    }

    private ImportAdmin() {
    }

    /** Remembers what an admin has just been shown, so confirming applies that and nothing else. */
    public static void remember(String admin, ImportPlan plan, boolean exposeCommands) {
        PREVIEWS.put(admin, new Preview(plan, exposeCommands, Instant.now()));
    }

    /** Whether the preview this admin holds was read with the commands exposed; false when there is none. */
    public static boolean previewedWithCommands(String admin) {
        Preview preview = PREVIEWS.get(admin);
        return preview != null && preview.exposeCommands();
    }

    /** What this admin previewed, or {@code null} when they previewed nothing or did it too long ago. */
    public static ImportPlan previewed(String admin) {
        Preview preview = PREVIEWS.get(admin);
        if (preview == null) return null;
        if (Duration.between(preview.read(), Instant.now()).compareTo(PREVIEW_KEEPS) > 0) {
            PREVIEWS.remove(admin);
            return null;
        }
        return preview.plan();
    }

    /** Forgets a preview once it has been applied: confirming twice would import the same thing twice. */
    public static void forget(String admin) {
        PREVIEWS.remove(admin);
    }

    /** The refusal when there is nothing to read, or {@code null} when LuckPerms can be imported. */
    public static AdminResult unavailable() {
        return CustomPerm.isLuckPermsActive() ? null
                : AdminResult.fail("Nothing to import: LuckPerms is not running on this server.");
    }

    private static GradesConfig grades() {
        return CustomPerm.configManager.getGrades();
    }

    /**
     * Applies the plan.
     *
     * @param replace true to overwrite a grade that already exists, false to add to it and say so
     */
    public static AdminResult apply(MinecraftServer server, ImportPlan plan, boolean replace) {
        if (plan == null || plan.isEmpty()) {
            return AdminResult.fail("Nothing to import: preview it first, and check the report.");
        }
        CustomPerm.configManager.backupNow();

        List<String> refused = new ArrayList<>();
        List<String> merged = new ArrayList<>();
        int gradesWritten = 0;
        for (ImportPlan.Grade source : plan.grades()) {
            if (!GradeAdmin.validName(source.name())) {
                refused.add(source.name());
                continue;
            }
            GradesConfig.Grade target = grades().grades.get(source.name());
            if (target == null) {
                target = new GradesConfig.Grade();
                target.name = source.name();
                target.weight = source.weight();
                grades().grades.put(source.name(), target);
            } else if (replace) {
                target.permissions.clear();
                target.deniedPermissions.clear();
                target.permissionExpiries.clear();
                target.deniedPermissionExpiries.clear();
                target.parents.clear();
                target.deniedParents.clear();
                target.weight = source.weight();
                target.prefix = null;
                target.suffix = null;
            } else {
                // A grade that already exists keeps its weight: the number an admin set by hand here is
                // a decision, and silently taking the one from LuckPerms would undo it.
                merged.add(source.name());
            }
            // A temporary node keeps its expiry only when the import adds it: one already here for good stays so.
            for (String node : source.allow()) {
                Long at = source.expiries().get("allow:" + node);
                if (target.permissions.add(node) && at != null) target.permissionExpiries.put(node, at);
            }
            for (String node : source.deny()) {
                Long at = source.expiries().get("deny:" + node);
                if (target.deniedPermissions.add(node) && at != null) target.deniedPermissionExpiries.put(node, at);
            }
            addAll(target.parents, source.parents());
            addAll(target.deniedParents, source.deniedParents());
            // Adding keeps a prefix already set here, like the weight: it is what the admin chose.
            if (target.prefix == null) target.prefix = source.prefix();
            if (target.suffix == null) target.suffix = source.suffix();
            gradesWritten++;
        }

        int playersWritten = 0;
        for (ImportPlan.Player source : plan.players()) {
            if (replace) {
                grades().userGrades.remove(source.uuid());
                grades().userDeniedGrades.remove(source.uuid());
                grades().userPermissions.remove(source.uuid());
                grades().userDeniedPermissions.remove(source.uuid());
                grades().userPrefixes.remove(source.uuid());
                grades().userSuffixes.remove(source.uuid());
                grades().userGradeExpiries.remove(source.uuid());
                grades().userDeniedGradeExpiries.remove(source.uuid());
                grades().userPermissionExpiries.remove(source.uuid());
                grades().userDeniedPermissionExpiries.remove(source.uuid());
            }
            String uuid = source.uuid();
            List<String> held = grades().userGrades.computeIfAbsent(uuid, k -> new ArrayList<>());
            for (String grade : source.grades()) {
                if (held.contains(grade)) continue;
                held.add(grade);
                timed(grades().userGradeExpiries, uuid, grade, source.expiries().get("grade:" + grade));
            }
            List<String> refusing = grades().userDeniedGrades.computeIfAbsent(uuid, k -> new ArrayList<>());
            for (String grade : source.deniedGrades()) {
                if (refusing.contains(grade)) continue;
                refusing.add(grade);
                timed(grades().userDeniedGradeExpiries, uuid, grade, source.expiries().get("refuse:" + grade));
            }
            java.util.Set<String> allowed = grades().userPermissions.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
            for (String node : source.allow()) {
                if (allowed.add(node)) timed(grades().userPermissionExpiries, uuid, node, source.expiries().get("allow:" + node));
            }
            java.util.Set<String> denied = grades().userDeniedPermissions.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
            for (String node : source.deny()) {
                if (denied.add(node)) timed(grades().userDeniedPermissionExpiries, uuid, node, source.expiries().get("deny:" + node));
            }
            if (source.prefix() != null) grades().userPrefixes.putIfAbsent(source.uuid(), source.prefix());
            if (source.suffix() != null) grades().userSuffixes.putIfAbsent(source.uuid(), source.suffix());
            playersWritten++;
        }
        // An entry left empty by a player who only carried entries that were not imported would show them
        // as holding something they do not.
        grades().normalize();

        CommandsConfig commands = CustomPerm.configManager.getCommands();
        int exposed = 0;
        for (String command : plan.exposeCommands()) {
            if (commands.grantedCommands.add(command)) exposed++;
        }

        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        CustomPerm.LOGGER.info("[CustomPerm] Imported from LuckPerms: {} grade(s), {} player(s), {} command(s) exposed.",
                gradesWritten, playersWritten, exposed);

        AdminResult result = AdminResult.ok("Imported " + gradesWritten + " grade(s), " + playersWritten
                + " player(s) and exposed " + exposed + " command(s).").warn(warning);
        if (!merged.isEmpty()) {
            result = result.note("Added to grades that already existed, keeping their weight: "
                    + String.join(", ", merged) + ".");
        }
        if (!refused.isEmpty()) {
            result = result.note("Skipped, the name is not one a grade can have: " + String.join(", ", refused) + ".");
        }
        if (CustomPerm.isLuckPermsActive()) {
            result = result.note("LuckPerms still decides permissions: what was imported takes over once "
                    + "LuckPerms is removed, and can be read on the Grades page until then.");
        }
        return result.note("Every config file was copied to config/arcadia/customperm/backup/ first.");
    }

    /** Records the expiry of an entry the import just added to one player, when it has one. */
    private static void timed(Map<String, Map<String, Long>> byUser, String uuid, String key, Long at) {
        if (at != null) byUser.computeIfAbsent(uuid, k -> new HashMap<>()).put(key, at);
    }

    private static void addAll(List<String> target, List<String> values) {
        for (String value : values) {
            if (!target.contains(value)) target.add(value);
        }
    }
}
