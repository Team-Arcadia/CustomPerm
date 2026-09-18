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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Temporary entries: durations as admins write them, and the resolver ignoring what has expired, so an
 * expiry is right even before anything sweeps it. Pure Java.
 */
class ExpiryTest {

    private GradesConfig grades;
    private UUID player;
    private String user;

    @BeforeEach
    void setUp() {
        grades = new GradesConfig();
        player = UUID.randomUUID();
        user = player.toString();
    }

    // --- durations ---

    @Test
    void durationsAreReadInEveryUnitAndCombination() {
        assertEquals(30L * 86400, Expiry.parse("30d"));
        assertEquals(2L * 3600, Expiry.parse("2h"));
        assertEquals(86400 + 12 * 3600, Expiry.parse("1d12h"));
        assertEquals(7L * 86400, Expiry.parse("1w"));
        assertEquals(90L * 60, Expiry.parse("90M"), "case does not matter");
        assertEquals(45, Expiry.parse("45s"));
    }

    @Test
    void anythingElseIsRefused() {
        for (String bad : new String[]{"", "30", "d", "0d", "-1d", "1y", "1d 2h", "abc", "11000d", "99999999999999999999d"}) {
            assertEquals(-1, Expiry.parse(bad), bad);
        }
        assertEquals(-1, Expiry.parse(null));
    }

    @Test
    void aRemainingTimeReadsInItsTwoLargestUnits() {
        assertEquals("29d 23h", Expiry.describe(29L * 86400 + 23 * 3600 + 59));
        assertEquals("2h 5m", Expiry.describe(2 * 3600 + 5 * 60 + 3));
        assertEquals("3d", Expiry.describe(3L * 86400 + 30));
        assertEquals("40s", Expiry.describe(40));
        assertEquals("expired", Expiry.describe(0));
    }

    // --- resolution ---

    @Test
    void anExpiredNodeOfAGradeGrantsNothing() {
        GradesConfig.Grade vip = grade("vip");
        vip.permissions.add("customperm.command.fly");
        vip.permissionExpiries.put("customperm.command.fly", Expiry.now() - 1);
        assign("vip");
        assertEquals(Tristate.UNSET, check("customperm.command.fly"));

        vip.permissionExpiries.put("customperm.command.fly", Expiry.now() + 3600);
        assertEquals(Tristate.ALLOW, check("customperm.command.fly"), "until it expires, it grants");
    }

    @Test
    void anExpiredEntryLetsALessSpecificOneAnswer() {
        GradesConfig.Grade vip = grade("vip");
        vip.permissions.add("customperm.command.*");
        vip.deniedPermissions.add("customperm.command.fly");
        vip.deniedPermissionExpiries.put("customperm.command.fly", Expiry.now() - 1);
        assign("vip");
        assertEquals(Tristate.ALLOW, check("customperm.command.fly"),
                "an expired denial does not exist, so the wildcard below it decides");
    }

    @Test
    void anExpiredGradeIsNoLongerHeld() {
        grade("vip").permissions.add("x");
        assign("vip");
        grades.userGradeExpiries.put(user, new HashMap<>(Map.of("vip", Expiry.now() - 1)));
        assertEquals(Tristate.UNSET, check("x"));
    }

    @Test
    void anExpiredRefusalGivesTheGradeBack() {
        GradesConfig.Grade base = grade("base");
        base.permissions.add("x");
        GradesConfig.Grade vip = grade("vip");
        vip.parents.add("base");
        assign("vip");
        grades.userDeniedGrades.put(user, new ArrayList<>(List.of("base")));
        assertEquals(Tristate.UNSET, check("x"));

        grades.userDeniedGradeExpiries.put(user, new HashMap<>(Map.of("base", Expiry.now() - 1)));
        assertEquals(Tristate.ALLOW, check("x"));
    }

    @Test
    void aPlayersOwnTemporaryNodeExpiresToo() {
        grades.userPermissions.put(user, new java.util.LinkedHashSet<>(Set.of("x")));
        grades.userPermissionExpiries.put(user, new HashMap<>(Map.of("x", Expiry.now() + 60)));
        assertEquals(Tristate.ALLOW, check("x"));
        grades.userPermissionExpiries.get(user).put("x", Expiry.now() - 60);
        assertEquals(Tristate.UNSET, check("x"));
    }

    @Test
    void anExpiredGradeGivesNoPrefix() {
        grade("vip").prefixes.add(new GradesConfig.ChatEntry(0, "[VIP]", 0));
        assign("vip");
        grades.userGradeExpiries.put(user, new HashMap<>(Map.of("vip", Expiry.now() - 1)));
        assertNull(PermissionResolver.prefix(grades, player, null));
    }

    @Test
    void anExpiredParentIsNoLongerInherited() {
        grade("base").permissions.add("x");
        GradesConfig.Grade vip = grade("vip");
        vip.parents.add("base");
        assign("vip");
        vip.parentExpiries.put("base", Expiry.now() + 60);
        assertEquals(Tristate.ALLOW, check("x"), "until it expires, the parent is inherited");
        vip.parentExpiries.put("base", Expiry.now() - 1);
        assertEquals(Tristate.UNSET, check("x"));
    }

    @Test
    void aParentExpiredOnOnePathIsStillReachedByAnother() {
        grade("base").permissions.add("x");
        grade("middle").parents.add("base");
        GradesConfig.Grade vip = grade("vip");
        vip.parents.addAll(List.of("base", "middle"));
        vip.parentExpiries.put("base", Expiry.now() - 1);
        assign("vip");
        assertEquals(Tristate.ALLOW, check("x"),
                "the direct link ran out, but middle still inherits base for good");
    }

    @Test
    void anExpiredParentRefusalRefusesNothing() {
        grade("base").permissions.add("x");
        grade("middle").parents.add("base");
        GradesConfig.Grade vip = grade("vip");
        vip.parents.add("middle");
        vip.deniedParents.add("base");
        assign("vip");
        assertEquals(Tristate.UNSET, check("x"));
        vip.deniedParentExpiries.put("base", Expiry.now() - 1);
        assertEquals(Tristate.ALLOW, check("x"));
    }

    @Test
    void anExpiredParentGivesNoPrefix() {
        grade("base").prefixes.add(new GradesConfig.ChatEntry(0, "[Base]", 0));
        GradesConfig.Grade vip = grade("vip");
        vip.parents.add("base");
        vip.parentExpiries.put("base", Expiry.now() - 1);
        assign("vip");
        assertNull(PermissionResolver.prefix(grades, player, null));
    }

    // --- file hygiene ---

    @Test
    void anExpiryNamingNothingIsDroppedWhenTheFileIsRead() {
        GradesConfig.Grade vip = grade("vip");
        vip.permissionExpiries.put("gone", Expiry.now() + 60);
        grades.userGradeExpiries.put(user, new HashMap<>(Map.of("vip", Expiry.now() + 60)));
        grades.normalize();
        assertTrue(vip.permissionExpiries.isEmpty(),
                "a node added back later must not inherit an expiry left behind");
        assertTrue(grades.userGradeExpiries.isEmpty(), "the player does not hold the grade");

        vip.parentExpiries.put("gone", Expiry.now() + 60);
        vip.deniedParentExpiries.put("gone", Expiry.now() + 60);
        grades.normalize();
        assertTrue(vip.parentExpiries.isEmpty() && vip.deniedParentExpiries.isEmpty(),
                "a parent expiry naming no parent is dropped too");
    }

    private GradesConfig.Grade grade(String name) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grades.grades.put(name, grade);
        return grade;
    }

    private void assign(String... names) {
        grades.userGrades.put(user, new ArrayList<>(List.of(names)));
    }

    private Tristate check(String node) {
        return PermissionResolver.check(grades, player, node, null);
    }
}
