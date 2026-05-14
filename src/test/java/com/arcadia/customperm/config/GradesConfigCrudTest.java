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

    // T2.2 — AC2 : refus de doublon (la condition containsKey empêche l'écrasement)
    @Test
    void shouldNotOverwriteGrade_whenNameAlreadyExists() {
        GradesConfig cfg = freshConfig();

        GradesConfig.Grade original = new GradesConfig.Grade();
        original.name = "mod";
        original.permissions.add("customperm.command.tp");
        cfg.grades.put("mod", original);

        // Simuler la condition handler : containsKey → ne pas put
        boolean alreadyExists = cfg.grades.containsKey("mod");
        if (!alreadyExists) {
            GradesConfig.Grade duplicate = new GradesConfig.Grade();
            duplicate.name = "mod";
            cfg.grades.put("mod", duplicate);
        }

        assertTrue(alreadyExists);
        // Le grade original avec sa permission est intact
        assertTrue(cfg.grades.get("mod").permissions.contains("customperm.command.tp"));
    }

    // T2.3 — AC3 : suppression avec désassignation en cascade sur plusieurs joueurs
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

    // T2.4 — AC3 : le second grade d'un joueur survit à la suppression du premier
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

    // T2.5 — AC3 + FR2 : le joueur perd toutes les perms custom après suppression de son seul grade
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

        // Avant suppression : le joueur a la permission
        assertTrue(PermissionResolver.resolve(cfg, player, "customperm.command.tp"));

        // Simuler gradeDelete
        cfg.grades.remove("admin");
        cfg.userGrades.values().forEach(list -> list.remove("admin"));

        // Après suppression : plus aucune permission custom (FR2)
        assertFalse(PermissionResolver.resolve(cfg, player, "customperm.command.tp"));
    }

    // T2.6 — AC4 : listage de tous les grades définis
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
}
