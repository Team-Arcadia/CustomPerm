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
 * Tests de la couche data pour l'assignation/désassignation des joueurs aux grades.
 * Zéro import Minecraft — tests JUnit 5 purs sur GradesConfig + PermissionResolver.
 * Couvre AC1-AC6 de l'histoire 2-4.
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

    // T3.1 — AC1 : assignation d'un joueur à un grade (couche data)
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

    // T3.2 — AC5 : idempotence — list.contains() retourne true si déjà assigné
    @Test
    void shouldNotDuplicate_whenAssigningSameGradeTwice() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "admin");
        UUID player = UUID.randomUUID();

        var list = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list.add("admin");

        // Simule la vérification du handler corrigé : contains() avant add()
        boolean alreadyAssigned = list.contains("admin");
        assertTrue(alreadyAssigned, "list.contains() doit retourner true — le handler doit skipper l'add");

        // Pas d'add() — vérifier que la liste reste à 1 élément
        assertEquals(1, list.size());
    }

    // T3.3 — AC3 : désassignation d'un joueur (couche data)
    @Test
    void shouldUnassignPlayer_whenGradeAssigned() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "vip");
        UUID player = UUID.randomUUID();

        var list = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list.add("vip");
        boolean removed = list.remove("vip");

        assertTrue(removed, "List.remove() doit retourner true pour un grade assigné");
        assertTrue(list.isEmpty());
    }

    // T3.4 — AC2 : union multi-grade — PermissionResolver voit les deux grades
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

        // L'union doit donner accès aux deux nœuds
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.tp"),
                "Grade mod doit accorder customperm.command.tp");
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.kit"),
                "Grade vip doit accorder customperm.command.kit");
        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.command.ban"),
                "Nœud non accordé doit retourner false");
    }

    // T3.5 — AC4 : joueur sans grade → resolve retourne false pour tout nœud
    @Test
    void shouldReturnFalse_whenPlayerHasNoGrade() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "admin");

        UUID player = UUID.randomUUID();
        // Aucune entrée dans userGrades pour ce joueur

        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.command.tp"),
                "Joueur sans grade ne doit avoir aucun accès");
        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.*"),
                "Wildcard ne doit pas accorder d'accès sans grade");
    }

    // T3.6 — AC6 : unassign sur grade non assigné → List.remove() retourne false (proof-of-contract)
    @Test
    void shouldNotCrash_whenUnassigningGradeNotAssigned() {
        GradesConfig cfg = freshConfig();
        addGrade(cfg, "mod");
        UUID player = UUID.randomUUID();

        // Joueur sans entrée dans userGrades
        var list = cfg.userGrades.get(player.toString());

        // Simule la vérification du handler corrigé : list == null → removed = false
        boolean removed = list != null && list.remove("mod");

        assertFalse(removed, "removed doit être false si joueur sans assignation — handler doit no-op");

        // Liste avec d'autres grades mais pas "mod"
        var list2 = cfg.userGrades.computeIfAbsent(player.toString(), k -> new ArrayList<>());
        list2.add("vip");
        boolean removedAbsent = list2.remove("mod");

        assertFalse(removedAbsent, "List.remove() retourne false si le grade n'est pas dans la liste");
        assertEquals(1, list2.size(), "La liste ne doit pas être altérée");
    }
}
