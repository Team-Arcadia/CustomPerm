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
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Tracks: ordered ladders of grades, and moving a player one rung along one. Shared by
 * {@code /customperm track} and the Players page, server thread only. A track grants nothing by itself:
 * promoting is unassigning one grade and assigning the next, in one write. Like the grades, every
 * operation refuses while LuckPerms is the active backend, whose own tracks decide there.
 */
public final class TrackAdmin {

    private TrackAdmin() {
    }

    private static GradesConfig grades() {
        return CustomPerm.configManager.getGrades();
    }

    private static Map<String, List<String>> tracks() {
        return grades().tracks;
    }

    /** Every track name, sorted. */
    public static List<String> names() {
        return List.copyOf(new TreeSet<>(tracks().keySet()));
    }

    /** The grades of a track, lowest first; empty for an unknown one. */
    public static List<String> rungs(String track) {
        return List.copyOf(tracks().getOrDefault(track, List.of()));
    }

    public static AdminResult create(String name) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        if (!GradeAdmin.validName(name)) {
            return AdminResult.fail("Invalid track name '" + name + "': use 1 to 64 letters, digits, _ - . or +.");
        }
        if (tracks().containsKey(name)) return AdminResult.fail("Track already exists: " + name);
        tracks().put(name, new ArrayList<>());
        return AdminResult.ok("Created track " + name + ". Add its grades lowest first with /customperm track append.")
                .warn(ConfigAdmin.persist());
    }

    /** Deletes a track. The grades on it and the players holding them are untouched: a track grants nothing. */
    public static AdminResult delete(String name) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        if (tracks().remove(name) == null) return AdminResult.fail("No such track: " + name);
        return AdminResult.ok("Deleted track " + name + ". Its grades and who holds them are unchanged.")
                .warn(ConfigAdmin.persist());
    }

    /** Adds a grade as the new top rung. */
    public static AdminResult append(String track, String grade) {
        List<String> rungs = tracks().get(track);
        return insert(track, grade, rungs == null ? 1 : rungs.size() + 1);
    }

    /**
     * Puts a grade at {@code position}, 1 for the lowest rung, the size plus one for a new top rung.
     */
    public static AdminResult insert(String track, String grade, int position) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        List<String> rungs = tracks().get(track);
        if (rungs == null) return AdminResult.fail("No such track: " + track);
        if (!grades().grades.containsKey(grade)) return AdminResult.fail("No such grade: " + grade);
        if (rungs.contains(grade)) {
            return AdminResult.fail(grade + " is already rung " + (rungs.indexOf(grade) + 1) + " of " + track
                    + ": remove it first to move it.");
        }
        if (position < 1 || position > rungs.size() + 1) {
            return AdminResult.fail("Position " + position + " is outside " + track + ": use 1 to " + (rungs.size() + 1) + ".");
        }
        rungs.add(position - 1, grade);
        return AdminResult.ok(track + ": " + describe(rungs)).warn(ConfigAdmin.persist());
    }

    /** Takes a grade off a track; whoever holds it keeps it. */
    public static AdminResult remove(String track, String grade) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        List<String> rungs = tracks().get(track);
        if (rungs == null) return AdminResult.fail("No such track: " + track);
        if (!rungs.remove(grade)) return AdminResult.ok(grade + " is not on " + track + " — no change.");
        return AdminResult.ok(track + ": " + describe(rungs)).warn(ConfigAdmin.persist())
                .note("Players holding " + grade + " keep it.");
    }

    /** {@code member > vip > staff}, or a word for an empty track. */
    public static String describe(List<String> rungs) {
        return rungs.isEmpty() ? "no grade yet" : String.join(" > ", rungs);
    }

    /**
     * Moves one player a rung up or down a track, in one write. Only the grades held everywhere count: a
     * grade held in one world or refused is not a rung the player stands on. The grade given up takes its
     * expiry with it, and the grade given is permanent, as with LuckPerms' promote.
     */
    public static AdminResult move(MinecraftServer server, GameProfile profile, String track, boolean up) {
        return move(server, profile, track, up, null);
    }

    /**
     * {@link #move(MinecraftServer, GameProfile, String, boolean)} within {@code rawContext}, such as
     * {@code world=the_nether}; blank for everywhere. In a context the rungs are the grades held there only,
     * like LuckPerms' promote with a context: a grade held everywhere is not a rung there, and the grade
     * given is held in that context alone. A grade refused everywhere or in that context is not given.
     */
    public static AdminResult move(MinecraftServer server, GameProfile profile, String track, boolean up,
                                   String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        List<String> rungs = tracks().get(track);
        if (rungs == null) return AdminResult.fail("No such track: " + track);
        String uuid = profile.getId().toString();
        String name = profile.getName();
        String where = Scopes.span(context);
        GradesConfig.UserScoped found = context == null ? null : Scopes.find(grades(), profile.getId(), context);
        List<String> held = context == null ? grades().userGrades.getOrDefault(uuid, List.of())
                : found == null ? List.of() : found.grades;
        TrackStep step = TrackStep.of(rungs, held, up);
        if (step.problem() != null) {
            return AdminResult.fail("Cannot " + (up ? "promote " : "demote ") + name + " on " + track + where + ": "
                    + step.problem() + ".");
        }
        if (!step.changes()) {
            return AdminResult.ok(up ? name + " is already on the top rung of " + track + where + " — no change."
                    : name + " is on no rung of " + track + where + " — no change.");
        }
        if (step.to() != null) {
            if (!grades().grades.containsKey(step.to())) {
                return AdminResult.fail(track + " names " + step.to() + ", which is not a grade: fix the track first.");
            }
            if (grades().userDeniedGrades.getOrDefault(uuid, List.of()).contains(step.to())
                    || found != null && found.refused.contains(step.to())) {
                return AdminResult.fail(name + " refuses " + step.to() + (found != null && found.refused.contains(step.to())
                        ? where : "") + ": remove that refusal first.");
            }
        }
        boolean wasTemporary = context == null ? moveEverywhere(profile, step) : moveWithin(profile, context, step);
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, profile.getId());

        String message;
        if (step.from() == null) {
            message = "Put " + name + " on " + track + where + " at " + step.to();
        } else if (step.to() == null) {
            message = "Took " + name + " off " + track + where + ": " + step.from() + " was its first rung";
        } else {
            message = (up ? "Promoted " : "Demoted ") + name + " on " + track + where + ": " + step.from() + " -> "
                    + step.to();
        }
        AdminResult result = AdminResult.ok(message).warn(warning);
        if (context != null && step.to() != null
                && grades().userGrades.getOrDefault(uuid, List.of()).contains(step.to())) {
            result = result.note("They also hold " + step.to() + " everywhere, which already covers that context.");
        }
        return wasTemporary ? result.note(step.from() + " was temporary; " + (step.to() == null ? "nothing replaces it."
                : step.to() + " is held for good.")) : result;
    }

    /** Applies a step to the grades held everywhere; true when the grade given up was temporary. */
    private static boolean moveEverywhere(GameProfile profile, TrackStep step) {
        String uuid = profile.getId().toString();
        boolean wasTemporary = false;
        List<String> list = grades().userGrades.computeIfAbsent(uuid, k -> new ArrayList<>());
        if (step.from() != null) {
            list.remove(step.from());
            Map<String, Long> expiries = grades().userGradeExpiries.get(uuid);
            wasTemporary = expiries != null && expiries.containsKey(step.from());
            Expiries.forget(grades().userGradeExpiries, profile.getId(), step.from());
        }
        if (step.to() != null && !list.contains(step.to())) list.add(step.to());
        if (list.isEmpty()) grades().userGrades.remove(uuid);
        return wasTemporary;
    }

    /** Applies a step to the grades held in {@code context}; true when the grade given up was temporary. */
    private static boolean moveWithin(GameProfile profile, String context, TrackStep step) {
        GradesConfig.UserScoped scope = Scopes.of(grades(), profile.getId(), context);
        boolean wasTemporary = false;
        if (step.from() != null) {
            scope.grades.remove(step.from());
            wasTemporary = scope.gradeExpiries.remove(step.from()) != null;
        }
        if (step.to() != null && !scope.grades.contains(step.to())) scope.grades.add(step.to());
        Scopes.tidy(grades(), profile.getId());
        return wasTemporary;
    }
}
