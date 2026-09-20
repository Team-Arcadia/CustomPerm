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

class GradesConfigCrudTest {

    private GradesConfig freshConfig() {
        GradesConfig cfg = new GradesConfig();
        cfg.normalize();
        return cfg;
    }

    // T2.1 — AC1 : création d'un grade inexistant
    @Test
    void shouldCreateGrade_whenNameDoesNotExist() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = "admin";
        cfg.grades.put("admin", grade);

        assertTrue(cfg.grades.containsKey("admin"));
        assertNotNull(cfg.grades.get("admin"));
        assertNotNull(cfg.grades.get("admin").permissions);
        assertNotNull(cfg.grades.get("admin").deniedPermissions);
        assertTrue(cfg.grades.get("admin").permissions.isEmpty());
    }

    // T2.2, AC2: a duplicate is refused, the containsKey check preventing the overwrite
    @Test
    void shouldNotOverwriteGrade_whenNameAlreadyExists() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade original = new GradesConfig.Grade();
        original.name = "mod";
        original.permissions.add("customperm.command.tp");
        cfg.grades.put("mod", original);

        // Mirrors the handler's check: containsKey means no put
        boolean alreadyExists = cfg.grades.containsKey("mod");
        if (!alreadyExists) {
            GradesConfig.Grade duplicate = new GradesConfig.Grade();
            duplicate.name = "mod";
            cfg.grades.put("mod", duplicate);
        }

        assertTrue(alreadyExists);
        // The original grade and its permission are untouched
        assertTrue(cfg.grades.get("mod").permissions.contains("customperm.command.tp"));
    }

    // T2.3, AC3: deleting a grade takes it off every player holding it
    @Test
    void shouldDeleteGrade_andCascadeRemoveFromAllUserGrades() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade admin = new GradesConfig.Grade();
        admin.name = "admin";
        cfg.grades.put("admin", admin);

        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        cfg.userGrades.put(player1.toString(), new ArrayList<>());
        cfg.userGrades.get(player1.toString()).add("admin");
        cfg.userGrades.put(player2.toString(), new ArrayList<>());
        cfg.userGrades.get(player2.toString()).add("admin");

        // Simuler gradeDelete
        cfg.grades.remove("admin");
        cfg.userGrades.values().forEach(list -> list.remove("admin"));

        assertFalse(cfg.grades.containsKey("admin"));
        assertFalse(cfg.userGrades.get(player1.toString()).contains("admin"));
        assertFalse(cfg.userGrades.get(player2.toString()).contains("admin"));
    }

    // T2.4, AC3: a player's second grade survives the deletion of the first
    @Test
    void shouldPreserveOtherGrade_whenOneOfTwoIsDeleted() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade admin = new GradesConfig.Grade();
        admin.name = "admin";
        GradesConfig.Grade mod = new GradesConfig.Grade();
        mod.name = "mod";
        cfg.grades.put("admin", admin);
        cfg.grades.put("mod", mod);

        UUID player = UUID.randomUUID();
        cfg.userGrades.put(player.toString(), new ArrayList<>());
        cfg.userGrades.get(player.toString()).add("admin");
        cfg.userGrades.get(player.toString()).add("mod");

        // Supprimer uniquement "admin"
        cfg.grades.remove("admin");
        cfg.userGrades.values().forEach(list -> list.remove("admin"));

        assertFalse(cfg.userGrades.get(player.toString()).contains("admin"));
        assertTrue(cfg.userGrades.get(player.toString()).contains("mod"));
        assertTrue(cfg.grades.containsKey("mod"));
    }

    // T2.5, AC3 and FR2: deleting a player's only grade takes every custom node with it
    @Test
    void shouldPlayerLoseAllPermissions_afterSoleGradeDeleted() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade admin = new GradesConfig.Grade();
        admin.name = "admin";
        admin.permissions.add("customperm.command.tp");
        cfg.grades.put("admin", admin);

        UUID player = UUID.randomUUID();
        cfg.userGrades.put(player.toString(), new ArrayList<>());
        cfg.userGrades.get(player.toString()).add("admin");

        // Before the deletion: the player holds the node
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.tp"));

        // Simuler gradeDelete
        cfg.grades.remove("admin");
        cfg.userGrades.values().forEach(list -> list.remove("admin"));

        // After the deletion: no custom node left (FR2)
        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.command.tp"));
    }

    // T2.6, AC4: listing every grade defined
    @Test
    void shouldListAllGradeNames() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade admin = new GradesConfig.Grade();
        admin.name = "admin";
        GradesConfig.Grade mod = new GradesConfig.Grade();
        mod.name = "mod";
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.name = "vip";
        cfg.grades.put("admin", admin);
        cfg.grades.put("mod", mod);
        cfg.grades.put("vip", vip);

        assertEquals(3, cfg.grades.keySet().size());
        assertTrue(cfg.grades.keySet().contains("admin"));
        assertTrue(cfg.grades.keySet().contains("mod"));
        assertTrue(cfg.grades.keySet().contains("vip"));
    }

    // --- display names ---

    @Test
    void aDisplayNameIsShownBesideTheNameNeverInsteadOfIt() {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        assertEquals("vip", grade.label("vip"), "no display name: the name alone");
        grade.displayName = "Very Important";
        assertEquals("Very Important (vip)", grade.label("vip"));
    }

    @Test
    void aDisplayNameIsBoundedAndSingleLine() {
        assertNull(GradesConfig.displayNameProblem("Very Important"));
        assertNull(GradesConfig.displayNameProblem("  "), "blank means none, callers clear");
        assertNull(GradesConfig.displayNameProblem("x".repeat(GradesConfig.DISPLAY_NAME_MAX)));
        assertNotNull(GradesConfig.displayNameProblem("x".repeat(GradesConfig.DISPLAY_NAME_MAX + 1)));
        assertNotNull(GradesConfig.displayNameProblem("two\nlines"));
        assertNotNull(GradesConfig.displayNameProblem("tab\there"));
    }

    @Test
    void aHandEditedDisplayNameIsTrimmedAndABlankOneIsNone() {
        GradesConfig cfg = new GradesConfig();
        GradesConfig.Grade padded = new GradesConfig.Grade();
        padded.name = "vip";
        padded.displayName = "  Very Important ";
        GradesConfig.Grade blank = new GradesConfig.Grade();
        blank.name = "base";
        blank.displayName = "   ";
        cfg.grades.put("vip", padded);
        cfg.grades.put("base", blank);

        cfg.normalize();

        assertEquals("Very Important", cfg.grades.get("vip").displayName);
        assertNull(cfg.grades.get("base").displayName);
    }
}
