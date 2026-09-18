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
import com.arcadia.customperm.perm.PermissionNodes;
import com.arcadia.customperm.perm.PermissionResolver;
import com.arcadia.customperm.perm.Tristate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Carries the grades over to LuckPerms, for the server that built them here and installs LuckPerms later,
 * for chat prefixes say, and wants them to keep deciding. Server thread only, except for the counters the
 * worker thread moves.
 *
 * <p>The import could promise a way back, a copy of files it controls. An export writes into LuckPerms'
 * storage, which cannot be copied from here: what stands in for a backup is telling the admin to run
 * {@code /lp export} first, writing the groups before the players, and saying exactly where a failure
 * stopped.
 */
public final class ExportAdmin {

    /** How long a preview stays good for, as for the import: past it the report is read again. */
    private static final Duration PREVIEW_KEEPS = Duration.ofMinutes(10);

    /** One preview per admin, so two of them cannot confirm each other's plan. */
    private static final Map<String, Preview> PREVIEWS = new HashMap<>();

    /** One export at a time: two writing the same holders would each save over the other. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile int done;
    private static volatile int total;

    private record Preview(ExportPlan plan, Instant read) {
    }

    /** Where a running export is, for the page and for the admin who asks again. */
    public record Progress(boolean running, int done, int total) {
    }

    private ExportAdmin() {
    }

    /** The refusal when there is nothing to write to, or {@code null} when LuckPerms can be written. */
    public static AdminResult unavailable() {
        return CustomPerm.isLuckPermsActive() ? null
                : AdminResult.fail("Nothing to export to: LuckPerms is not running on this server.");
    }

    /** What the configuration would become in LuckPerms, before anything is known about LuckPerms itself. */
    public static ExportPlan plan() {
        return ExportPlan.of(CustomPerm.configManager.getGrades(),
                CustomPerm.configManager.getSettings().defaultGrade);
    }

    public static void remember(String admin, ExportPlan plan) {
        PREVIEWS.put(admin, new Preview(plan, Instant.now()));
    }

    /** What this admin previewed, or {@code null} when they previewed nothing or did it too long ago. */
    public static ExportPlan previewed(String admin) {
        Preview preview = PREVIEWS.get(admin);
        if (preview == null) return null;
        if (Duration.between(preview.read(), Instant.now()).compareTo(PREVIEW_KEEPS) > 0) {
            PREVIEWS.remove(admin);
            return null;
        }
        return preview.plan();
    }

    public static void forget(String admin) {
        PREVIEWS.remove(admin);
    }

    public static Progress progress() {
        return new Progress(RUNNING.get(), done, total);
    }

    /**
     * Refuses an export that would take away the admin's own access to the grades, as
     * {@link GradeAdmin#guarded} does for a change here. It cannot be undone after the fact, LuckPerms
     * being what is written, so it is read on the plan before. Adding refuses only an explicit denial the
     * plan carries; replacing clears the customperm nodes of every holder it writes, so the plan itself
     * must then allow it. The console is never checked.
     */
    public static AdminResult lockout(ServerPlayer admin, ExportPlan plan, boolean replace) {
        if (admin == null) return null;
        var grades = plan.asConfig();
        for (String node : new String[]{PermissionNodes.ADMIN, PermissionNodes.MANAGE_GRADES}) {
            Tristate verdict = PermissionResolver.check(grades, admin.getUUID(), node, plan.defaultGrade());
            if (verdict == Tristate.DENY || (replace && verdict != Tristate.ALLOW)) {
                return AdminResult.fail("Refused: once in LuckPerms, " + (replace
                        ? "what is exported would no longer give you " : "what is exported would deny you ")
                        + node + ". Allow it to you in one of your grades, or export from the console.");
            }
        }
        return null;
    }

    /**
     * Starts writing the plan and answers at once. {@code onProgress} and {@code onDone} run on the server
     * thread; progress is told about twenty times over the whole run rather than after every holder.
     */
    public static AdminResult start(MinecraftServer server, ExportPlan plan, boolean replace,
                                    Runnable onProgress, Consumer<AdminResult> onDone) {
        if (plan == null || plan.isEmpty()) {
            return AdminResult.fail("Nothing to export: preview it first, and check the report.");
        }
        if (!RUNNING.compareAndSet(false, true)) {
            return AdminResult.fail("An export is already running: " + done + " of " + total + " written.");
        }
        done = 0;
        total = plan.holders();
        int step = Math.max(1, total / 20);
        int[] told = {0};
        CustomPerm.LOGGER.info("[CustomPerm] Exporting to LuckPerms: {} group(s), {} player(s), {}.",
                plan.groups().size(), plan.players().size(), replace ? "replacing" : "adding");
        // Inside the LuckPerms branch, and called rather than referenced: a method reference would resolve its
        // target eagerly and drag LuckPerms onto a server that does not have it.
        com.arcadia.customperm.perm.lp.LuckPermsExport.write(plan, replace, written -> {
            done = written;
            if (written - told[0] >= step && written < total) {
                told[0] = written;
                server.execute(onProgress);
            }
        }).whenComplete((outcome, error) -> server.execute(() -> {
            RUNNING.set(false);
            onDone.accept(result(plan, outcome, error));
        }));
        return AdminResult.ok("Exporting " + total + " holder(s) to LuckPerms in the background, this can take "
                + "a while on a large server.");
    }

    private static AdminResult result(ExportPlan plan, ExportPlan.Outcome outcome, Throwable error) {
        if (outcome == null) {
            outcome = new ExportPlan.Outcome(0, 0, 0, "LuckPerms", error == null ? "no answer" : error.getMessage());
        }
        String written = outcome.groupsWritten() + " group(s) and " + outcome.playersWritten() + " player(s)";
        AdminResult result;
        if (outcome.complete()) {
            CustomPerm.LOGGER.info("[CustomPerm] Exported to LuckPerms: {}.", written);
            result = AdminResult.ok("Exported " + written + " to LuckPerms.");
        } else {
            CustomPerm.LOGGER.warn("[CustomPerm] Export to LuckPerms stopped at {}: {}. Written before it: {}.",
                    outcome.stoppedAt(), outcome.error(), written);
            result = AdminResult.fail("Stopped at " + outcome.stoppedAt() + ": " + outcome.error()
                    + ". Written before it: " + written + " of " + plan.groups().size() + " and "
                    + plan.players().size() + "; nothing after it.")
                    .note("LuckPerms is half written: restore it with /lp import <file> from the export taken "
                            + "before, or fix the cause and export again, which adds only what is missing.");
        }
        if (outcome.kept() > 0) {
            result = result.note(outcome.kept() + " entrie(s) LuckPerms already set the other way were kept as "
                    + "they were: adding never takes away.");
        }
        return result.note("grades.json is unchanged: the grades are now in LuckPerms as well, which decides.");
    }
}
