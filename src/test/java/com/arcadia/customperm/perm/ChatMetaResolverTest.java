/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.perm;

import com.arcadia.customperm.chat.ChatStack;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.SettingsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chat prefixes and suffixes: the highest priority first, then at equal priority the player's own, the
 * heaviest grade and the nearest ancestor, refusals, expiries and the default grade applying as they do to a
 * node; and how several are shown. Pure Java.
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
        own("[Me]", 0);
        assertEquals("[Me]", PermissionResolver.prefix(grades, player, null), "at equal priority");
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

        grades.grades.get("top").prefixes.add(new GradesConfig.ChatEntry(0, "[Top]", 0));
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
        grades.grades.get("member").suffixes.add(new GradesConfig.ChatEntry(0, " *", 0));
        assign("vip", "member");
        assertEquals("[VIP]", PermissionResolver.prefix(grades, player, null));
        assertEquals(" *", PermissionResolver.suffix(grades, player, null),
                "the heavier grade has no suffix, so it does not hide the lighter one's");
    }

    @Test
    void theHighestPriorityShowsWhateverItsHolder() {
        grade("member", 0, null);
        grades.grades.get("member").prefixes.add(new GradesConfig.ChatEntry(50, "[Member]", 0));
        grade("vip", 100, "[VIP]");
        assign("member", "vip");
        own("[Me]", 10);
        assertEquals("[Member]", PermissionResolver.prefix(grades, player, null),
                "like LuckPerms, priority comes before the holder: the lightest grade and the player's own lose to it");
        assertEquals(List.of("[Member]", "[Me]", "[VIP]"), PermissionResolver.prefixes(grades, player, null));
    }

    @Test
    void aParentsHigherPriorityShowsThroughItsChild() {
        grade("base", 0, null);
        grades.grades.get("base").prefixes.add(new GradesConfig.ChatEntry(20, "[Base]", 0));
        grade("vip", 0, "[VIP]");
        grades.grades.get("vip").parents.add("base");
        assign("vip");
        assertEquals(List.of("[Base]", "[VIP]"), PermissionResolver.prefixes(grades, player, null));
    }

    @Test
    void theSameTextReachedTwiceShowsOnce() {
        grade("a", 1, "[Staff]");
        grade("b", 0, "[Staff]");
        assign("a", "b");
        assertEquals(List.of("[Staff]"), PermissionResolver.prefixes(grades, player, null));
    }

    @Test
    void anExpiredPrefixIsNotShownAndTheNextOneIs() {
        grade("vip", 0, "[VIP]");
        grades.grades.get("vip").prefixes.add(new GradesConfig.ChatEntry(10, "[Event]", Expiry.now() - 1));
        assign("vip");
        assertEquals("[VIP]", PermissionResolver.prefix(grades, player, null));
        grades.grades.get("vip").prefixes.get(1).expires = Expiry.now() + 60;
        assertEquals("[Event]", PermissionResolver.prefix(grades, player, null), "until it runs out, it shows");
    }

    @Test
    void aFileWrittenBeforePrioritiesReadsTheSame() {
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.name = "vip";
        vip.prefix = "[VIP]";
        vip.suffix = " *";
        grades.grades.put("vip", vip);
        grades.userPrefixes = new java.util.HashMap<>(java.util.Map.of(player.toString(), "[Me]"));
        grades.normalize();
        assertNull(vip.prefix, "the legacy field is moved, not kept beside the entries");
        assertEquals(0, vip.prefixes.get(0).priority);
        assertEquals(" *", vip.suffixes.get(0).text);
        assertNull(grades.userPrefixes);
        assign("vip");
        assertEquals("[Me]", PermissionResolver.prefix(grades, player, null),
                "at priority 0 on both, the player's own still shows first, as it did");
    }

    @Test
    void twoEntriesAtOnePriorityKeepTheFirst() {
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.name = "vip";
        vip.prefixes.add(new GradesConfig.ChatEntry(5, "[A]", 0));
        vip.prefixes.add(new GradesConfig.ChatEntry(5, "[B]", 0));
        vip.prefixes.add(new GradesConfig.ChatEntry(9, "", 0));
        grades.grades.put("vip", vip);
        grades.normalize();
        assertEquals(1, vip.prefixes.size(), "a priority names one entry, and an empty text is none");
        assertEquals("[A]", vip.prefixes.get(0).text);
    }

    @Test
    void aStackShowsSeveralBetweenItsSpacers() {
        SettingsConfig.ChatStack stack = new SettingsConfig.ChatStack();
        List<String> ordered = List.of("[A]", "[B]", "[C]", "[D]");
        assertEquals("[A]", ChatStack.format(ordered, stack), "highest only by default");
        stack.mode = SettingsConfig.ChatStack.STACKED;
        stack.limit = 3;
        stack.start = "<";
        stack.middle = "|";
        stack.end = "> ";
        assertEquals("<[A]|[B]|[C]> ", ChatStack.format(ordered, stack));
        assertNull(ChatStack.format(List.of(), stack), "nothing to show is no prefix, not the spacers alone");
    }

    @Test
    void aStackSettingThatMakesNoSenseFallsBack() {
        SettingsConfig settings = new SettingsConfig();
        settings.prefixStack.mode = "sideways";
        settings.prefixStack.limit = 999;
        settings.suffixStack = null;
        settings.normalize();
        assertFalse(settings.prefixStack.stacked());
        assertEquals(3, settings.prefixStack.limit);
        assertNotNull(settings.suffixStack);
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
        if (prefix != null) grade.prefixes.add(new GradesConfig.ChatEntry(0, prefix, 0));
        grades.grades.put(name, grade);
    }

    private void own(String prefix, int priority) {
        grades.userPrefixEntries.computeIfAbsent(player.toString(), k -> new ArrayList<>())
                .add(new GradesConfig.ChatEntry(priority, prefix, 0));
    }

    private void assign(String... names) {
        grades.userGrades.put(player.toString(), new ArrayList<>(List.of(names)));
    }
}
