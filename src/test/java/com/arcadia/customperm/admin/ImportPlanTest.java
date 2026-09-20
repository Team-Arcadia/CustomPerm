/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.admin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules an import applies to a LuckPerms node, and the report it comes back with. Pure Java, no
 * Minecraft and no LuckPerms: what is checked here is the decision, not the reading.
 */
class ImportPlanTest {

    // ─── Node translation ─────────────────────────────────────────────────────

    @Test
    void customPermNodesCarryOverAsTheyAre() {
        assertEquals("customperm.command.gamemode", ImportPlan.translate("customperm.command.gamemode"));
        assertEquals("customperm.*", ImportPlan.translate("customperm.*"));
        assertEquals("customperm.admin", ImportPlan.translate("customperm.admin"));
    }

    @Test
    void minecraftCommandNodesBecomeCustomPermOnes() {
        assertEquals("customperm.command.gamemode", ImportPlan.translate("minecraft.command.gamemode"));
        assertEquals("customperm.command.*", ImportPlan.translate("minecraft.command.*"));
    }

    @Test
    void theGlobalWildcardStaysItself() {
        assertEquals("*", ImportPlan.translate("*"));
    }

    @Test
    void whatAnotherModReadsIsNotImported() {
        // Storing it would put a string in grades.json that nothing reads once LuckPerms is gone.
        assertNull(ImportPlan.translate("essentials.fly"));
        assertNull(ImportPlan.translate("worldedit.navigation.jumpto"));
        assertNull(ImportPlan.translate("minecraft.autocraft"), "only minecraft.command.* has a meaning here");
    }

    @Test
    void anEmptyOrMissingKeyIsNotANode() {
        assertNull(ImportPlan.translate(null));
        assertNull(ImportPlan.translate(""));
        assertNull(ImportPlan.translate("   "));
        assertEquals("customperm.admin", ImportPlan.translate("  customperm.admin  "));
    }

    // ─── Commandes à exposer ──────────────────────────────────────────────────

    @Test
    void aTranslatedNodeNamesTheCommandItNeedsExposed() {
        assertEquals("gamemode", ImportPlan.exposedCommand("minecraft.command.gamemode"));
        assertEquals("tp", ImportPlan.exposedCommand("customperm.command.tp"));
    }

    @Test
    void aWildcardNamesNoCommand() {
        assertNull(ImportPlan.exposedCommand("customperm.command.*"));
        assertNull(ImportPlan.exposedCommand("minecraft.command.*"));
        assertNull(ImportPlan.exposedCommand("*"));
        assertNull(ImportPlan.exposedCommand("customperm.admin"));
        assertNull(ImportPlan.exposedCommand("essentials.fly"));
    }

    // ─── Rapport ──────────────────────────────────────────────────────────────

    @Test
    void theReportCountsWhatIsImportedAndWhatIsLeftBehind() {
        ImportPlan.Builder builder = new ImportPlan.Builder();
        builder.grade(new ImportPlan.Grade("vip", 10, List.of("default"), List.of(),
                Set.of("customperm.command.tp"), Set.of(), List.of(new ChatGrant(false, 10, "&6[VIP] ", 0)), java.util.Map.of(),
                List.of(new ScopedGrant("world=minecraft:the_nether", ScopedGrant.DENY, "customperm.command.tp"))));
        builder.player(new ImportPlan.Player("00000000-0000-0000-0000-000000000001", "Alex",
                List.of("vip"), List.of(), Set.of(), Set.of(), List.of(), java.util.Map.of(), List.of()));
        builder.imported(false);
        builder.imported(true);
        builder.imported(false);
        builder.world();
        builder.expose("tp");
        builder.track("ladder", List.of("default", "vip"));
        builder.contextual();
        builder.foreign();
        builder.other();

        ImportPlan plan = builder.build();
        assertEquals(1, plan.counts().groups());
        assertEquals(1, plan.counts().players());
        assertEquals(3, plan.counts().nodes());
        assertEquals(1, plan.counts().worlds());
        assertEquals(1, plan.counts().translated());
        assertEquals(1, plan.counts().commands());
        assertEquals(3, plan.counts().skipped());
        assertFalse(plan.isEmpty());

        String report = String.join("\n", plan.report());
        assertTrue(report.contains("1 group(s) become grades"), report);
        assertTrue(report.contains("3 node(s) imported, 1 of them translated"), report);
        assertTrue(report.contains("1 limited to a world, carried with it."), report);
        assertTrue(report.contains("3 entrie(s) are left behind"), report);
        assertTrue(report.contains("tp"), "the exposed command is named: " + report);
        assertTrue(report.contains("1 track(s) carried with their rungs: ladder."), report);
        assertFalse(report.contains("tracks)"), "tracks are no longer left behind: " + report);
    }

    @Test
    void aNodeAnotherModDeclaredIsKept() {
        List<String> declared = List.of("ftbchunks.claim", "ftbchunks.admin.bypass");
        assertEquals("ftbchunks.claim", ImportPlan.translate("ftbchunks.claim", declared));
        assertEquals("ftbchunks.*", ImportPlan.translate("ftbchunks.*", declared), "a wildcard over declared nodes");
        assertEquals("ftbchunks.admin.*", ImportPlan.translate("ftbchunks.admin.*", declared));
        assertNull(ImportPlan.translate("essentials.fly", declared), "a node no mod declared stays out");
        assertNull(ImportPlan.translate("ftbquests.*", declared));
        assertEquals("customperm.command.tp", ImportPlan.translate("minecraft.command.tp", declared),
                "the translations of CustomPerm's own nodes still apply");
    }

    @Test
    void theSameReasonIsSaidOnce() {
        ImportPlan.Builder builder = new ImportPlan.Builder();
        builder.note("Temporary entries are not imported.");
        builder.note("Temporary entries are not imported.");
        builder.note("Contextual entries are not imported.");
        assertEquals(2, builder.build().skipped().size());
    }

    @Test
    void anEmptyPlanSaysNothingIsLeftBehind() {
        ImportPlan plan = new ImportPlan.Builder().build();
        assertTrue(plan.isEmpty());
        assertTrue(String.join("\n", plan.report()).contains("Nothing is left behind."));
        assertTrue(ImportPlan.EMPTY.isEmpty());
    }
}
