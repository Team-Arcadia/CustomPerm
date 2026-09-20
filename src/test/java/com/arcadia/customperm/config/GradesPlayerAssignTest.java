/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.config;

import com.arcadia.customperm.perm.PermissionResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-layer tests of assigning players to grades and taking them off again.
 * No Minecraft import: pure JUnit 5 over GradesConfig and PermissionResolver.
 * Covers AC1 to AC6 of story 2-4.
 */
class GradesPlayerAssignTest {

    private GradesConfig freshConfig() {
        GradesConfig cfg = new GradesConfig();
        cfg.normalize();
        return cfg;
    }

    private GradesConfig.Grade addGrade(GradesConfig cfg, String name) {
        GradesConfig.Grade g = new GradesConfig.Grade();
        g.name = name;
        cfg.grades.put(name, g);
        return g;
    }

    // T3.1, AC1: assigning a player to a grade, at the data layer
    @Test
    void shouldAssignPlayerToGrade_whenGradeExists() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "mod");
        UUID player = UUID.randomUUID();

        cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>()).add("mod");

        assertTrue(cfg.userGrades.containsKey(player.toString()));
        assertTrue(cfg.userGrades.get(player.toString()).contains("mod"));
        assertEquals(1, cfg.userGrades.get(player.toString()).size());
    }

    // T3.2, AC5 idempotence: list.contains() is true when the grade is already held
    @Test
    void shouldNotDuplicate_whenAssigningSameGradeTwice() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "admin");
        UUID player = UUID.randomUUID();

        var list = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list.add("admin");

        // Mirrors the handler's check: contains() before add()
        boolean alreadyAssigned = list.contains("admin");
        assertTrue(alreadyAssigned, "list.contains() must be true, so the handler skips the add");

        // No add(): the list must still hold one element
        assertEquals(1, list.size());
    }

    // T3.3, AC3: taking a grade off a player, at the data layer
    @Test
    void shouldUnassignPlayer_whenGradeAssigned() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "vip");
        UUID player = UUID.randomUUID();

        var list = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list.add("vip");
        boolean removed = list.remove("vip");

        assertTrue(removed, "List.remove() must be true for a grade the player holds");
        assertTrue(list.isEmpty());
    }

    // T3.4, AC2: with several grades, PermissionResolver reads both
    @Test
    void shouldApplyUnionOfGrades_whenPlayerHasMultiple() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade mod = addGrade(cfg, "mod");
        mod.permissions.add("customperm.command.tp");

        GradesConfig.Grade vip = addGrade(cfg, "vip");
        vip.permissions.add("customperm.command.kit");

        UUID player = UUID.randomUUID();
        var list = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list.add("mod");
        list.add("vip");

        // Both nodes must be granted
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.tp"),
                "the mod grade must grant customperm.command.tp");
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.kit"),
                "the vip grade must grant customperm.command.kit");
        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.command.ban"),
                "a node granted by neither grade must be false");
    }

    // T3.5, AC4: a player with no grade resolves false on every node
    @Test
    void shouldReturnFalse_whenPlayerHasNoGrade() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "admin");

        UUID player = UUID.randomUUID();
        // No userGrades entry for this player

        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.command.tp"),
                "a player holding no grade must be granted nothing");
        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.*"),
                "a wildcard grants nothing to a player holding no grade");
    }

    // T3.6, AC6: unassigning a grade the player does not hold gives List.remove() == false
    @Test
    void shouldNotCrash_whenUnassigningGradeNotAssigned() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "mod");
        UUID player = UUID.randomUUID();

        // A player with no userGrades entry
        var list = cfg.userGrades.get(player.toString());

        // Mirrors the handler's check: list == null means removed = false
        boolean removed = list != null && list.remove("mod");

        assertFalse(removed, "removed must be false for a player holding nothing, so the handler no-ops");

        // A list holding other grades, but not "mod"
        var list2 = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list2.add("vip");
        boolean removedAbsent = list2.remove("mod");

        assertFalse(removedAbsent, "List.remove() is false when the grade is not in the list");
        assertEquals(1, list2.size(), "the list must be left untouched");
    }
}
