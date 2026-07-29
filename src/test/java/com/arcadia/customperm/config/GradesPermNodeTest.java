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

    // T3.1 — AC1 : ajout d'un nœud inexistant
    @Test
    void shouldAddPermNode_whenNodeDoesNotExist() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "mod");

        boolean added = grade.permissions.add("customperm.command.tp");

        assertTrue(added);
        assertTrue(grade.permissions.contains("customperm.command.tp"));
    }

    // T3.2 — AC5 : idempotence — Set.add() retourne false si nœud déjà présent
    @Test
    void shouldBeIdempotent_whenAddingDuplicateNode() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "mod");

        grade.permissions.add("customperm.command.tp");
        boolean addedAgain = grade.permissions.add("customperm.command.tp");

        assertFalse(addedAgain);
        assertEquals(1, grade.permissions.size());
    }

    // T3.3 — AC2 : retrait d'un nœud existant
    @Test
    void shouldRemovePermNode_whenNodeExists() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "mod");

        grade.permissions.add("customperm.command.tp");
        grade.permissions.remove("customperm.command.tp");

        assertFalse(grade.permissions.contains("customperm.command.tp"));
        assertTrue(grade.permissions.isEmpty());
    }

    // T3.4 — AC2 : les autres nœuds survivent au retrait d'un seul
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

    // T3.5 — AC3 : nœud wildcard stocké et résolu pour un descendant
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

    // T3.6 — AC4 : nœud stocké dans le Set même si commande non exposée (data-layer)
    @Test
    void shouldStoreNode_regardlessOfCommandExposure() {
        GradesConfig cfg = freshConfig();
        GradesConfig.Grade grade = addGrade(cfg, "staff");

        // Le nœud est stocké même sans exposition de commande (NFR5 = logique CommandTree)
        grade.permissions.add("customperm.command.nonexistent");

        assertTrue(grade.permissions.contains("customperm.command.nonexistent"));

        UUID player = UUID.randomUUID();
        assignPlayer(cfg, player, "staff");

        // Le PermissionResolver retourne true — le nœud est accordé en data-layer
        // L'absence d'accès effectif (NFR5) est garantie par CommandTreeRewriter, pas ici
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.nonexistent"));
    }
}
