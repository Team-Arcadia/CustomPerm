package com.arcadia.customperm.config;

import com.arcadia.customperm.util.VersionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@code VersionUtils.isVersionAtLeast()} — AC5 (NFR12).
 * Zéro import NeoForge/Minecraft/LuckPerms — tests JUnit 5 purs.
 *
 * Note : {@code VersionUtils} est une classe utilitaire pure Java sans dépendance NeoForge,
 * extraite de {@code CustomPerm} pour permettre les tests sans runtime NeoForge.
 *
 * Limites intentionnelles (déférées à É6.2 GameTest) :
 *   - {@code CustomPerm.isLuckPermsVersionCompatible()} requiert {@code ModList.get()} (NeoForge runtime)
 *   - Vérification AC5 en runtime avec LP version insuffisante réelle
 *
 * Couvre les aspects testables en pur Java :
 *   - Limite exacte : 5.4.150 incluse → true
 *   - En-dessous : 5.4.149 → false
 *   - Au-dessus : patch, minor, major supérieurs → true
 *   - En-dessous : minor, major inférieurs → false
 *   - Patch absent : "5.4" → patch 0 < 150 → false
 *   - Versions malformées (SNAPSHOT, tag, vide, null) → false (sécurité)
 */
class LuckPermsVersionCheckTest {

    // ── Limite exacte ────────────────────────────────────────────────────────

    @Test
    void shouldReturnTrue_whenVersionIsExactMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("5.4.150", 5, 4, 150),
                "La version exacte 5.4.150 doit être acceptée (limite incluse — NFR12)");
    }

    // ── En-dessous de la limite ───────────────────────────────────────────────

    @Test
    void shouldReturnFalse_whenPatchIsOneBelowMinimum() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4.149", 5, 4, 150),
                "5.4.149 est un patch en-dessous de la limite — doit être refusé");
    }

    @Test
    void shouldReturnFalse_whenMinorIsBelowMinimum() {
        assertFalse(VersionUtils.isVersionAtLeast("5.3.200", 5, 4, 150),
                "5.3.200 : minor inférieur — le patch élevé ne compense pas");
    }

    @Test
    void shouldReturnFalse_whenMajorIsBelowMinimum() {
        assertFalse(VersionUtils.isVersionAtLeast("4.9.999", 5, 4, 150),
                "4.9.999 : major inférieur — doit être refusé");
    }

    // ── Au-dessus de la limite ────────────────────────────────────────────────

    @Test
    void shouldReturnTrue_whenPatchIsAboveMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("5.4.151", 5, 4, 150),
                "5.4.151 : un patch au-dessus — doit être accepté");
    }

    @Test
    void shouldReturnTrue_whenMinorIsAboveMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("5.5.0", 5, 4, 150),
                "5.5.0 : minor supérieur — patch 0 n'est pas pertinent");
    }

    @Test
    void shouldReturnTrue_whenMajorIsAboveMinimum() {
        assertTrue(VersionUtils.isVersionAtLeast("6.0.0", 5, 4, 150),
                "6.0.0 : major supérieur — doit être accepté");
    }

    // ── Patch absent ──────────────────────────────────────────────────────────

    @Test
    void shouldReturnFalse_whenPatchIsAbsent() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4", 5, 4, 150),
                "\"5.4\" : patch absent → traité comme 0, donc 0 < 150 → false");
    }

    // ── Versions malformées → false (sécurité) ────────────────────────────────

    @Test
    void shouldReturnFalse_whenVersionHasSnapshotTag() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4-SNAPSHOT", 5, 4, 150),
                "\"5.4-SNAPSHOT\" : split sur [.\\-] produit [5, 4, SNAPSHOT] → NumberFormatException → false");
    }

    @Test
    void shouldReturnFalse_whenPatchVersionHasSnapshotTag() {
        assertFalse(VersionUtils.isVersionAtLeast("5.4.150-SNAPSHOT", 5, 4, 150),
                "\"5.4.150-SNAPSHOT\" doit être refusé pour rester cohérent avec les autres prereleases");
    }

    @Test
    void shouldReturnFalse_whenVersionIsNonNumeric() {
        assertFalse(VersionUtils.isVersionAtLeast("invalid", 5, 4, 150),
                "\"invalid\" : NumberFormatException lors du parse → false");
    }

    @Test
    void shouldReturnFalse_whenVersionIsEmpty() {
        assertFalse(VersionUtils.isVersionAtLeast("", 5, 4, 150),
                "Version vide : guard en tête → false");
    }

    @Test
    void shouldReturnFalse_whenVersionIsNull() {
        assertFalse(VersionUtils.isVersionAtLeast(null, 5, 4, 150),
                "Version null : guard en tête → false");
    }
}
