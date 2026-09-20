/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.util;

/**
 * Semantic version comparison helpers.
 * Pure Java, with no NeoForge, Minecraft or LuckPerms dependency, so JUnit 5 tests it without a
 * NeoForge runtime.
 */
public final class VersionUtils {

    private VersionUtils() {
        // Utility class, never instantiated.
    }

    /**
     * Compares a {@code "major.minor.patch"} semantic version against a minimum.
     *
     * <p>Parsing rules:
     * <ul>
     *   <li>Split on {@code [.\-]}, so a dot and a dash both separate.</li>
     *   <li>A missing patch counts as 0.</li>
     *   <li>Any non-numeric part (for example {@code SNAPSHOT}) returns {@code false}.</li>
     *   <li>{@code null} or an empty string returns {@code false}.</li>
     * </ul>
     *
     * @param version  the version string to compare (for example {@code "5.4.150"})
     * @param minMajor the minimum major expected
     * @param minMinor the minimum minor expected
     * @param minPatch the minimum patch expected, inclusive
     * @return {@code true} when {@code version} is at least {@code minMajor.minMinor.minPatch}
     */
    public static boolean isVersionAtLeast(String version, int minMajor, int minMinor, int minPatch) {
        if (version == null || version.isEmpty()) return false;
        try {
            String[] parts = version.split("[.\\-]");
            if (parts.length > 3) return false;
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            if (major != minMajor) return major > minMajor;
            if (minor != minMinor) return minor > minMinor;
            return patch >= minPatch;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
