/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Moves along a track, worked out without a server: LuckPerms' promote and demote rules. */
class TrackStepTest {

    private static final List<String> LADDER = List.of("member", "vip", "staff");

    @Test
    void promoteFromNowherePutsThePlayerOnTheFirstRung() {
        assertEquals(new TrackStep(null, "member", null), TrackStep.of(LADDER, List.of("builder"), true));
    }

    @Test
    void promoteMovesOneRungUp() {
        assertEquals(new TrackStep("member", "vip", null), TrackStep.of(LADDER, List.of("builder", "member"), true));
    }

    @Test
    void promoteFromTheTopChangesNothing() {
        TrackStep step = TrackStep.of(LADDER, List.of("staff"), true);
        assertNull(step.problem());
        assertFalse(step.changes());
    }

    @Test
    void demoteMovesOneRungDown() {
        assertEquals(new TrackStep("staff", "vip", null), TrackStep.of(LADDER, List.of("staff"), false));
    }

    @Test
    void demoteFromTheFirstRungTakesThePlayerOff() {
        assertEquals(new TrackStep("member", null, null), TrackStep.of(LADDER, List.of("member"), false));
    }

    @Test
    void demoteFromNowhereChangesNothing() {
        assertFalse(TrackStep.of(LADDER, List.of(), false).changes());
    }

    @Test
    void severalRungsAreRefusedRatherThanGuessed() {
        TrackStep step = TrackStep.of(LADDER, List.of("vip", "member"), true);
        assertNotNull(step.problem());
        assertTrue(step.problem().contains("member, vip"), "named in track order: " + step.problem());
    }

    @Test
    void anEmptyTrackIsRefused() {
        assertNotNull(TrackStep.of(List.of(), List.of("vip"), true).problem());
    }
}
