/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import java.util.List;

/**
 * One move along a track, worked out before anything is written. Pure Java, so the rules are tested
 * without a server: they are LuckPerms' own.
 *
 * <ul>
 *   <li>Promoting a player on no rung of the track puts them on the first one.</li>
 *   <li>Promoting from the top rung, and demoting from nowhere, changes nothing.</li>
 *   <li>Demoting from the first rung takes the player off the track.</li>
 *   <li>A player on several rungs of one track is refused rather than guessed at: which one they are
 *       "on" is exactly what the admin has to decide.</li>
 * </ul>
 *
 * @param from    the grade given up, {@code null} when none is
 * @param to      the grade given, {@code null} when none is
 * @param problem why nothing can be done, {@code null} when the move applies or changes nothing
 */
public record TrackStep(String from, String to, String problem) {

    public boolean changes() {
        return problem == null && (from != null || to != null);
    }

    /**
     * @param rungs the track, lowest first
     * @param held  the grades the player holds everywhere
     * @param up    true to promote, false to demote
     */
    public static TrackStep of(List<String> rungs, List<String> held, boolean up) {
        if (rungs.isEmpty()) return new TrackStep(null, null, "the track has no grade yet");
        List<String> on = rungs.stream().filter(held::contains).toList();
        if (on.size() > 1) {
            return new TrackStep(null, null, "they hold several grades of it (" + String.join(", ", on)
                    + "): unassign all but one first");
        }
        if (on.isEmpty()) {
            return up ? new TrackStep(null, rungs.get(0), null) : new TrackStep(null, null, null);
        }
        String current = on.get(0);
        int rung = rungs.indexOf(current);
        if (up) {
            return rung == rungs.size() - 1 ? new TrackStep(null, null, null)
                    : new TrackStep(current, rungs.get(rung + 1), null);
        }
        return new TrackStep(current, rung == 0 ? null : rungs.get(rung - 1), null);
    }
}
