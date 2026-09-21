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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a grade or a player says about one server: the entries naming {@code server=}, the others left out. The
 * order is the resolver's: the player's own entry above their grades, the heaviest grade above a lighter one.
 */
class ServerScopedResolverTest {

    private static final String NODE = "customperm.command.tp";
    private static final Contexts ON_S2 = Contexts.of("minecraft:overworld", "survival", Map.of("server", "s2"));

    private GradesConfig grades;
    private UUID player;

    @BeforeEach
    void setUp() {
        grades = new GradesConfig();
        player = UUID.randomUUID();
    }

    private GradesConfig.Grade grade(String name, int weight) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.weight = weight;
        grades.grades.put(name, grade);
        grades.userGrades.computeIfAbsent(player.toString(), k -> new java.util.ArrayList<>()).add(name);
        return grade;
    }

    private static GradesConfig.GradeScoped on(GradesConfig.Grade grade, String context) {
        return grade.contexts.computeIfAbsent(context, k -> new GradesConfig.GradeScoped());
    }

    private Tristate serverScoped() {
        return PermissionResolver.checkServerScoped(grades, player, NODE, null, ON_S2);
    }

    @Test
    void aNodeHeldEverywhereSaysNothingAboutAServer() {
        grade("vip", 0).permissions.add(NODE);
        assertEquals(Tristate.UNSET, serverScoped(), "A grade holding the node everywhere must not undo a command's list.");
        assertEquals(Tristate.ALLOW, PermissionResolver.check(grades, player, NODE, null, ON_S2));
    }

    @Test
    void aGradeEntryNamingTheServerDecides() {
        GradesConfig.Grade vip = grade("vip", 0);
        vip.permissions.add(NODE);
        on(vip, "server=s2").deniedPermissions.add(NODE);
        assertEquals(Tristate.DENY, serverScoped());
        assertEquals(Tristate.DENY, PermissionResolver.check(grades, player, NODE, null, ON_S2),
                "Not on server 2 must win over the same grade's node held everywhere.");
        assertEquals(Tristate.UNSET, PermissionResolver.checkServerScoped(grades, player, NODE, null,
                Contexts.of("minecraft:overworld", "survival", Map.of("server", "s1"))), "Another server is not concerned.");
    }

    @Test
    void anEntryNamingTheServerAndMoreSpeaksAboutIt() {
        on(grade("vip", 0), "server=s2,world=minecraft:overworld").permissions.add(NODE);
        assertEquals(Tristate.ALLOW, serverScoped());
    }

    @Test
    void thePlayerHasTheLastWordOverTheirGrades() {
        on(grade("vip", 100), "server=s2").permissions.add(NODE);
        GradesConfig.UserScoped own = new GradesConfig.UserScoped();
        own.deniedPermissions.add(NODE);
        grades.userContexts.computeIfAbsent(player.toString(), k -> new java.util.HashMap<>()).put("server=s2", own);
        assertEquals(Tristate.DENY, serverScoped());
    }

    @Test
    void theHeaviestGradeWinsBetweenGrades() {
        on(grade("staff", 100), "server=s2").permissions.add(NODE);
        on(grade("member", 1), "server=s2").deniedPermissions.add(NODE);
        assertEquals(Tristate.ALLOW, serverScoped());
    }

    @Test
    void outsideAnyContextNothingIsSaid() {
        on(grade("vip", 0), "server=s2").permissions.add(NODE);
        assertEquals(Tristate.UNSET, PermissionResolver.checkServerScoped(grades, player, NODE, null, Contexts.NONE));
    }
}
