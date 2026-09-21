/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GapReaderTest {

    @Test
    void consecutiveNumbersLeaveNothingToWaitFor() {
        GapReader reader = new GapReader();
        assertTrue(reader.accept(1, 0));
        assertTrue(reader.accept(2, 0));
        assertEquals(2, reader.last());
        assertTrue(reader.missing().isEmpty());
        assertFalse(reader.accept(2, 0), "a number already read is not new");
    }

    @Test
    void aSkippedNumberIsWaitedForAndTakenOnceWhenItCommits() {
        GapReader reader = new GapReader();
        reader.accept(1, 0);
        reader.accept(4, 0);
        assertEquals(Set.of(2L, 3L), reader.missing());
        assertTrue(reader.accept(3, 1000), "the late commit is read");
        assertFalse(reader.accept(3, 1000), "and only once");
        assertEquals(Set.of(2L), reader.missing());
    }

    @Test
    void aNumberNeverFilledIsForgottenAfterTheWait() {
        GapReader reader = new GapReader();
        reader.accept(1, 0);
        reader.accept(3, 0);
        reader.prune(GapReader.WAIT_MILLIS);
        assertEquals(Set.of(2L), reader.missing(), "still waited for at the limit");
        reader.prune(GapReader.WAIT_MILLIS + 1);
        assertTrue(reader.missing().isEmpty(), "a rolled back insert leaves a hole for ever");
    }

    @Test
    void startingAfterANumberSkipsWhatIsAlreadyThere() {
        GapReader reader = new GapReader();
        reader.startAfter(40);
        assertFalse(reader.accept(12, 0));
        assertTrue(reader.accept(41, 0));
        assertTrue(reader.missing().isEmpty());
    }

    @Test
    void aNumberSkippedOnTheVeryFirstRowIsStillWaitedFor() {
        GapReader reader = new GapReader();
        assertTrue(reader.accept(3, 0), "the first row read is new");
        assertEquals(Set.of(1L, 2L), reader.missing(),
                "a cluster joining an empty table starts at 0, and a late commit below the first row read must "
                        + "still be asked for by number");
        assertTrue(reader.accept(1, 10), "the late commit is read");
        assertEquals(Set.of(2L), reader.missing());
    }

    @Test
    void startingAtZeroOnALongTableStillRefusesThousandsOfNumbers() {
        GapReader reader = new GapReader();
        reader.accept(GapReader.MISSING_MAX + 2, 0);
        assertTrue(reader.missing().isEmpty(), "the bound, not the starting point, is what caps the waiting list");
    }

    @Test
    void aHugeJumpIsNotTurnedIntoThousandsOfNumbersToWaitFor() {
        GapReader reader = new GapReader();
        reader.accept(1, 0);
        reader.accept(1 + GapReader.MISSING_MAX + 10, 0);
        assertTrue(reader.missing().isEmpty());
    }
}
