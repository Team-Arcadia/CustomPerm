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
import com.arcadia.customperm.perm.PermissionResolver;
import com.arcadia.customperm.perm.Tristate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What an export to LuckPerms would write, read from a configuration. Pure Java, no Minecraft and no
 * LuckPerms: what is checked here is the plan, not the writing.
 */
class ExportPlanTest {

    private static final String PLAYER = "00000000-0000-0000-0000-0000000000bb";

    // --- carried as it is ---

    @Test
    void aGradeBecomesAGroupWithEverythingItSays() {
        GradesConfig config = new GradesConfig();
        grade(config, "base", 0, List.of(), List.of(), Set.of("customperm.command.time"), Set.of());
        grade(config, "vip", 42, List.of("base"), List.of(), Set.of("customperm.command.fly"),
                Set.of("customperm.command.ban"));

        ExportPlan plan = ExportPlan.of(config, "");

        ExportPlan.Group vip = group(plan, "vip");
        assertEquals(42, vip.weight());
        assertEquals(List.of("base"), vip.parents());
        assertEquals(Set.of("customperm.command.fly"), vip.allow(), "nothing is translated");
        assertEquals(Set.of("customperm.command.ban"), vip.deny());
        assertTrue(plan.refused().isEmpty());
        assertEquals(0, plan.dropped());
    }

    @Test
    void aDisplayNameIsExportedAndCountedAndAGradeWithoutOneWritesNone() {
        GradesConfig config = new GradesConfig();
        grade(config, "base", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        config.grades.get("vip").displayName = "Very Important";

        ExportPlan plan = ExportPlan.of(config, "");

        assertEquals("Very Important", group(plan, "vip").displayName());
        assertEquals("", group(plan, "base").displayName());
        assertEquals(1, plan.entries(), "the display name is the one entry written");
        assertEquals("Very Important", plan.asConfig().grades.get("vip").displayName);
        assertNull(plan.asConfig().grades.get("base").displayName);
    }

    @Test
    void aTemporaryParentIsExportedWithItsExpiryAndAnExpiredOneIsNot() {
        GradesConfig config = new GradesConfig();
        grade(config, "base", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "staff", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "old", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "vip", 0, List.of("base", "old"), List.of("staff"), Set.of(), Set.of());
        long later = com.arcadia.customperm.perm.Expiry.now() + 3600;
        config.grades.get("vip").parentExpiries.put("base", later);
        config.grades.get("vip").parentExpiries.put("old", com.arcadia.customperm.perm.Expiry.now() - 1);
        config.grades.get("vip").deniedParentExpiries.put("staff", later);

        ExportPlan plan = ExportPlan.of(config, "");

        ExportPlan.Group vip = group(plan, "vip");
        assertEquals(List.of("base"), vip.parents(), "a parent that ran out is not exported");
        assertEquals(later, vip.expiries().get("grade:base"));
        assertEquals(later, vip.expiries().get("refuse:staff"));
        GradesConfig after = plan.asConfig();
        assertEquals(later, after.grades.get("vip").parentExpiries.get("base"));
        assertEquals(later, after.grades.get("vip").deniedParentExpiries.get("staff"));
    }

    @Test
    void groupsComeInNameOrder() {
        GradesConfig config = new GradesConfig();
        grade(config, "zeta", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "alpha", 0, List.of(), List.of(), Set.of(), Set.of());

        assertEquals(List.of("alpha", "zeta"),
                ExportPlan.of(config, "").groups().stream().map(ExportPlan.Group::name).toList(),
                "two previews of the same file must read the same, and a failure must name a findable point");
    }

    @Test
    void aPlayerKeepsGradesRefusalsAndOwnNodes() {
        GradesConfig config = new GradesConfig();
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "staff", 0, List.of(), List.of(), Set.of(), Set.of());
        config.userGrades.put(PLAYER, new ArrayList<>(List.of("vip")));
        config.userDeniedGrades.put(PLAYER, new ArrayList<>(List.of("staff")));
        config.userPermissions.put(PLAYER, new LinkedHashSet<>(Set.of("customperm.command.home")));
        config.userDeniedPermissions.put(PLAYER, new LinkedHashSet<>(Set.of("customperm.command.tp")));

        ExportPlan.Player player = ExportPlan.of(config, "").players().get(0);

        assertEquals(PLAYER, player.uuid());
        assertEquals(List.of("vip"), player.grades());
        assertEquals(List.of("staff"), player.deniedGrades());
        assertEquals(Set.of("customperm.command.home"), player.allow());
        assertEquals(Set.of("customperm.command.tp"), player.deny());
    }

    // --- the default grade ---

    @Test
    void theDefaultGradeBecomesAParentOfTheDefaultGroup() {
        GradesConfig config = new GradesConfig();
        grade(config, "member", 0, List.of(), List.of(), Set.of(), Set.of());

        ExportPlan plan = ExportPlan.of(config, "member");

        assertEquals("member", plan.defaultGrade());
        assertTrue(plan.carriesDefault());
        assertEquals(2, plan.holders(), "the group, then the default group");
        assertTrue(String.join(" ", plan.report()).contains("parent of the LuckPerms default group"));
    }

    @Test
    void aDefaultGradeNamedDefaultIsThatGroupItself() {
        GradesConfig config = new GradesConfig();
        grade(config, "default", 0, List.of(), List.of(), Set.of("customperm.command.spawn"), Set.of());

        ExportPlan plan = ExportPlan.of(config, "default");

        assertFalse(plan.carriesDefault(), "the default group cannot be made its own parent");
        assertEquals(1, plan.holders());
    }

    // --- left out, and said ---

    @Test
    void aNameLuckPermsWouldRenameIsRefusedAndSoIsWhatNamesIt() {
        GradesConfig config = new GradesConfig();
        grade(config, "Admin", 0, List.of(), List.of(), Set.of("*"), Set.of());
        grade(config, "vip+", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "mod", 0, List.of("Admin"), List.of(), Set.of(), Set.of());
        config.userGrades.put(PLAYER, new ArrayList<>(List.of("Admin", "mod")));

        ExportPlan plan = ExportPlan.of(config, "Admin");

        assertEquals(List.of("Admin", "vip+"), plan.refused(), "LuckPerms lowercases, and rejects +");
        assertTrue(group(plan, "mod").parents().isEmpty(), "a parent that is not exported grants nothing there");
        assertEquals(List.of("mod"), plan.players().get(0).grades());
        assertEquals("", plan.defaultGrade());
        assertEquals(3, plan.dropped(), "the parent, the player's grade and the default grade");
        String report = String.join(" | ", plan.report());
        assertTrue(report.contains("Admin, vip+"), report);
        assertTrue(report.contains("3 entrie(s) left out"), report);
    }

    @Test
    void aParentThatIsNoGradeIsLeftOutRatherThanWrittenAsADanglingGroup() {
        GradesConfig config = new GradesConfig();
        grade(config, "vip", 0, List.of("ghost"), List.of(), Set.of(), Set.of());

        ExportPlan plan = ExportPlan.of(config, "");

        assertTrue(group(plan, "vip").parents().isEmpty());
        assertTrue(plan.notes().get(0).contains("ghost is not a grade"), plan.notes().toString());
    }

    @Test
    void aKeyThatIsNoPlayerIdentifierIsLeftOut() {
        GradesConfig config = new GradesConfig();
        config.userPermissions.put("Steve", new LinkedHashSet<>(Set.of("customperm.command.home")));

        ExportPlan plan = ExportPlan.of(config, "");

        assertTrue(plan.players().isEmpty());
        assertEquals(1, plan.dropped());
    }

    @Test
    void aPlayerLeftWithNothingIsNotWritten() {
        GradesConfig config = new GradesConfig();
        config.userGrades.put(PLAYER, new ArrayList<>(List.of("ghost")));

        assertTrue(ExportPlan.of(config, "").players().isEmpty(), "one load and one save for nothing");
    }

    @Test
    void anEmptyConfigurationHasNothingToExport() {
        ExportPlan plan = ExportPlan.of(new GradesConfig(), "");

        assertTrue(plan.isEmpty());
        assertTrue(plan.report().get(0).startsWith("Nothing to export"));
    }

    // --- the report ---

    @Test
    void theReportNamesTheGroupsLuckPermsAlreadyHasAndTheBackup() {
        GradesConfig config = new GradesConfig();
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "new", 0, List.of(), List.of(), Set.of(), Set.of());

        ExportPlan plan = ExportPlan.of(config, "").withExisting(Set.of("vip", "default", "other"));

        assertEquals(Set.of("vip"), plan.existing(), "only the groups this export writes");
        String report = String.join(" | ", plan.report());
        assertTrue(report.contains("Already in LuckPerms: vip."), report);
        assertTrue(report.contains("/lp export"), "the only way back must be said before, not after");
    }

    // --- the lockout guard reads the plan ---

    @Test
    void thePlanReadsBackAsTheGradesItCarries() {
        UUID admin = UUID.fromString(PLAYER);
        GradesConfig config = new GradesConfig();
        grade(config, "owner", 0, List.of(), List.of(), Set.of("customperm.*"), Set.of());
        grade(config, "Staff", 0, List.of(), List.of(), Set.of(), Set.of("customperm.admin"));
        config.userGrades.put(PLAYER, new ArrayList<>(List.of("owner", "Staff")));

        GradesConfig exported = ExportPlan.of(config, "").asConfig();

        assertEquals(Tristate.DENY, PermissionResolver.check(config, admin, "customperm.admin", null));
        assertEquals(Tristate.ALLOW, PermissionResolver.check(exported, admin, "customperm.admin", null),
                "a refused grade is not in LuckPerms, so its denial is not either");
    }

    // --- limited to a world ---

    @Test
    void entriesLimitedToAWorldAreCarriedAndReadBackTheSame() {
        GradesConfig config = new GradesConfig();
        grade(config, "base", 0, List.of(), List.of(), Set.of(), Set.of());
        GradesConfig.GradeScoped nether = new GradesConfig.GradeScoped();
        nether.deniedPermissions.add("customperm.command.home");
        config.grades.get("base").contexts.put("world=minecraft:the_nether", nether);
        GradesConfig.GradeScoped server = new GradesConfig.GradeScoped();
        server.permissions.add("customperm.command.fly");
        config.grades.get("base").contexts.put("server=lobby", server);
        GradesConfig.UserScoped end = new GradesConfig.UserScoped();
        end.grades.add("base");
        end.grades.add("ghost");
        config.userContexts.put(PLAYER, new java.util.HashMap<>(java.util.Map.of("world=minecraft:the_end", end)));

        ExportPlan plan = ExportPlan.of(config, "");

        assertEquals(List.of(new ScopedGrant("server=lobby", ScopedGrant.ALLOW, "customperm.command.fly"),
                        new ScopedGrant("world=minecraft:the_nether", ScopedGrant.DENY, "customperm.command.home")),
                group(plan, "base").scoped(), "a server context is written as LuckPerms' own server context");
        assertEquals(List.of(new ScopedGrant("world=minecraft:the_end", ScopedGrant.GRADE, "base")),
                plan.players().get(0).scoped(), "a grade that is not exported is not held anywhere");
        assertEquals(1, plan.dropped(), "only the grade that does not exist is left behind");

        GradesConfig back = plan.asConfig();
        UUID player = UUID.fromString(PLAYER);
        assertEquals(Tristate.DENY, PermissionResolver.check(back, player, "customperm.command.home", "base",
                com.arcadia.customperm.perm.Contexts.world("minecraft:the_nether")));
        assertTrue(back.userContexts.get(PLAYER).get("world=minecraft:the_end").grades.contains("base"));
    }

    @Test
    void parentsRefusalsAndPrefixesLimitedToAWorldAreCarriedToo() {
        GradesConfig config = new GradesConfig();
        grade(config, "base", 0, List.of(), List.of(), Set.of("customperm.command.home"), Set.of());
        grade(config, "staff", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        GradesConfig.GradeScoped nether = new GradesConfig.GradeScoped();
        nether.parents.add("base");
        nether.refused.add("staff");
        nether.prefixes.add(new GradesConfig.ChatEntry(5, "[Hot]", 0));
        config.grades.get("vip").contexts.put("world=minecraft:the_nether", nether);
        GradesConfig.GradeScoped server = new GradesConfig.GradeScoped();
        server.prefixes.add(new GradesConfig.ChatEntry(5, "[Lobby]", 0));
        config.grades.get("vip").contexts.put("server=lobby", server);
        GradesConfig.UserScoped end = new GradesConfig.UserScoped();
        end.refused.add("vip");
        config.userContexts.put(PLAYER, new java.util.HashMap<>(java.util.Map.of("world=minecraft:the_end", end)));

        ExportPlan plan = ExportPlan.of(config, "");

        ExportPlan.Group vip = group(plan, "vip");
        assertEquals(List.of(new ScopedGrant("world=minecraft:the_nether", ScopedGrant.PARENT, "base"),
                new ScopedGrant("world=minecraft:the_nether", ScopedGrant.REFUSED, "staff")), vip.scoped());
        assertEquals(List.of(new ChatGrant(false, 5, "[Lobby]", 0, "server=lobby"),
                        new ChatGrant(false, 5, "[Hot]", 0, "world=minecraft:the_nether")), vip.chat(),
                "a prefix limited to a server is written with LuckPerms' server context");
        assertEquals(List.of(new ScopedGrant("world=minecraft:the_end", ScopedGrant.REFUSED, "vip")),
                plan.players().get(0).scoped());

        GradesConfig back = plan.asConfig();
        UUID player = UUID.fromString(PLAYER);
        back.userGrades.put(PLAYER, new ArrayList<>(List.of("vip")));
        assertEquals(Tristate.ALLOW, PermissionResolver.check(back, player, "customperm.command.home", null,
                com.arcadia.customperm.perm.Contexts.world("minecraft:the_nether")), "the parent in the Nether came back");
        assertEquals(List.of("[Hot]"), PermissionResolver.prefixes(back, player, null,
                com.arcadia.customperm.perm.Contexts.world("minecraft:the_nether")));
    }

    @Test
    void aTemporaryEntryLimitedToAWorldIsCarriedWithItsExpiryAndAnExpiredOneIsNot() {
        GradesConfig config = new GradesConfig();
        grade(config, "base", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        long later = com.arcadia.customperm.perm.Expiry.now() + 3600;
        GradesConfig.GradeScoped nether = new GradesConfig.GradeScoped();
        nether.parents.add("base");
        nether.parentExpiries.put("base", later);
        nether.permissions.add("customperm.command.fly");
        nether.permissionExpiries.put("customperm.command.fly", com.arcadia.customperm.perm.Expiry.now() - 1);
        config.grades.get("vip").contexts.put("world=minecraft:the_nether", nether);

        ExportPlan plan = ExportPlan.of(config, "");

        assertEquals(List.of(new ScopedGrant("world=minecraft:the_nether", ScopedGrant.PARENT, "base", later)),
                group(plan, "vip").scoped(), "the expired node is left out, the parent keeps its expiry");
        GradesConfig back = plan.asConfig();
        assertEquals(java.util.Map.of("base", later),
                back.grades.get("vip").contexts.get("world=minecraft:the_nether").parentExpiries);
    }

    @Test
    void metaIsCarriedWithItsContextAndExpiryAndReadBack() {
        GradesConfig config = new GradesConfig();
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        long later = com.arcadia.customperm.perm.Expiry.now() + 3600;
        config.grades.get("vip").meta.put("rank", "gold");
        config.grades.get("vip").meta.put("gone", "x");
        config.grades.get("vip").metaExpiries.put("gone", com.arcadia.customperm.perm.Expiry.now() - 1);
        GradesConfig.GradeScoped nether = new GradesConfig.GradeScoped();
        nether.meta.put("rank", "fire");
        nether.metaExpiries.put("rank", later);
        config.grades.get("vip").contexts.put("world=minecraft:the_nether", nether);
        config.userMeta.put(PLAYER, new java.util.TreeMap<>(java.util.Map.of("homes", "3")));

        ExportPlan plan = ExportPlan.of(config, "");

        assertEquals(List.of(new MetaGrant("rank", "gold", 0, ""), new MetaGrant("rank", "fire", later, "world=minecraft:the_nether")),
                group(plan, "vip").meta(), "an expired value is left out");
        assertEquals(List.of(new MetaGrant("homes", "3", 0, "")), plan.players().get(0).meta(),
                "a player holding only meta is exported");
        GradesConfig back = plan.asConfig();
        assertEquals(java.util.Map.of("rank", "gold"), back.grades.get("vip").meta);
        assertEquals(java.util.Map.of("rank", later),
                back.grades.get("vip").contexts.get("world=minecraft:the_nether").metaExpiries);
        assertEquals(java.util.Map.of("homes", "3"), back.userMeta.get(PLAYER));
    }

    @Test
    void theSameEntryReadTwiceKeepsThePermanentOneOrTheLongerOne() {
        String nether = "world=minecraft:the_nether";
        assertEquals(List.of(new ScopedGrant(nether, ScopedGrant.ALLOW, "a", 0),
                        new ScopedGrant(nether, ScopedGrant.ALLOW, "b", 200)),
                ScopedGrant.merged(List.of(new ScopedGrant(nether, ScopedGrant.ALLOW, "a", 100),
                        new ScopedGrant(nether, ScopedGrant.ALLOW, "b", 100),
                        new ScopedGrant(nether, ScopedGrant.ALLOW, "a", 0),
                        new ScopedGrant(nether, ScopedGrant.ALLOW, "b", 200))));
    }

    // --- tracks ---

    @Test
    void aTrackKeepsItsExportedRungsInOrder() {
        GradesConfig config = new GradesConfig();
        grade(config, "member", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "vip", 0, List.of(), List.of(), Set.of(), Set.of());
        grade(config, "Staff", 0, List.of(), List.of(), Set.of(), Set.of());
        config.tracks.put("ladder", new ArrayList<>(List.of("member", "Staff", "vip")));
        config.tracks.put("Bad", new ArrayList<>(List.of("member")));

        ExportPlan plan = ExportPlan.of(config, "");

        assertEquals(List.of(new ExportPlan.Track("ladder", List.of("member", "vip"))), plan.tracks(),
                "a grade that is not exported leaves the ladder, the rest keeps its order");
        assertEquals(2, plan.dropped(), "the refused rung and the refused track name are both counted");
        assertEquals(List.of("member", "vip"), plan.asConfig().tracks.get("ladder"));
        assertTrue(String.join("\n", plan.report()).contains("1 track(s) written"), plan.report().toString());
    }

    // --- helpers ---

    private static void grade(GradesConfig config, String name, int weight, List<String> parents,
                              List<String> deniedParents, Set<String> allow, Set<String> deny) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grade.weight = weight;
        grade.parents = new ArrayList<>(parents);
        grade.deniedParents = new ArrayList<>(deniedParents);
        grade.permissions = new java.util.HashSet<>(allow);
        grade.deniedPermissions = new java.util.HashSet<>(deny);
        config.grades.put(name, grade);
    }

    private static ExportPlan.Group group(ExportPlan plan, String name) {
        return plan.groups().stream().filter(g -> g.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No group " + name + " in " + plan.groups()));
    }
}
