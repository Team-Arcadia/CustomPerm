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
 * Utilitaires de comparaison de version sémantique.
 * Classe pure Java — aucune dépendance NeoForge/Minecraft/LuckPerms.
 * Testable en JUnit 5 sans runtime NeoForge.
 */
public final class VersionUtils {

    private VersionUtils() {
        // Classe utilitaire — pas d'instanciation
    }

    /**
     * Compare une version sémantique sous forme {@code "major.minor.patch"} à un seuil minimum.
     *
     * <p>Règles de parsing :
     * <ul>
     *   <li>Split sur {@code [.\-]} — tolère les séparateurs point et tiret.</li>
     *   <li>Patch absent → traité comme 0.</li>
     *   <li>Toute partie non numérique (ex. {@code SNAPSHOT}) → retourne {@code false}.</li>
     *   <li>{@code null} ou chaîne vide → retourne {@code false}.</li>
     * </ul>
     *
     * @param version  chaîne de version à comparer (ex. {@code "5.4.150"})
     * @param minMajor major minimum attendu
     * @param minMinor minor minimum attendu
     * @param minPatch patch minimum attendu (inclus)
     * @return {@code true} si {@code version ≥ minMajor.minMinor.minPatch}
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
