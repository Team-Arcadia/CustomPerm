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
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Meta decided like a node: the player's own, then the heaviest grade, then the nearest ancestor. Pure Java. */
class MetaResolverTest {

    private static final String KEY = "mymod.homes.max";
    private static final String NETHER = "world=minecraft:the_nether";

    private GradesConfig grades;
    private UUID player;
    private String user;

    @BeforeEach
    void setUp() {
        grades = new GradesConfig();
        player = UUID.randomUUID();
        user = player.toString();
    }

    @Test
    void nothingSetIsNull() {
        grade("member", 0);
        assign("member");
        assertNull(meta(Contexts.NONE));
    }

    @Test
    void thePlayersOwnWinsThenTheHeaviestGrade() {
        grade("member", 0).meta.put(KEY, "3");
        grade("vip", 10).meta.put(KEY, "5");
        assign("member", "vip");
        assertEquals("5", meta(Contexts.NONE), "the heaviest grade decides");
        grades.userMeta.put(user, new TreeMap<>(Map.of(KEY, "1")));
        assertEquals("1", meta(Contexts.NONE), "the player's own comes first");
    }

    @Test
    void theNearestAncestorWinsAndTheGradeItselfFirst() {
        grade("base", 0).meta.put(KEY, "2");
        GradesConfig.Grade middle = grade("middle", 0);
        middle.parents.add("base");
        middle.meta.put(KEY, "4");
        GradesConfig.Grade top = grade("top", 0);
        top.parents.add("middle");
        assign("top");
        assertEquals("4", meta(Contexts.NONE));
        top.meta.put(KEY, "9");
        assertEquals("9", meta(Contexts.NONE));
    }

    @Test
    void oneLimitedToTheWorldOutranksTheSameHoldersGlobalOne() {
        GradesConfig.Grade vip = grade("vip", 0);
        vip.meta.put(KEY, "5");
        GradesConfig.GradeScoped nether = new GradesConfig.GradeScoped();
        nether.meta.put(KEY, "1");
        vip.contexts.put(NETHER, nether);
        assign("vip");
        assertEquals("1", meta(Contexts.world("minecraft:the_nether")));
        assertEquals("5", meta(Contexts.world("minecraft:overworld")));
    }

    @Test
    void anExpiredValueIsGoneAndTheNextOneAnswers() {
        grade("vip", 0).meta.put(KEY, "5");
        assign("vip");
        grades.userMeta.put(user, new TreeMap<>(Map.of(KEY, "1")));
        grades.userMetaExpiries.put(user, new HashMap<>(Map.of(KEY, Expiry.now() - 1)));
        assertEquals("5", meta(Contexts.NONE));
    }

    @Test
    void aRefusedGradeGivesNoMetaAndTheDefaultGradeOnlyWhenNothingElseDoes() {
        grade("vip", 0).meta.put(KEY, "5");
        grade("everyone", 0).meta.put(KEY, "0");
        assign("vip");
        assertEquals("5", PermissionResolver.meta(grades, player, KEY, "everyone", Contexts.NONE));
        grades.userDeniedGrades.put(user, new ArrayList<>(List.of("vip")));
        assertEquals("0", PermissionResolver.meta(grades, player, KEY, "everyone", Contexts.NONE),
                "the refused grade says nothing, so the default grade answers");
    }

    @Test
    void twoGradesOfEqualWeightAgreeOnTheValueThatSortsFirst() {
        grade("a", 0).meta.put(KEY, "8");
        grade("b", 0).meta.put(KEY, "6");
        assign("a", "b");
        assertEquals("6", meta(Contexts.NONE));
        assign("b", "a");
        assertEquals("6", meta(Contexts.NONE), "the order grades were assigned in decides nothing");
    }

    @Test
    void keysAreLowercasedAndDanglingExpiriesDroppedWhenTheFileIsRead() {
        GradesConfig.Grade vip = grade("vip", 0);
        vip.meta.put("MyMod.Homes.Max", "5");
        vip.metaExpiries.put("gone", Expiry.now() + 60);
        grades.userMeta.put(user, new TreeMap<>(Map.of("Rank", "gold")));
        grades.userMetaExpiries.put(user, new HashMap<>(Map.of("rank", Expiry.now() + 60, "gone", Expiry.now() + 60)));
        grades.normalize();
        assertEquals(Map.of(KEY, "5"), vip.meta);
        assertTrue(vip.metaExpiries.isEmpty());
        assertEquals(Map.of("rank", "gold"), grades.userMeta.get(user));
        assertEquals(java.util.Set.of("rank"), grades.userMetaExpiries.get(user).keySet());
    }

    private String meta(Contexts contexts) {
        return PermissionResolver.meta(grades, player, KEY, null, contexts);
    }

    private GradesConfig.Grade grade(String name, int weight) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grade.weight = weight;
        grades.grades.put(name, grade);
        return grade;
    }

    private void assign(String... names) {
        grades.userGrades.put(user, new ArrayList<>(List.of(names)));
    }
}
