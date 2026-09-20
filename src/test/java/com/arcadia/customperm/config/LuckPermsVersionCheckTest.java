/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.config;

import com.arcadia.customperm.util.VersionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests of {@code VersionUtils.isVersionAtLeast()}, AC5 (NFR12).
 * Zéro import NeoForge/Minecraft/LuckPerms — tests JUnit 5 purs.
 *
 * Note: {@code VersionUtils} is a pure Java utility class with no NeoForge dependency, pulled
 * out of {@code CustomPerm} so it can be tested without a NeoForge runtime.
 *
 * Limites intentionnelles (déférées à É6.2 GameTest) :
 *   - {@code CustomPerm.isLuckPermsVersionCompatible()} requiert {@code ModList.get()} (NeoForge runtime)
 *   - checking AC5 at runtime against a real, too-old LuckPerms
 *
 * Covers what is testable in pure Java:
 *   - Limite exacte : 5.4.150 incluse → true
 *   - En-dessous : 5.4.149 → false
 *   - Au-dessus : patch, minor, major supérieurs → true
 *   - En-dessous : minor, major inférieurs → false
 *   - Patch absent : "5.4" → patch 0 < 150 → false
 *   - malformed versions (SNAPSHOT, a tag, empty, null) answer false, on the safe side
 */
class LuckPermsVersionCheckTest {

    // ── Limite exacte ────────────────────────────────────────────────────────

    @Test
    void shouldReturnTrue_whenVersionIsExactMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("5.4.150", 5, 4, 150),
                "the exact 5.4.150 must be accepted, the bound being inclusive (NFR12)");
    }

    // ── Below the bound ──────────────────────────────────────────────────────

    @Test
    void shouldReturnFalse_whenPatchIsOneBelowMinimum() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4.149", 5, 4, 150),
                "5.4.149 is one patch below the bound and must be refused");
    }

    @Test
    void shouldReturnFalse_whenMinorIsBelowMinimum() {
        assertFalse(VersionUtils.isVersionAtLeast("5.3.200", 5, 4, 150),
                "5.3.200: a lower minor, which a high patch does not make up for");
    }

    @Test
    void shouldReturnFalse_whenMajorIsBelowMinimum() {
        assertFalse(VersionUtils.isVersionAtLeast("4.9.999", 5, 4, 150),
                "4.9.999: a lower major, so refused");
    }

    // ── Above the bound ──────────────────────────────────────────────────────

    @Test
    void shouldReturnTrue_whenPatchIsAboveMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("5.4.151", 5, 4, 150),
                "5.4.151: one patch above, so accepted");
    }

    @Test
    void shouldReturnTrue_whenMinorIsAboveMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("5.5.0", 5, 4, 150),
                "5.5.0: a higher minor, where patch 0 does not matter");
    }

    @Test
    void shouldReturnTrue_whenMajorIsAboveMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("6.0.0", 5, 4, 150),
                "6.0.0: a higher major, so accepted");
    }

    // ── Patch absent ──────────────────────────────────────────────────────────

    @Test
    void shouldReturnFalse_whenPatchIsAbsent() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4", 5, 4, 150),
                "\"5.4\": a missing patch counts as 0, and 0 < 150, so false");
    }

    // ── Versions malformées → false (sécurité) ────────────────────────────────

    @Test
    void shouldReturnFalse_whenVersionHasSnapshotTag() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4-SNAPSHOT", 5, 4, 150),
                "\"5.4-SNAPSHOT\": the split on [.\\-] gives [5, 4, SNAPSHOT], a NumberFormatException, so false");
    }

    @Test
    void shouldReturnFalse_whenPatchVersionHasSnapshotTag() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4.150-SNAPSHOT", 5, 4, 150),
                "\"5.4.150-SNAPSHOT\" must be refused, like every other prerelease");
    }

    @Test
    void shouldReturnFalse_whenVersionIsNonNumeric() {
        assertFalse(VersionUtils.isVersionAtLeast("invalid", 5, 4, 150),
                "\"invalid\": the parse throws NumberFormatException, so false");
    }

    @Test
    void shouldReturnFalse_whenVersionIsEmpty() {
        assertFalse(VersionUtils.isVersionAtLeast("", 5, 4, 150),
                "an empty version is caught by the leading guard, so false");
    }

    @Test
    void shouldReturnFalse_whenVersionIsNull() {
        assertFalse(VersionUtils.isVersionAtLeast(null, 5, 4, 150),
                "Version null : guard en tête → false");
    }
}
