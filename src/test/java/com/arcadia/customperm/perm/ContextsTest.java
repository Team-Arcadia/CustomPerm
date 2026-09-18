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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Contextual entries: how a context is written, and how the resolver ranks an entry limited to one. */
class ContextsTest {

    private static final String NETHER = "world=minecraft:the_nether";
    private static final Contexts IN_NETHER = Contexts.world("minecraft:the_nether");
    private static final Contexts IN_OVERWORLD = Contexts.world("minecraft:overworld");

    private GradesConfig grades;
    private UUID player;

    @BeforeEach
    void setUp() {
        grades = new GradesConfig();
        player = UUID.randomUUID();
    }

    // ─── Parsing ───────────────────────────────────────────────────────────────

    @Test
    void parse_addsTheVanillaNamespaceAndLowercases() {
        assertEquals(NETHER, Contexts.parse("world=the_nether"));
        assertEquals(NETHER, Contexts.parse(" World=Minecraft:The_Nether "));
        assertEquals("world=mymod:mining_world", Contexts.parse("world=mymod:mining_world"));
    }

    @Test
    void parse_refusesWhatIsNotAWorldContext() {
        assertNull(Contexts.parse(null));
        assertNull(Contexts.parse(""));
        assertNull(Contexts.parse("the_nether"));
        assertNull(Contexts.parse("=the_nether"));
        assertNull(Contexts.parse("world="));
        assertNull(Contexts.parse("world=bad name"));
        assertNull(Contexts.parse("server=lobby"), "only world is read until cluster mode adds server");
        assertNull(Contexts.parse("world=a,world=b"), "one value per key");
    }

    @Test
    void satisfies_needsEveryPair() {
        assertTrue(IN_NETHER.satisfies(NETHER));
        assertTrue(IN_NETHER.satisfies(""));
        assertFalse(IN_OVERWORLD.satisfies(NETHER));
        assertFalse(IN_NETHER.satisfies(NETHER + ",server=lobby"));
        assertFalse(Contexts.NONE.satisfies(NETHER));
        assertFalse(IN_NETHER.satisfies("world=minecraft:the_nethe"), "a prefix of a pair is not the pair");
    }

    @Test
    void size_andDescribe() {
        assertEquals(0, Contexts.size(""));
        assertEquals(1, Contexts.size(NETHER));
        assertEquals(2, Contexts.size(NETHER + ",server=lobby"));
        assertEquals("the_nether", Contexts.describe(NETHER));
        assertEquals("mymod:mining", Contexts.describe("world=mymod:mining"));
        assertEquals("minecraft:the_nether", Contexts.worldOf(NETHER));
    }

    // ─── Resolution ────────────────────────────────────────────────────────────

    @Test
    void gradeNode_appliesOnlyInItsWorld() {
        GradesConfig.Grade builder = grade("builder", 0);
        scope(builder, NETHER).permissions.add("customperm.command.fly");
        assign("builder");

        assertEquals(Tristate.ALLOW, check("customperm.command.fly", IN_NETHER));
        assertEquals(Tristate.UNSET, check("customperm.command.fly", IN_OVERWORLD));
        assertEquals(Tristate.UNSET, check("customperm.command.fly", Contexts.NONE));
    }

    @Test
    void contextualEntry_outranksTheSameHoldersGlobalOne() {
        GradesConfig.Grade member = grade("member", 0);
        member.permissions.add("customperm.command.home");
        scope(member, NETHER).deniedPermissions.add("customperm.command.home");
        assign("member");

        assertEquals(Tristate.DENY, check("customperm.command.home", IN_NETHER));
        assertEquals(Tristate.ALLOW, check("customperm.command.home", IN_OVERWORLD));

        member.permissions.remove("customperm.command.home");
        member.deniedPermissions.add("customperm.command.home");
        member.contexts.clear();
        scope(member, NETHER).permissions.add("customperm.command.home");
        assertEquals(Tristate.ALLOW, check("customperm.command.home", IN_NETHER),
                "the contextual ALLOW outranks a global DENY: the context is a rank, not a DENY tie-break");
    }

    @Test
    void specificityStillComesFirst() {
        GradesConfig.Grade member = grade("member", 0);
        member.permissions.add("customperm.command.home");
        scope(member, NETHER).deniedPermissions.add("customperm.command.*");
        assign("member");

        assertEquals(Tristate.ALLOW, check("customperm.command.home", IN_NETHER),
                "an exact node beats a contextual wildcard");
        assertEquals(Tristate.DENY, check("customperm.command.spawn", IN_NETHER));
    }

    @Test
    void holderRankStillComesBeforeTheContext() {
        GradesConfig.Grade light = grade("light", 1);
        scope(light, NETHER).deniedPermissions.add("customperm.command.tp");
        GradesConfig.Grade heavy = grade("heavy", 10);
        heavy.permissions.add("customperm.command.tp");
        assign("light", "heavy");

        assertEquals(Tristate.ALLOW, check("customperm.command.tp", IN_NETHER),
                "a heavier grade decides over a lighter grade's contextual node");

        heavy.weight = 1;
        assertEquals(Tristate.DENY, check("customperm.command.tp", IN_NETHER),
                "at equal weight the contextual node decides");
    }

    @Test
    void playersOwnGlobalNode_beatsAGradesContextualOne() {
        GradesConfig.Grade member = grade("member", 0);
        scope(member, NETHER).deniedPermissions.add("customperm.command.tp");
        assign("member");
        grades.userPermissions.put(player.toString(), new java.util.HashSet<>(List.of("customperm.command.tp")));

        assertEquals(Tristate.ALLOW, check("customperm.command.tp", IN_NETHER));
    }

    @Test
    void playersOwnContextualNode_outranksTheirGlobalOne() {
        grades.userDeniedPermissions.put(player.toString(), new java.util.HashSet<>(List.of("customperm.command.tp")));
        userScope(NETHER).permissions.add("customperm.command.tp");

        assertEquals(Tristate.ALLOW, check("customperm.command.tp", IN_NETHER));
        assertEquals(Tristate.DENY, check("customperm.command.tp", IN_OVERWORLD));
    }

    @Test
    void gradeHeldInAWorld_appliesOnlyThere() {
        GradesConfig.Grade builder = grade("builder", 0);
        builder.permissions.add("customperm.command.gamemode");
        userScope(NETHER).grades.add("builder");

        assertEquals(Tristate.ALLOW, check("customperm.command.gamemode", IN_NETHER));
        assertEquals(Tristate.UNSET, check("customperm.command.gamemode", IN_OVERWORLD));
    }

    @Test
    void gradeHeldInAWorld_isStillRefusable() {
        GradesConfig.Grade builder = grade("builder", 0);
        builder.permissions.add("customperm.command.gamemode");
        userScope(NETHER).grades.add("builder");
        grades.userDeniedGrades.put(player.toString(), new java.util.ArrayList<>(List.of("builder")));

        assertEquals(Tristate.UNSET, check("customperm.command.gamemode", IN_NETHER));
    }

    @Test
    void contextualNodeInherited_throughAParent() {
        GradesConfig.Grade base = grade("base", 0);
        scope(base, NETHER).permissions.add("customperm.command.fly");
        GradesConfig.Grade vip = grade("vip", 0);
        vip.parents.add("base");
        assign("vip");

        assertEquals(Tristate.ALLOW, check("customperm.command.fly", IN_NETHER));
        assertEquals(Tristate.UNSET, check("customperm.command.fly", IN_OVERWORLD));

        vip.deniedPermissions.add("customperm.command.fly");
        assertEquals(Tristate.DENY, check("customperm.command.fly", IN_NETHER),
                "a child overrides what it inherits, contextual or not");
    }

    @Test
    void defaultGrade_contextualNodeApplies() {
        GradesConfig.Grade everyone = grade("everyone", 0);
        scope(everyone, NETHER).deniedPermissions.add("customperm.command.home");

        assertEquals(Tristate.DENY,
                PermissionResolver.check(grades, player, "customperm.command.home", "everyone", IN_NETHER));
        assertEquals(Tristate.UNSET,
                PermissionResolver.check(grades, player, "customperm.command.home", "everyone", IN_OVERWORLD));
    }

    @Test
    void normalize_mergesSpellingsAndKeepsUnknownKeys() {
        GradesConfig.Grade member = grade("member", 0);
        scope(member, "world=the_nether").permissions.add("a");
        scope(member, NETHER).permissions.add("b");
        scope(member, "server=lobby").permissions.add("c");
        scope(member, "world=minecraft:the_end");
        grades.normalize();

        assertEquals(java.util.Set.of(NETHER, "server=lobby"), member.contexts.keySet(),
                "one entry per stored form, an unknown key kept as it is, an empty scope dropped");
        assertEquals(java.util.Set.of("a", "b"), member.contexts.get(NETHER).permissions);
    }

    @Test
    void hasContextualEntries() {
        grade("member", 0);
        assertFalse(grades.hasContextualEntries());
        userScope(NETHER).grades.add("member");
        assertTrue(grades.hasContextualEntries());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Tristate check(String node, Contexts contexts) {
        return PermissionResolver.check(grades, player, node, null, contexts);
    }

    private GradesConfig.Grade grade(String name, int weight) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grade.weight = weight;
        grades.grades.put(name, grade);
        return grade;
    }

    private void assign(String... names) {
        grades.userGrades.put(player.toString(), new java.util.ArrayList<>(List.of(names)));
    }

    private static GradesConfig.Scoped scope(GradesConfig.Grade grade, String context) {
        return grade.contexts.computeIfAbsent(context, k -> new GradesConfig.Scoped());
    }

    private GradesConfig.UserScoped userScope(String context) {
        Map<String, GradesConfig.UserScoped> scopes =
                grades.userContexts.computeIfAbsent(player.toString(), k -> new HashMap<>());
        return scopes.computeIfAbsent(context, k -> new GradesConfig.UserScoped());
    }
}
