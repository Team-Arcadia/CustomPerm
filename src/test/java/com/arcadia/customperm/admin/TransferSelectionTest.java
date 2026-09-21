/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.config.GradesConfig;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an import or an export carries once the admin has chosen who and what. */
class TransferSelectionTest {

    private static final String STEVE = "00000000-0000-0000-0000-000000000001";
    private static final String ALEX = "00000000-0000-0000-0000-000000000002";

    /** Two groups, vip inheriting base; Steve in vip with a prefix, a meta, a timed node and one in the Nether. */
    private static ImportPlan plan() {
        ImportPlan.Grade base = new ImportPlan.Grade("base", 1, List.of(), List.of(), Set.of("customperm.command.home"),
                Set.of(), List.of(), Map.of(), List.of(), List.of(), "");
        ImportPlan.Grade vip = new ImportPlan.Grade("vip", 10, List.of("base"), List.of(),
                Set.of("customperm.command.fly", "customperm.command.tp"), Set.of(),
                List.of(new ChatGrant(false, 10, "&6[VIP] ", 0, "")),
                Map.of("allow:customperm.command.tp", 99L),
                List.of(new ScopedGrant("world=minecraft:the_nether", ScopedGrant.ALLOW, "customperm.command.seed", 0)),
                List.of(new MetaGrant("homes", "3", 0, "")), "Very Important");
        ImportPlan.Player steve = new ImportPlan.Player(STEVE, "Steve", List.of("vip"), List.of(), Set.of(), Set.of(),
                List.of(), Map.of(), List.of(), List.of());
        ImportPlan.Player alex = new ImportPlan.Player(ALEX, "Alex", List.of("base"), List.of(), Set.of(), Set.of(),
                List.of(), Map.of(), List.of(), List.of());
        return new ImportPlan(List.of(base, vip), List.of(steve, alex), Map.of("ranks", List.of("base", "vip")),
                Set.of("fly", "home", "tp", "seed"), List.of(), ImportPlan.Counts.NONE);
    }

    private static TransferSelection.Candidates candidates() {
        return new TransferSelection.Candidates(List.of("base", "vip"), Map.of(STEVE, "Steve", ALEX, "Alex"), List.of("ranks"));
    }

    @Test
    void everythingLeavesThePlanAsItIs() {
        ImportPlan plan = plan();
        assertSame(plan, TransferSelection.ALL.filter(plan, Set.of()));
    }

    @Test
    void aGroupLeftOutIsNotImportedAndAParentNoOneHasIsDropped() {
        ImportPlan filtered = TransferSelection.ALL.withGroups(Set.of("vip")).filter(plan(), Set.of());
        assertEquals(List.of("vip"), filtered.grades().stream().map(ImportPlan.Grade::name).toList());
        assertEquals(List.of(), filtered.grades().get(0).parents(), "base is neither selected nor here: dropped.");
        assertTrue(String.join(" ", filtered.skipped()).contains("base (parent of vip)"));
        // Alex's only grade is not imported and not here either.
        assertEquals(List.of(), filtered.players().stream().filter(p -> p.uuid().equals(ALEX)).findFirst().orElseThrow().grades());
    }

    @Test
    void aParentAlreadyHereIsKept() {
        ImportPlan filtered = TransferSelection.ALL.withGroups(Set.of("vip")).filter(plan(), Set.of("base"));
        assertEquals(List.of("base"), filtered.grades().get(0).parents());
    }

    @Test
    void kindsLeftOutAreNotCarried() {
        TransferSelection selection = TransferSelection.ALL.withKind(TransferSelection.Kind.CHAT, false)
                .withKind(TransferSelection.Kind.META, false)
                .withKind(TransferSelection.Kind.TEMPORARY, false)
                .withKind(TransferSelection.Kind.CONTEXTUAL, false);
        ImportPlan.Grade vip = selection.filter(plan(), Set.of()).grades().get(1);
        assertEquals(List.of(), vip.chat());
        assertEquals(List.of(), vip.meta());
        assertEquals(List.of(), vip.scoped());
        assertEquals(Set.of("customperm.command.fly"), vip.allow(), "The timed node goes with temporary entries.");
        assertEquals("Very Important", vip.displayName(), "A group's name and weight always come with it.");
    }

    @Test
    void onlyTheCommandsTheKeptNodesNameAreExposed() {
        ImportPlan filtered = TransferSelection.ALL.withGroups(Set.of("base")).filter(plan(), Set.of());
        assertEquals(Set.of("home"), filtered.exposeCommands());
        assertEquals(Set.of(), TransferSelection.ALL.withKind(TransferSelection.Kind.COMMANDS, false)
                .filter(plan(), Set.of()).exposeCommands());
    }

    @Test
    void playersAndTracksFollowTheirSelection() {
        ImportPlan filtered = TransferSelection.ALL.withPlayers(Set.of(STEVE)).withTracks(Set.of()).filter(plan(), Set.of());
        assertEquals(List.of(STEVE), filtered.players().stream().map(ImportPlan.Player::uuid).toList());
        assertTrue(filtered.tracks().isEmpty());
        assertTrue(filtered.skipped().get(0).startsWith("Selection: every group, 1 of 2 players, 0 of 1 track"));
    }

    @Test
    void editingTakesNamesAllNoneAndRefusesTypos() {
        TransferSelection.Edit byName = TransferSelection.ALL.edit("players", "set", "steve", candidates());
        assertNull(byName.problem());
        assertEquals(Set.of(STEVE), byName.next().players());
        TransferSelection.Edit added = byName.next().edit("players", "add", ALEX, candidates());
        assertNull(added.next().players(), "Every candidate chosen is every one.");
        TransferSelection.Edit removed = TransferSelection.ALL.edit("groups", "remove", "vip", candidates());
        assertEquals(Set.of("base"), removed.next().groups());
        assertEquals(Set.of(), TransferSelection.ALL.edit("tracks", "set", "none", candidates()).next().tracks());
        TransferSelection.Edit typo = TransferSelection.ALL.edit("groups", "set", "vip, bsae", candidates());
        assertNotNull(typo.problem());
        assertSame(TransferSelection.ALL, typo.next(), "A refused edit changes nothing.");
        TransferSelection.Edit kinds = TransferSelection.ALL.edit("kinds", "set", "nodes,contexts", candidates());
        assertEquals(EnumSet.of(TransferSelection.Kind.NODES, TransferSelection.Kind.CONTEXTUAL), kinds.next().kinds());
        assertNotNull(TransferSelection.ALL.edit("kinds", "add", "all", candidates()).problem(), "all goes with set.");
    }

    @Test
    void anExportKeepsReferencesToGroupsLuckPermsHasOrThatGoToo() {
        GradesConfig config = new GradesConfig();
        GradesConfig.Grade base = new GradesConfig.Grade();
        base.permissions.add("customperm.command.home");
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.parents.add("base");
        config.grades.put("base", base);
        config.grades.put("vip", vip);
        config.tracks.put("ranks", new java.util.ArrayList<>(List.of("base", "vip")));
        ExportPlan plan = ExportPlan.of(config, "base");

        ExportPlan onlyVip = TransferSelection.ALL.withGroups(Set.of("vip")).filter(plan);
        assertEquals(List.of(), onlyVip.groups().get(0).parents(), "base is neither exported nor in LuckPerms.");
        assertEquals(List.of("vip"), onlyVip.tracks().get(0).groups(), "A rung LuckPerms will not have is left out.");
        assertFalse(onlyVip.carriesDefault(), "The default grade stays out when it is not written.");

        ExportPlan withBaseThere = TransferSelection.ALL.withGroups(Set.of("vip")).filter(plan.withExisting(Set.of("base")));
        assertEquals(List.of("base"), withBaseThere.groups().get(0).parents());
    }
}
