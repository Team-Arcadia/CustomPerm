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

class GradesPermNodeTest {

    private GradesConfig freshConfig() {
        GradesConfig cfg = new GradesConfig();
        cfg.normalize();
        return cfg;
    }

    private GradesConfig.Grade addGrade(GradesConfig cfg, String gradeName) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = gradeName;
        cfg.grades.put(gradeName, grade);
        return grade;
    }

    private void assignPlayer(GradesConfig cfg, UUID player, String gradeName) {
        cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>()).add(gradeName);
    }

    // T3.1, AC1: adding a node the grade lacks
    @Test
    void shouldAddPermNode_whenNodeDoesNotExist() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "mod");

        boolean added = grade.permissions.add("customperm.command.tp");

        assertTrue(added);
        assertTrue(grade.permissions.contains("customperm.command.tp"));
    }

    // T3.2, AC5 idempotence: Set.add() is false when the node is already there
    @Test
    void shouldBeIdempotent_whenAddingDuplicateNode() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "mod");

        grade.permissions.add("customperm.command.tp");
        boolean addedAgain = grade.permissions.add("customperm.command.tp");

        assertFalse(addedAgain);
        assertEquals(1, grade.permissions.size());
    }

    // T3.3, AC2: removing a node the grade holds
    @Test
    void shouldRemovePermNode_whenNodeExists() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "mod");

        grade.permissions.add("customperm.command.tp");
        grade.permissions.remove("customperm.command.tp");

        assertFalse(grade.permissions.contains("customperm.command.tp"));
        assertTrue(grade.permissions.isEmpty());
    }

    // T3.4, AC2: the other nodes survive the removal of one
    @Test
    void shouldPreserveOtherNodes_whenOneIsRemoved() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "admin");

        grade.permissions.add("customperm.command.tp");
        grade.permissions.add("customperm.command.gamemode");
        grade.permissions.remove("customperm.command.tp");

        assertFalse(grade.permissions.contains("customperm.command.tp"));
        assertTrue(grade.permissions.contains("customperm.command.gamemode"));
        assertEquals(1, grade.permissions.size());
    }

    // T3.5, AC3: a wildcard node is stored, and answers for a node below it
    @Test
    void shouldStoreWildcardNode_andResolveForDescendant() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "vip");

        grade.permissions.add("customperm.command.*");

        UUID player = UUID.randomUUID();
        assignPlayer(cfg, player, "vip");

        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.tp"));
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.gamemode"));
        assertFalse(PermissionResolver.resolve(cfg, player, "other.node"));
    }

    // T3.6, AC4: the node is stored even where the command is not exposed, at the data layer
    @Test
    void shouldStoreNode_regardlessOfCommandExposure() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "staff");

        // The node is stored with no command exposed (NFR5 lives in the command tree)
        grade.permissions.add("customperm.command.nonexistent");

        assertTrue(grade.permissions.contains("customperm.command.nonexistent"));

        UUID player = UUID.randomUUID();
        assignPlayer(cfg, player, "staff");

        // PermissionResolver answers true: the node is granted at the data layer.
        // What actually refuses access (NFR5) is CommandTreeRewriter, not this.
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.nonexistent"));
    }
}
