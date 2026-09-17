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
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.manage.luckperms"));
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

    // ─── Poids de grade (départage à spécificité égale) ───────────────────────

    @Test
    void heaviestGradeWins_whenTwoGradesTieOnSpecificity() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of()).weight = 10;
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp"));
        grades.userGrades.put(player.toString(), java.util.List.of("vip", "restricted"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"),
            "the heavier grade decides when both cover the node exactly");
    }

    @Test
    void heaviestGradeWins_whateverTheAssignmentOrder() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of()).weight = 10;
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp"));
        grades.userGrades.put(player.toString(), java.util.List.of("restricted", "vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"),
            "the order grades were assigned in carries no meaning");
    }

    @Test
    void heaviestGradeWins_whenItIsTheDenyingOne() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of());
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp")).weight = 3;
        grades.userGrades.put(player.toString(), java.util.List.of("vip", "restricted"));
        assertEquals(Tristate.DENY, check("customperm.command.tp"));
    }

    @Test
    void denyWins_whenWeightsAreEqual() {
        // INVARIANT-101 survives the weight: every grade at 0 is what an existing file deserializes to.
        createGrade("vip", Set.of("customperm.command.tp"), Set.of());
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp"));
        grades.userGrades.put(player.toString(), java.util.List.of("vip", "restricted"));
        assertEquals(Tristate.DENY, check("customperm.command.tp"));

        createGrade("vip2", Set.of("customperm.command.fly"), Set.of()).weight = 7;
        createGrade("restricted2", Set.of(), Set.of("customperm.command.fly")).weight = 7;
        grades.userGrades.put(player.toString(), java.util.List.of("vip2", "restricted2"));
        assertEquals(Tristate.DENY, check("customperm.command.fly"), "equal weights fall back to DENY");
    }

    @Test
    void weightNeverBeatsSpecificity() {
        // A heavy grade denying everything must not swallow an exact node allowed by a light one,
        // otherwise a weight would become a way around the most-specific-wins rule.
        createGrade("locked", Set.of(), Set.of("*")).weight = 1000;
        createGrade("vip", Set.of("customperm.command.tp"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("locked", "vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
        assertEquals(Tristate.DENY, check("customperm.command.op"), "everything else stays denied");
    }

    @Test
    void negativeWeightRanksBelowTheDefaultZero() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of()).weight = -5;
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp"));
        grades.userGrades.put(player.toString(), java.util.List.of("vip", "restricted"));
        assertEquals(Tristate.DENY, check("customperm.command.tp"));
    }

    @Test
    void weightDoesNotCrossTheDefaultGradeLayer() {
        // The default grade is read only when the player's own grades say nothing about the node:
        // weighing it heavily must not promote it above them.
        createGrade("everyone", Set.of(), Set.of("customperm.command.tp")).weight = 1000;
        createGrade("staff", Set.of("customperm.command.tp"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("staff"));
        assertEquals(Tristate.ALLOW, PermissionResolver.check(grades, player, "customperm.command.tp", "everyone"));
    }

    // ─── Nœuds portés par le joueur ───────────────────────────────────────────

    @Test
    void ownNodeWinsOverAGradeAtTheSameLevel() {
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp")).weight = 1000;
        grades.userGrades.put(player.toString(), java.util.List.of("restricted"));
        allowOwn("customperm.command.tp");
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"),
            "a node on the player outranks any grade, whatever it weighs");
    }

    @Test
    void ownDenyWinsOverAGradeAllow() {
        createGrade("staff", Set.of("customperm.command.tp"), Set.of()).weight = 50;
        grades.userGrades.put(player.toString(), java.util.List.of("staff"));
        denyOwn("customperm.command.tp");
        assertEquals(Tristate.DENY, check("customperm.command.tp"));
    }

    @Test
    void ownNodeNeverBeatsAMoreSpecificGradeNode() {
        // The rank of the holder only breaks a tie: the most specific entry still decides first.
        createGrade("staff", Set.of("customperm.command.tp"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("staff"));
        denyOwn("*");
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
        assertEquals(Tristate.DENY, check("customperm.command.op"), "the own wildcard still closes the rest");
    }

    @Test
    void ownDenyWinsOverAnOwnAllowAtTheSameLevel() {
        allowOwn("customperm.command.tp");
        denyOwn("customperm.command.tp");
        assertEquals(Tristate.DENY, check("customperm.command.tp"));
    }

    @Test
    void ownNodeAloneGrantsWithoutAnyGrade() {
        allowOwn("customperm.command.tp");
        assertTrue(PermissionResolver.resolve(grades, player, "customperm.command.tp"),
            "a player needs no grade to carry a node");
        assertFalse(PermissionResolver.resolve(grades, player, "customperm.command.ban"));
    }

    @Test
    void ownNodeIsReadBeforeTheDefaultGrade() {
        createGrade("everyone", Set.of(), Set.of("*"));
        allowOwn("customperm.command.tp");
        assertEquals(Tristate.ALLOW, PermissionResolver.check(grades, player, "customperm.command.tp", "everyone"));
        assertEquals(Tristate.DENY, PermissionResolver.check(grades, player, "customperm.admin", "everyone"),
            "what the player carries says nothing about this node, so the default grade decides");
    }

    @Test
    void anotherPlayersNodesDoNotLeak() {
        allowOwn("customperm.command.tp");
        assertEquals(Tristate.UNSET, PermissionResolver.check(grades, UUID.randomUUID(), "customperm.command.tp", null));
    }

    // ─── Héritage entre grades ────────────────────────────────────────────────

    @Test
    void aGradeInheritsWhatItsParentAllows() {
        createGrade("base", Set.of("customperm.command.tp"), Set.of());
        inherit("vip", "base");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
        assertEquals(Tristate.UNSET, check("customperm.command.ban"));
    }

    @Test
    void aGradeOverridesWhatItInheritsAtTheSameLevel() {
        createGrade("base", Set.of(), Set.of("customperm.command.tp"));
        createGrade("vip", Set.of("customperm.command.tp"), Set.of()).parents.add("base");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"), "the child decides over its parent");
    }

    @Test
    void aMoreSpecificParentEntryStillBeatsTheChild() {
        createGrade("base", Set.of("customperm.command.tp"), Set.of());
        createGrade("vip", Set.of(), Set.of("*")).parents.add("base");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"),
            "inheritance does not change which entry is the most specific");
        assertEquals(Tristate.DENY, check("customperm.command.ban"));
    }

    @Test
    void theNearestAncestorWinsOnATie() {
        createGrade("root", Set.of("customperm.command.tp"), Set.of());
        createGrade("middle", Set.of(), Set.of("customperm.command.tp")).parents.add("root");
        inherit("vip", "middle");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.DENY, check("customperm.command.tp"), "the closer parent answers first");
    }

    @Test
    void twoParentsAtTheSameDistanceFallBackToDeny() {
        createGrade("left", Set.of("customperm.command.tp"), Set.of());
        createGrade("right", Set.of(), Set.of("customperm.command.tp"));
        createGrade("vip", Set.of(), Set.of()).parents.addAll(java.util.List.of("left", "right"));
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.DENY, check("customperm.command.tp"));
    }

    @Test
    void aCycleResolvesInsteadOfLooping() {
        createGrade("a", Set.of("customperm.command.tp"), Set.of()).parents.add("b");
        createGrade("b", Set.of("customperm.command.fly"), Set.of()).parents.add("a");
        grades.userGrades.put(player.toString(), java.util.List.of("a"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
        assertEquals(Tristate.ALLOW, check("customperm.command.fly"), "the cycle is walked once, not refused");
        assertEquals(Tristate.UNSET, check("customperm.command.ban"));
    }

    @Test
    void anUnknownParentIsIgnored() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of()).parents.add("deleted");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
    }

    @Test
    void aChainCompetesAtTheWeightOfTheGradeHeld() {
        // The parent weighs a lot, but the player holds "vip": what a parent says arrives at vip's weight.
        createGrade("heavy", Set.of("customperm.command.tp"), Set.of()).weight = 1000;
        createGrade("vip", Set.of(), Set.of()).parents.add("heavy");
        createGrade("restricted", Set.of(), Set.of("customperm.command.tp")).weight = 5;
        grades.userGrades.put(player.toString(), java.util.List.of("vip", "restricted"));
        assertEquals(Tristate.DENY, check("customperm.command.tp"));
    }

    @Test
    void anOwnNodeStillOutranksAnInheritedOne() {
        createGrade("base", Set.of(), Set.of("customperm.command.tp"));
        inherit("vip", "base");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        allowOwn("customperm.command.tp");
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
    }

    @Test
    void normalizeDropsSelfInheritanceAndDuplicates() {
        GradesConfig.Grade vip = createGrade("vip", Set.of("customperm.command.tp"), Set.of());
        vip.parents.addAll(java.util.Arrays.asList("vip", "base", "base", null));
        grades.normalize();
        assertEquals(java.util.List.of("base"), vip.parents);
    }

    // ─── Héritage refusé ──────────────────────────────────────────────────────

    @Test
    void aGradeThePlayerRefusesGrantsNothing() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        refuse("vip");
        assertEquals(Tristate.UNSET, check("customperm.command.tp"),
            "a refusal takes the grade out, it does not turn its ALLOW into a DENY");
    }

    @Test
    void aRefusedGradeIsNotReachedThroughAChainEither() {
        createGrade("base", Set.of("customperm.command.tp"), Set.of());
        createGrade("mid", Set.of("customperm.command.fly"), Set.of()).parents.add("base");
        inherit("vip", "mid");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        refuse("base");
        assertEquals(Tristate.UNSET, check("customperm.command.tp"), "the refused ancestor is not walked");
        assertEquals(Tristate.ALLOW, check("customperm.command.fly"), "the rest of the chain still applies");
    }

    @Test
    void aPlayerCanRefuseTheDefaultGrade() {
        createGrade("everyone", Set.of(), Set.of("*"));
        assertEquals(Tristate.DENY, PermissionResolver.check(grades, player, "customperm.command.tp", "everyone"));
        refuse("everyone");
        assertEquals(Tristate.UNSET, PermissionResolver.check(grades, player, "customperm.command.tp", "everyone"),
            "the default grade applies to every player except the ones who refuse it");
    }

    @Test
    void aGradeCanRefuseWhatItsParentInherits() {
        createGrade("root", Set.of("customperm.command.tp"), Set.of());
        createGrade("middle", Set.of("customperm.command.fly"), Set.of()).parents.add("root");
        GradesConfig.Grade vip = createGrade("vip", Set.of(), Set.of());
        vip.parents.add("middle");
        vip.deniedParents.add("root");
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.UNSET, check("customperm.command.tp"));
        assertEquals(Tristate.ALLOW, check("customperm.command.fly"));
    }

    @Test
    void aRefusalDeclaredFartherDoesNotCloseANearerInheritance() {
        // vip inherits both middle and root; middle refuses root. The refusal is read where it is
        // declared, so it cannot undo what vip itself asked for.
        createGrade("root", Set.of("customperm.command.tp"), Set.of());
        createGrade("middle", Set.of(), Set.of()).deniedParents.add("root");
        GradesConfig.Grade vip = createGrade("vip", Set.of(), Set.of());
        vip.parents.addAll(java.util.List.of("middle", "root"));
        grades.userGrades.put(player.toString(), java.util.List.of("vip"));
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"));
    }

    @Test
    void refusingOneGradeLeavesTheOthersAlone() {
        createGrade("vip", Set.of("customperm.command.tp"), Set.of());
        createGrade("staff", Set.of("customperm.command.tp", "customperm.command.fly"), Set.of());
        grades.userGrades.put(player.toString(), java.util.List.of("vip", "staff"));
        refuse("vip");
        assertEquals(Tristate.ALLOW, check("customperm.command.tp"), "staff still answers");
        assertEquals(Tristate.ALLOW, check("customperm.command.fly"));
    }

    @Test
    void normalizeCleansRefusals() {
        GradesConfig.Grade vip = createGrade("vip", Set.of(), Set.of());
        vip.deniedParents.addAll(java.util.Arrays.asList("vip", "base", "base", null));
        grades.userDeniedGrades.put(player.toString(), new java.util.ArrayList<>(java.util.Arrays.asList("a", (String) null)));
        grades.userDeniedGrades.put("other", new java.util.ArrayList<>());
        grades.normalize();
        assertEquals(java.util.List.of("base"), vip.deniedParents);
        assertEquals(java.util.List.of("a"), grades.userDeniedGrades.get(player.toString()));
        assertNull(grades.userDeniedGrades.get("other"), "an empty refusal list is dropped");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Tristate check(String node) {
        return PermissionResolver.check(grades, player, node, null);
    }

    /** The player refuses {@code gradeName}, wherever it would otherwise be reached. */
    private void refuse(String gradeName) {
        grades.userDeniedGrades.computeIfAbsent(player.toString(), k -> new java.util.ArrayList<>()).add(gradeName);
    }

    /** An empty grade inheriting from {@code parent}, so a test reads as what it is about. */
    private void inherit(String name, String parent) {
        createGrade(name, Set.of(), Set.of()).parents.add(parent);
    }

    private void allowOwn(String node) {
        grades.userPermissions.computeIfAbsent(player.toString(), k -> new java.util.HashSet<>()).add(node);
    }

    private void denyOwn(String node) {
        grades.userDeniedPermissions.computeIfAbsent(player.toString(), k -> new java.util.HashSet<>()).add(node);
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
