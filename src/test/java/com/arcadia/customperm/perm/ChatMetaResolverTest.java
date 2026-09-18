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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chat prefixes and suffixes, resolved by the ranking permissions use with the specificity step dropped:
 * the player's own, then the heaviest grade, then the nearest ancestor, refusals and the default grade
 * applying as they do to a node. Pure Java.
 */
class ChatMetaResolverTest {

    private GradesConfig grades;
    private UUID player;

    @BeforeEach
    void setUp() {
        grades = new GradesConfig();
        player = UUID.randomUUID();
    }

    @Test
    void noPrefixAnywhereIsNull() {
        grade("member", 0, null);
        assign("member");
        assertNull(PermissionResolver.prefix(grades, player, null));
    }

    @Test
    void theHeaviestGradeDecides() {
        grade("member", 0, "[M]");
        grade("vip", 10, "[VIP]");
        assign("member", "vip");
        assertEquals("[VIP]", PermissionResolver.prefix(grades, player, null));
    }

    @Test
    void theOrderGradesWereAssignedInNeverDecides() {
        grade("a", 5, "[B]");
        grade("b", 5, "[A]");
        assign("a", "b");
        String first = PermissionResolver.prefix(grades, player, null);
        grades.userGrades.put(player.toString(), new ArrayList<>(List.of("b", "a")));
        assertEquals(first, PermissionResolver.prefix(grades, player, null));
        assertEquals("[A]", first, "between equal weights, the text that sorts first");
    }

    @Test
    void thePlayersOwnPrefixWinsOverEveryGrade() {
        grade("vip", Integer.MAX_VALUE, "[VIP]");
        assign("vip");
        grades.userPrefixes.put(player.toString(), "[Me]");
        assertEquals("[Me]", PermissionResolver.prefix(grades, player, null));
    }

    @Test
    void aGradeOverridesWhatItInheritsAndTheNearestAncestorWins() {
        grade("base", 0, "[Base]");
        grade("middle", 0, "[Middle]");
        grade("top", 0, null);
        grades.grades.get("middle").parents.add("base");
        grades.grades.get("top").parents.add("middle");
        assign("top");
        assertEquals("[Middle]", PermissionResolver.prefix(grades, player, null));

        grades.grades.get("top").prefix = "[Top]";
        assertEquals("[Top]", PermissionResolver.prefix(grades, player, null));
    }

    @Test
    void anInheritedPrefixCompetesAtTheWeightOfTheGradeHeld() {
        grade("heavyParent", 100, "[Parent]");
        grade("light", 1, null);
        grade("other", 5, "[Other]");
        grades.grades.get("light").parents.add("heavyParent");
        assign("light", "other");
        assertEquals("[Other]", PermissionResolver.prefix(grades, player, null),
                "a heavy parent does not smuggle its weight into a light grade");
    }

    @Test
    void aRefusedGradeGivesNoPrefix() {
        grade("vip", 10, "[VIP]");
        grade("member", 0, "[M]");
        assign("vip", "member");
        grades.userDeniedGrades.put(player.toString(), new ArrayList<>(List.of("vip")));
        assertEquals("[M]", PermissionResolver.prefix(grades, player, null));
    }

    @Test
    void theDefaultGradeAppliesOnlyWhenNothingHeldHasOne() {
        grade("everyone", 1000, "[Guest]");
        grade("vip", 0, "[VIP]");
        assertEquals("[Guest]", PermissionResolver.prefix(grades, player, "everyone"));
        assign("vip");
        assertEquals("[VIP]", PermissionResolver.prefix(grades, player, "everyone"),
                "below the player's own grades, whatever it weighs");
    }

    @Test
    void theSuffixIsResolvedApartFromThePrefix() {
        grade("vip", 10, "[VIP]");
        grade("member", 0, null);
        grades.grades.get("member").suffix = " *";
        assign("vip", "member");
        assertEquals("[VIP]", PermissionResolver.prefix(grades, player, null));
        assertEquals(" *", PermissionResolver.suffix(grades, player, null),
                "the heavier grade has no suffix, so it does not hide the lighter one's");
    }

    @Test
    void permissionsStillRankAsBefore() {
        grade("a", 0, null);
        grades.grades.get("a").deniedPermissions.add("x");
        grade("b", 0, null);
        grades.grades.get("b").permissions.add("x");
        assign("a", "b");
        assertEquals(Tristate.DENY, PermissionResolver.check(grades, player, "x", null),
                "a DENY still wins between equal ranks");
    }

    private void grade(String name, int weight, String prefix) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grade.weight = weight;
        grade.prefix = prefix;
        grades.grades.put(name, grade);
    }

    private void assign(String... names) {
        grades.userGrades.put(player.toString(), new ArrayList<>(List.of(names)));
    }
}
