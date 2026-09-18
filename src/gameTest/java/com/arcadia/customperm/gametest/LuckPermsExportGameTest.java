/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.ExportPlan;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.LuckPermsTestSupport;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.ImportData;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.perm.lp.LuckPermsExport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static com.arcadia.customperm.gametest.support.LuckPermsTestSupport.apply;

/**
 * The export, against a real LuckPerms: what lands in its groups and users, what adding keeps and
 * replacing clears, and the page that drives it. LuckPerms mode only, it being what is written.
 *
 * <p>Each test has its own batch. The first gives LuckPerms' default group a parent every test player
 * would inherit; the second swaps the grades the whole server reads for the length of the test.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class LuckPermsExportGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String BASE = "cp_x_base";
    private static final String VIP = "cp_x_vip";
    private static final String PAGE_GRADE = "cp_x_page";
    private static final String TRACK = "cp_x_ladder";
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "customperm_lp_export_write")
    public static void exportWritesTheGradesAddingThenReplacing(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        try {
            // LuckPerms already has the vip group, with a prefix, another mod's node, and a node set the
            // other way from the grade: what adding must keep and replacing must clear or keep.
            apply(LpEditOp.GROUP_CREATE, VIP);
            apply(LpEditOp.GROUP_PREFIX_SET, VIP, "10", "[VIP]", "");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "essentials.fly", "true", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.fly", "false", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.kick", "true", "", "0");

            GradesConfig config = new GradesConfig();
            grade(config, BASE, 0, List.of(), Set.of("customperm.command.time"), Set.of("customperm.command.ban"));
            config.grades.get(BASE).prefixes.add(new GradesConfig.ChatEntry(0, "&7[Base] ", 0));
            config.grades.get(BASE).prefixes.add(new GradesConfig.ChatEntry(20, "[Temp] ",
                    com.arcadia.customperm.perm.Expiry.now() + 3600));
            config.grades.get(BASE).permissions.add("customperm.command.list");
            config.grades.get(BASE).permissionExpiries.put("customperm.command.list",
                    com.arcadia.customperm.perm.Expiry.now() + 3600);
            config.grades.get(BASE).permissions.add("customperm.command.dead");
            config.grades.get(BASE).permissionExpiries.put("customperm.command.dead",
                    com.arcadia.customperm.perm.Expiry.now() - 1);
            grade(config, VIP, 42, List.of(BASE), Set.of("customperm.command.fly"), Set.of());
            config.grades.get(VIP).parentExpiries.put(BASE, com.arcadia.customperm.perm.Expiry.now() + 3600);
            grade(config, "cp_X_Bad", 0, List.of(), Set.of(), Set.of());
            config.userGrades.put(USER.toString(), new ArrayList<>(List.of(VIP)));
            config.userPermissions.put(USER.toString(), new LinkedHashSet<>(Set.of("customperm.command.home")));
            config.userPrefixEntries.put(USER.toString(), new ArrayList<>(List.of(new GradesConfig.ChatEntry(100, "[Me] ", 0))));
            GradesConfig.Scoped nether = new GradesConfig.Scoped();
            nether.deniedPermissions.add("customperm.command.time");
            config.grades.get(BASE).contexts.put("world=minecraft:the_nether", nether);
            GradesConfig.UserScoped end = new GradesConfig.UserScoped();
            end.grades.add(BASE);
            config.userContexts.put(USER.toString(), new java.util.HashMap<>(java.util.Map.of("world=minecraft:the_end", end)));
            config.tracks.put(TRACK, new ArrayList<>(List.of(BASE, VIP)));

            ExportPlan plan = ExportPlan.of(config, BASE)
                    .withExisting(LuckPermsTestSupport.await(LuckPermsExport.existingGroups()));
            if (!plan.existing().equals(Set.of(VIP))) fail("The report must name the group LuckPerms has: " + plan.existing());
            if (!plan.refused().equals(List.of("cp_X_Bad"))) fail("A name LuckPerms would rename must be refused: " + plan.refused());

            List<Integer> told = new ArrayList<>();
            ExportPlan.Outcome added = LuckPermsTestSupport.await(LuckPermsExport.write(plan, false, told::add));
            if (!added.complete()) fail("Adding stopped at " + added.stoppedAt() + ": " + added.error());
            if (!told.equals(List.of(1, 2, 3, 4, 5))) fail("Progress must count every holder, in order: " + told);
            if (added.kept() != 1) fail("The node LuckPerms set the other way must be kept and counted: " + added);

            List<String> base = LuckPermsTestSupport.groupNodes(BASE);
            expect(base, "customperm.command.time=true", "A grade's node must arrive as it is");
            expect(base, "customperm.command.ban=false", "A denied node must arrive set to false");
            expect(base, "prefix.0.&7[Base] =true", "A grade's prefix must arrive at its priority");
            expect(base, "prefix.20.[Temp] =true@expiring", "A temporary prefix must arrive temporary");
            expect(base, "customperm.command.list=true@expiring", "A temporary node must arrive temporary");
            expect(base, "customperm.command.time=false[world=the_nether]",
                    "A node limited to a world must arrive with LuckPerms' world context");
            if (base.stream().anyMatch(node -> node.startsWith("customperm.command.dead")))
                fail("A node that has already expired must not be exported: " + base);
            List<String> vip = LuckPermsTestSupport.groupNodes(VIP);
            expect(vip, "group." + BASE + "=true@expiring", "A temporary parent must arrive as a temporary inheritance node");
            expect(vip, "weight.42=true", "The weight must arrive on a group that had none");
            expect(vip, "customperm.command.fly=false", "Adding must keep LuckPerms' value");
            expect(vip, "customperm.command.kick=true", "Adding must keep what LuckPerms holds");
            expect(vip, "prefix.10.[VIP]=true", "Adding must keep the prefix");
            expect(LuckPermsTestSupport.groupNodes(ExportPlan.LP_DEFAULT), "group." + BASE + "=true",
                    "The default grade must become a parent of the default group");
            List<String> user = LuckPermsTestSupport.userNodes(USER);
            expect(user, "group." + VIP + "=true", "The player's grade must arrive as a parent");
            expect(user, "customperm.command.home=true", "The player's own node must arrive");
            expect(user, "group." + BASE + "=true[world=the_end]", "A grade held in one world must arrive there");
            expect(user, "prefix.100.[Me] =true", "The player's own prefix must arrive at its priority");
            if (LuckPermsTestSupport.groupExists("cp_x_bad")) fail("A refused grade must not be written under another name.");
            if (!LuckPermsTestSupport.trackGroups(TRACK).equals(List.of(BASE, VIP)))
                fail("The track must arrive with its groups in order: " + LuckPermsTestSupport.trackGroups(TRACK));

            ExportPlan.Outcome replaced = LuckPermsTestSupport.await(LuckPermsExport.write(plan, true, written -> { }));
            if (!replaced.complete()) fail("Replacing stopped at " + replaced.stoppedAt() + ": " + replaced.error());
            vip = LuckPermsTestSupport.groupNodes(VIP);
            expect(vip, "customperm.command.fly=true", "Replacing must write the grade's value");
            if (vip.contains("customperm.command.kick=true")) fail("Replacing must clear the customperm nodes: " + vip);
            expect(vip, "essentials.fly=true", "Replacing must keep the nodes of other mods");
            expect(vip, "prefix.10.[VIP]=true", "Replacing must keep a prefix the grade does not set");
            expect(vip, "group." + BASE + "=true@expiring", "Replacing must write the parent back, still temporary");
            expect(LuckPermsTestSupport.userNodes(USER), "group.default=true",
                    "Replacing must leave a player in the default group");
        } finally {
            LuckPermsTestSupport.clearGroupNodes(ExportPlan.LP_DEFAULT, List.of("group." + BASE));
            LuckPermsTestSupport.clearNodes(USER, List.of("group." + VIP, "group." + BASE, "customperm.command.home",
                    "prefix.100.[Me] "));
            LuckPermsTestSupport.cleanup(List.of(BASE, VIP), List.of(TRACK));
        }
        helper.succeed();
    }

    /** The export tab: the refusals, reading then exporting, the lockout guard, and a spent preview. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "customperm_lp_export_page")
    public static void exportPageReadsThenExports(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        Saved saved = Saved.of(grades, settings);
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_x_owner", 4);
             TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_x_reader", 2)) {
            // What the server exports is its whole configuration: only this grade, for the test's length.
            saved.clear(grades, settings);
            grade(grades, PAGE_GRADE, 0, List.of(), Set.of("customperm.command.weather"), Set.of());

            // The area check answers first, then the composite one: exporting asks for two nodes.
            reader.clearReceived();
            exportAct(reader, GuiAction.EXPORT_PREVIEW);
            expectResult(reader, "FAIL: You do not have customperm.manage.luckperms.");
            try (Grants oneArea = Grants.allow(reader, "customperm.manage.luckperms")) {
                reader.clearReceived();
                exportAct(reader, GuiAction.EXPORT_PREVIEW);
                expectResult(reader, "FAIL: Exporting needs customperm.manage.grades and customperm.manage.luckperms.");
            }

            owner.clearReceived();
            exportAct(owner, GuiAction.EXPORT_APPLY, "merge");
            expectResult(owner, "FAIL: Read the grades first");

            owner.clearReceived();
            exportAct(owner, GuiAction.EXPORT_PREVIEW);
            expectResult(owner, "OK: Reading the grades");
            ImportData page = awaitPage(server, owner, data -> data.export().previewed());
            if (page.export().report().isEmpty()) fail("The refreshed page must carry the report: " + page);
            if (LuckPermsTestSupport.groupExists(PAGE_GRADE)) fail("Reading must not write anything.");

            owner.clearReceived();
            exportAct(owner, GuiAction.EXPORT_APPLY, "sideways");
            expectResult(owner, "FAIL: Malformed request for EXPORT_APPLY.");

            // Replacing would clear what gives the owner their access, and the grades do not give it back.
            owner.clearReceived();
            exportAct(owner, GuiAction.EXPORT_APPLY, "replace");
            expectResult(owner, "FAIL: Refused: once in LuckPerms");

            owner.clearReceived();
            exportAct(owner, GuiAction.EXPORT_APPLY, "merge");
            List<String> started = results(owner);
            if (started.size() != 1 || !started.get(0).startsWith("OK: Exporting"))
                fail("Unexpected start: " + started);
            long deadline = System.currentTimeMillis() + 10000;
            server.managedBlock(() -> results(owner).size() > 1 || System.currentTimeMillis() > deadline);
            List<String> results = results(owner);
            if (results.size() < 2 || !results.get(1).startsWith("OK: Exported 1 group(s) and 0 player(s)"))
                fail("The export must report back when it ends: " + results);
            if (!LuckPermsTestSupport.groupNodes(PAGE_GRADE).contains("customperm.command.weather=true"))
                fail("The export did not write what it said it would.");

            // The preview is spent: confirming again asks for a new read rather than writing twice.
            owner.clearReceived();
            exportAct(owner, GuiAction.EXPORT_APPLY, "merge");
            expectResult(owner, "FAIL: Read the grades first");
        } finally {
            saved.restore(grades, settings);
            LuckPermsTestSupport.cleanup(List.of(PAGE_GRADE), List.of());
        }
        helper.succeed();
    }

    /** The grades and default grade the server had, put back once the test is done with them. */
    private record Saved(Map<String, GradesConfig.Grade> grades, Map<String, List<String>> userGrades,
                         Map<String, List<String>> userDeniedGrades, Map<String, Set<String>> userPermissions,
                         Map<String, Set<String>> userDeniedPermissions, String defaultGrade) {

        static Saved of(GradesConfig config, SettingsConfig settings) {
            return new Saved(new HashMap<>(config.grades), new HashMap<>(config.userGrades),
                    new HashMap<>(config.userDeniedGrades), new HashMap<>(config.userPermissions),
                    new HashMap<>(config.userDeniedPermissions), settings.defaultGrade);
        }

        void clear(GradesConfig config, SettingsConfig settings) {
            config.grades.clear();
            config.userGrades.clear();
            config.userDeniedGrades.clear();
            config.userPermissions.clear();
            config.userDeniedPermissions.clear();
            settings.defaultGrade = "";
        }

        void restore(GradesConfig config, SettingsConfig settings) {
            clear(config, settings);
            config.grades.putAll(grades);
            config.userGrades.putAll(userGrades);
            config.userDeniedGrades.putAll(userDeniedGrades);
            config.userPermissions.putAll(userPermissions);
            config.userDeniedPermissions.putAll(userDeniedPermissions);
            settings.defaultGrade = defaultGrade;
        }
    }

    /**
     * The page the server pushes once LuckPerms has answered. The answer is scheduled on the server
     * thread, the one running this test, so the wait drains that queue rather than sleeping on it.
     */
    private static ImportData awaitPage(MinecraftServer server, TestPlayer player, Predicate<ImportData> ready) {
        long deadline = System.currentTimeMillis() + 5000;
        server.managedBlock(() -> page(player, ready) != null || System.currentTimeMillis() > deadline);
        ImportData data = page(player, ready);
        if (data == null) throw new GameTestAssertException("LuckPerms did not answer the preview within 5s");
        return data;
    }

    private static ImportData page(TestPlayer player, Predicate<ImportData> ready) {
        List<GuiPagePayload> pages = player.payloads(GuiPagePayload.class);
        for (int i = pages.size() - 1; i >= 0; i--) {
            if (pages.get(i).data() instanceof ImportData data && ready.test(data)) return data;
        }
        return null;
    }

    private static void exportAct(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.IMPORT.id()),
                player.payloadContext());
    }

    private static void expectResult(TestPlayer player, String prefix) {
        List<String> results = results(player);
        if (results.size() != 1 || !results.get(0).startsWith(prefix))
            throw new GameTestAssertException("Expected one result starting with '" + prefix + "', got " + results);
    }

    private static List<String> results(TestPlayer player) {
        return player.payloads(GuiActionResultPayload.class).stream()
                .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message())
                .toList();
    }

    private static void grade(GradesConfig config, String name, int weight, List<String> parents,
                              Set<String> allow, Set<String> deny) {
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grade.weight = weight;
        grade.parents = new ArrayList<>(parents);
        grade.permissions = new HashSet<>(allow);
        grade.deniedPermissions = new HashSet<>(deny);
        config.grades.put(name, grade);
    }

    private static void expect(List<String> nodes, String node, String message) {
        if (!nodes.contains(node)) fail(message + ": " + node + " not in " + nodes);
    }

    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }
}
