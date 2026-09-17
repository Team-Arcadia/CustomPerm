/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.perm;

import com.arcadia.customperm.config.GradesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour PermissionResolver — pur Java, zéro import Minecraft/NeoForge (AR8).
 *
 * Covers most-specific-wins, INVARIANT-101 (DENY wins at the same specificity), INVARIANT-102
 * (cumulative union), FR2 (no grade = nothing granted), the default grade layer, wildcards and edge cases.
 */
class PermissionResolverTest {

    private GradesConfig grades;
    private UUID player;

    @BeforeEach
    void setUp() {
        grades = new GradesConfig();
        player = UUID.randomUUID();
    }

    // ─── Cas limites ───────────────────────────────────────────────────────────

    @Test
    void shouldReturnFalse_whenNodeIsNull() {
        assignGradeWithAllow("staff", "tp");
        assertFalse(PermissionResolver.resolve(grades, player, null));
    }

    @Test
    void shouldReturnFalse_whenNoGradeAssigned() {
        // Joueur sans grade → FR2
        createGrade("staff", Set.of("tp"), Set.of());
        // Ne pas assigner le grade au joueur
        assertFalse(PermissionResolver.resolve(grades, player, "tp"));
    }

    @Test
    void shouldReturnFalse_whenAssignedGradeDoesNotExist() {
        // Grade assigné mais supprimé de la map
        grades.userGrades.put(player.toString(), java.util.List.of("ghost"));
        assertFalse(PermissionResolver.resolve(grades, player, "tp"));
    }

    // ─── Résolution ALLOW ──────────────────────────────────────────────────────

    @Test
    void shouldReturnTrue_whenGradeHasDirectAllowNode() {
        assignGradeWithAllow("staff", "tp");
        assertTrue(PermissionResolver.resolve(grades, player, "tp"));
        assertFalse(PermissionResolver.resolve(grades, player, "ban"));
    }

    @Test
    void shouldReturnTrue_whenGradeHasGlobalWildcard() {
        assignGradeWithAllow("admin", "*");
        assertTrue(PermissionResolver.resolve(grades, player, "tp"));
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.command.gamemode"));
    }

    @Test
    void shouldReturnTrue_whenGradeHasPrefixWildcard() {
        assignGradeWithAllow("mod", "customperm.command.*");
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.command.gamemode"));
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.command.tp"));
        // Hors préfixe → false
        assertFalse(PermissionResolver.resolve(grades, player, "customperm.alias.heal"));
    }

    @Test
    void shouldReturnTrue_whenWildcardIsOnAnyAncestor() {
        assignGradeWithAllow("staff", "customperm.*");
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.command.gamemode"));
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.alias.heal"));
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.gui.luckperms.edit"));
        assertFalse(PermissionResolver.resolve(grades, player, "customperm"),
            "prefix.* covers descendants only, not the prefix itself");
        assertFalse(PermissionResolver.resolve(grades, player, "custompermx.command.tp"));
    }

    @Test
    void exactAllowBeatsAncestorDeny() {
        createGrade("staff", Set.of("customperm.command.gamemode"), Set.of("customperm.*"));
        grades.userGrades.put(player.toString(), java.util.List.of("staff"));
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.command.gamemode"),
            "the exact node is more specific than customperm.*");
        assertEquals(Tristate.DENY, check("customperm.command.tp"), "the ancestor DENY still covers the rest");
    }

    @Test
    void deniedStarBlocksEverythingExceptExplicitAllows() {
        createGrade("locked", Set.of("customperm.command.home"), Set.of("*"));
        grades.userGrades.put(player.toString(), java.util.List.of("locked"));
        assertEquals(Tristate.ALLOW, check("customperm.command.home"));
        assertEquals(Tristate.DENY, check("customperm.command.stop"));
        assertEquals(Tristate.DENY, check("customperm.admin"));
    }

    @Test
    void allowedStarGrantsEverythingExceptExplicitDenies() {
        createGrade("owner", Set.of("*"), Set.of("customperm.command.stop"));
        grades.userGrades.put(player.toString(), java.util.List.of("owner"));
        assertEquals(Tristate.ALLOW, check("customperm.command.op"));
        assertEquals(Tristate.DENY, check("customperm.command.stop"));
    }

    @Test
    void deeperWildcardBeatsShallowerOne() {
        createGrade("mixed", Set.of("customperm.command.*"), Set.of("customperm.*"));
        grades.userGrades.put(player.toString(), java.util.List.of("mixed"));
        assertEquals(Tristate.ALLOW, check("customperm.command.gamemode"));
        assertEquals(Tristate.DENY, check("customperm.alias.heal"));
    }

    @Test
    void specificityComparesAcrossGrades() {
        createGrade("base", Set.of(), Set.of("*"));
        createGrade("builder", Set.of("customperm.command.gamemode"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("base", "builder"));
        assertEquals(Tristate.ALLOW, check("customperm.command.gamemode"));
        assertEquals(Tristate.DENY, check("customperm.command.give"));
    }

    @Test
    void nothingMatchingIsUnset() {
        assignGradeWithAllow("staff", "customperm.command.tp");
        assertEquals(Tristate.UNSET, check("customperm.command.give"));
        assertEquals(Tristate.UNSET, PermissionResolver.check(grades, UUID.randomUUID(), "customperm.command.tp", null));
    }

    // ─── Default grade ────────────────────────────────────────────────────────

    @Test
    void defaultGradeAppliesToPlayersWithoutGrades() {
        createGrade("everyone", Set.of("customperm.command.spawn"), Set.of("*"));
        UUID stranger = UUID.randomUUID();
        assertEquals(Tristate.DENY, PermissionResolver.check(grades, stranger, "customperm.command.op", "everyone"));
        assertEquals(Tristate.ALLOW, PermissionResolver.check(grades, stranger, "customperm.command.spawn", "everyone"));
        assertEquals(Tristate.UNSET, PermissionResolver.check(grades, stranger, "customperm.command.op", ""),
            "no default grade configured");
        assertEquals(Tristate.UNSET, PermissionResolver.check(grades, stranger, "customperm.command.op", "missing"),
            "a default grade that does not exist grants and denies nothing");
    }

    @Test
    void ownGradesDecideBeforeTheDefaultGrade() {
        createGrade("everyone", Set.of(), Set.of("*"));
        assignGradeWithAllow("staff", "customperm.command.*");
        assertEquals(Tristate.ALLOW, PermissionResolver.check(grades, player, "customperm.command.gamemode", "everyone"),
            "the player's own grade answers first, even with a less specific node than the default would need");
        assertEquals(Tristate.DENY, PermissionResolver.check(grades, player, "customperm.admin", "everyone"),
            "nodes the own grades do not mention fall through to the default grade");
    }

    @Test
    void explicitlyAssignedDefaultGradeStaysInTheDefaultLayer() {
        createGrade("everyone", Set.of(), Set.of("*"));
        createGrade("staff", Set.of("customperm.command.tp"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("everyone", "staff"));
        assertEquals(Tristate.ALLOW, PermissionResolver.check(grades, player, "customperm.command.tp", "everyone"));
    }

    @Test
    void specificityValues() {
        assertEquals(Integer.MAX_VALUE, PermissionResolver.specificity(Set.of("a.b.c"), "a.b.c"));
        assertEquals(2, PermissionResolver.specificity(Set.of("a.b.*"), "a.b.c"));
        assertEquals(1, PermissionResolver.specificity(Set.of("a.*"), "a.b.c"));
        assertEquals(0, PermissionResolver.specificity(Set.of("*"), "a.b.c"));
        assertEquals(-1, PermissionResolver.specificity(Set.of("a.b.*"), "a.b"));
        assertEquals(2, PermissionResolver.specificity(Set.of("a.*", "a.b.*", "*"), "a.b.c"), "the deepest wildcard counts");
    }

    @Test
    void shouldReturnFalse_whenUserGradeListIsNull() {
        createGrade("staff", Set.of("tp"), Set.of());
        grades.userGrades.put(player.toString(), null);
        assertFalse(PermissionResolver.resolve(grades, player, "tp"));
    }

    // ─── Cumul multi-grades (INVARIANT-102) ───────────────────────────────────

    @Test
    void shouldCumulatePermissions_acrossMultipleGrades() {
        // Grade A : ALLOW cmd.foo | Grade B : ALLOW cmd.bar → les deux accordés (INVARIANT-102, FR3)
        GradesConfig.Grade gradeA = createGrade("gradeA", Set.of("cmd.foo"), Set.of());
        GradesConfig.Grade gradeB = createGrade("gradeB", Set.of("cmd.bar"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("gradeA", "gradeB"));

        assertTrue(PermissionResolver.resolve(grades, player, "cmd.foo"),  "cmd.foo via gradeA");
        assertTrue(PermissionResolver.resolve(grades, player, "cmd.bar"),  "cmd.bar via gradeB");
        assertFalse(PermissionResolver.resolve(grades, player, "cmd.baz"), "non accordé");
    }

    // ─── DENY wins at the same specificity (INVARIANT-101) ─────────────────────

    @Test
    void shouldDenyOverrideAllow_whenGradeAAllowsAndGradeBDenies() {
        // Grade A : ALLOW cmd.foo | Grade B : DENY cmd.foo → false (INVARIANT-101, NFR7)
        createGrade("gradeA", Set.of("cmd.foo"), Set.of());
        createGrade("gradeB", Set.of(), Set.of("cmd.foo"));
        // Ordre : A d'abord, B ensuite
        grades.userGrades.put(player.toString(), java.util.List.of("gradeA", "gradeB"));

        assertFalse(PermissionResolver.resolve(grades, player, "cmd.foo"),
            "DENY dans gradeB doit l'emporter sur ALLOW dans gradeA");
    }

    @Test
    void shouldDenyOverrideAllow_whenGradeBAllowsAndGradeADenies() {
        // Même invariant, ordre inversé — vérifie que l'ordre d'itération ne change pas le résultat
        createGrade("gradeA", Set.of(), Set.of("cmd.foo"));  // DENY
        createGrade("gradeB", Set.of("cmd.foo"), Set.of());  // ALLOW
        // Ordre : B d'abord (ALLOW), A ensuite (DENY)
        grades.userGrades.put(player.toString(), java.util.List.of("gradeB", "gradeA"));

        assertFalse(PermissionResolver.resolve(grades, player, "cmd.foo"),
            "DENY dans gradeA doit l'emporter même si gradeB est itéré en premier");
    }

    @Test
    void shouldReturnFalse_whenNodeExplicitlyDenied_noAllow() {
        createGrade("staff", Set.of(), Set.of("ban"));
        grades.userGrades.put(player.toString(), java.util.List.of("staff"));

        assertFalse(PermissionResolver.resolve(grades, player, "ban"));
    }

    // ─── Wildcard dans DENY ────────────────────────────────────────────────────

    @Test
    void shouldApplyWildcardToDenied_whenPrefixWildcardInDenied() {
        // Wildcard DENY : interdit tout customperm.command.*
        createGrade("restricted", Set.of("customperm.command.*"), Set.of("customperm.command.*"));
        // DENY sur wildcard l'emporte sur le ALLOW wildcard
        grades.userGrades.put(player.toString(), java.util.List.of("restricted"));

        assertFalse(PermissionResolver.resolve(grades, player, "customperm.command.gamemode"),
            "Wildcard DENY doit couvrir les nœuds descendants");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Tristate check(String node) {
        return PermissionResolver.check(grades, player, node, null);
    }

    private void assignGradeWithAllow(String gradeName, String allowNode) {
        createGrade(gradeName, Set.of(allowNode), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of(gradeName));
    }

    private GradesConfig.Grade createGrade(String name, Set<String> allow, Set<String> deny) {
        GradesConfig.Grade g = new GradesConfig.Grade();
        g.name = name;
        g.permissions = new java.util.HashSet<>(allow);
        g.deniedPermissions = new java.util.HashSet<>(deny);
        grades.grades.put(name, g);
        return g;
    }
}
