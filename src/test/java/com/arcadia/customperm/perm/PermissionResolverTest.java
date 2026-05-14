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
 * Couvre INVARIANT-101 (DENY > ALLOW), INVARIANT-102 (union cumulative),
 * FR2 (pas de grade = refus), wildcards, et cas limites.
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

    // ─── DENY > ALLOW (INVARIANT-101) ─────────────────────────────────────────

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
