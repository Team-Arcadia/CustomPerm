/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.lp;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LpFormat#durationSeconds} turns a field the admin typed into the argument of a
 * permission grant, so its failure mode is not a cosmetic one: reading {@code 7d} as {@code 7}
 * would hand out a rank that expires seven seconds later.
 */
class LpFormatTest {

    @Test
    void bareNumbersAreSeconds() {
        assertEquals("0", LpFormat.durationSeconds("0"));
        assertEquals("90", LpFormat.durationSeconds("90"));
        assertEquals("90", LpFormat.durationSeconds("  90  "));
    }

    @Test
    void suffixesScaleTheAmount() {
        assertEquals("45", LpFormat.durationSeconds("45s"));
        assertEquals("1800", LpFormat.durationSeconds("30m"));
        assertEquals("7200", LpFormat.durationSeconds("2h"));
        assertEquals("604800", LpFormat.durationSeconds("7d"));
        assertEquals("604800", LpFormat.durationSeconds("7D"));
    }

    @Test
    void unreadableInputFallsBackToPermanent() {
        assertEquals("0", LpFormat.durationSeconds(""));
        assertEquals("0", LpFormat.durationSeconds("   "));
        assertEquals("0", LpFormat.durationSeconds("soon"));
        assertEquals("0", LpFormat.durationSeconds("30x"));
        assertEquals("0", LpFormat.durationSeconds("m"));
    }

    @Test
    void negativeDurationsAreClampedRatherThanSentAsPastExpiries() {
        assertEquals("0", LpFormat.durationSeconds("-5"));
        assertEquals("0", LpFormat.durationSeconds("-5m"));
    }

    /**
     * Offsets sit clear of each unit boundary on purpose: the countdown is computed against
     * {@code Instant.now()} at call time, so a value of exactly one hour would render as
     * {@code 59m} whenever a second elapses between the two reads.
     */
    @Test
    void expiryReadsAsACountdown() {
        long now = Instant.now().getEpochSecond();
        assertEquals("permanent", LpFormat.expiry(0L));
        assertEquals("expired", LpFormat.expiry(now - 60));
        assertTrue(LpFormat.expiry(now + 45).endsWith("s"));
        assertEquals("1m", LpFormat.expiry(now + 90));
        assertEquals("2h", LpFormat.expiry(now + 7500));
        assertEquals("3d", LpFormat.expiry(now + 3 * 86_400 + 3600));
    }

    @Test
    void emptyContextSetReadsAsGlobal() {
        assertEquals("global", LpFormat.contexts(""));
        assertEquals("world=nether", LpFormat.contexts("world=nether"));
    }
}
