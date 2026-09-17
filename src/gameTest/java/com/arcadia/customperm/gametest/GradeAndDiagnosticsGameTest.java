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
import com.arcadia.customperm.gametest.CommandExposureGameTest.Exposure;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Grade management commands and the internal resolver seen from a connected player (procedure 1.0.5
 * A3, audit retest W01-W04), plus the diagnostic commands (A9.1-A9.3). Grade and wildcard tests are
 * internal-only: under LuckPerms, grade commands are refused and LuckPerms resolves its own wildcards.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class GradeAndDiagnosticsGameTest {

    private static final String TEMPLATE = "empty_3x3";

    /** A3.1: duplicate creation and deletion of a missing grade are refused. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void gradeLifecycleRefusesDuplicatesAndMissing(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try {
            expect(ServerCommands.run(server, "customperm grade create cp_gt_life"), "Created grade cp_gt_life");
            expect(ServerCommands.run(server, "customperm grade create cp_gt_life"), "Grade already exists: cp_gt_life");
            expect(ServerCommands.run(server, "customperm grade list"), "cp_gt_life");
            expect(ServerCommands.run(server, "customperm grade delete cp_gt_life"), "Deleted grade cp_gt_life");
            expect(ServerCommands.run(server, "customperm grade delete cp_gt_life"), "No such grade: cp_gt_life");
        } finally {
            CustomPerm.configManager.getGrades().grades.remove("cp_gt_life");
        }
        helper.succeed();
    }

    /** B1.6: with LuckPerms active, grade commands point to /lp instead of silently using grades.json. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void gradeCommandsDeferToLuckPerms(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        List<String> lines = ServerCommands.run(helper.getLevel().getServer(), "customperm grade create cp_gt_lp");
        expect(lines, "use /lp instead");
        if (CustomPerm.configManager.getGrades().grades.containsKey("cp_gt_lp"))
            fail("A grade was created in grades.json while LuckPerms is active.");
        helper.succeed();
    }

    /** A3.2 + A3.5: grades assigned by command add up, and unassigning resyncs the player's command tree. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void gradesUnionAndUnassignResyncs(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_union", 0);
             Exposure ignored = Exposure.of(server, "gamemode")) {
            ServerCommands.run(server, "customperm grade create cp_gt_vip");
            ServerCommands.run(server, "customperm grade create cp_gt_builder");
            ServerCommands.run(server, "customperm grade addperm cp_gt_vip customperm.command.gamemode");
            ServerCommands.run(server, "customperm grade addperm cp_gt_builder customperm.command.give");
            ServerCommands.run(server, "customperm grade assign cp_g_union cp_gt_vip");
            ServerCommands.run(server, "customperm grade assign cp_g_union cp_gt_builder");
            expect(ServerCommands.run(server, "customperm test cp_g_union customperm.command.gamemode"), "-> GRANTED");
            expect(ServerCommands.run(server, "customperm test cp_g_union customperm.command.give"), "-> GRANTED");
            if (!player.canUse("gamemode")) fail("Grade-granted /gamemode is not usable.");

            player.clearReceived();
            ServerCommands.run(server, "customperm grade unassign cp_g_union cp_gt_vip");
            if (player.commandTreesReceived() < 1) fail("Unassigning a grade must resend the player's command tree.");
            if (player.canUse("gamemode")) fail("/gamemode still usable after unassigning the grade that granted it.");
            expect(ServerCommands.run(server, "customperm test cp_g_union customperm.command.gamemode"), "-> DENIED");
        } finally {
            var grades = CustomPerm.configManager.getGrades();
            grades.grades.remove("cp_gt_vip");
            grades.grades.remove("cp_gt_builder");
            grades.userGrades.values().forEach(list -> list.removeIf(g -> g.startsWith("cp_gt_")));
            grades.userGrades.values().removeIf(List::isEmpty);
        }
        helper.succeed();
    }

    /** A3.3 / W03: a DENY on any ancestor wins over an explicit ALLOW, across grades. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ancestorDenyBeatsExplicitAllow(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_deny", 0);
             Exposure ignored = Exposure.of(helper.getLevel().getServer(), "gamemode");
             Grants allow = Grants.allow(player, "customperm.command.gamemode")) {
            if (!player.canUse("gamemode")) fail("Setup: explicit ALLOW did not open /gamemode.");
            Grants.deny(player, "customperm.*");
            if (player.canUse("gamemode")) fail("customperm.* in DENY must beat an explicit ALLOW.");
        }
        helper.succeed();
    }

    /** A3.4 / W01, W02, W04: every wildcard form, end to end, with negative controls. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void wildcardFormsOpenOnlyTheirScope(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_wild", 0);
             Exposure ignored = Exposure.of(server, "gamemode")) {
            if (player.canUse("gamemode")) fail("W02: without any grade the exposed command must stay closed.");
            for (String wildcard : List.of("customperm.*", "customperm.command.*", "*")) {
                try (Grants grants = Grants.allow(player, wildcard)) {
                    if (!player.canUse("gamemode")) fail(wildcard + " did not open exposed /gamemode.");
                }
            }
            try (Grants grants = Grants.allow(player, "customperm.alias.*")) {
                if (player.canUse("gamemode")) fail("customperm.alias.* must not open a command.");
                expect(ServerCommands.run(server, "customperm test cp_g_wild customperm.alias.anything"), "-> GRANTED");
            }
        }
        helper.succeed();
    }

    /** A9.1: the status header, backend and fallback mode lines. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void statusReportsBackendAndFallback(GameTestHelper helper) {
        List<String> lines = ServerCommands.run(helper.getLevel().getServer(), "customperm status");
        expect(lines, "=== CustomPerm Status ===");
        expect(lines, "Backend            : " + CustomPerm.backendLabel());
        expect(lines, "LP fallback mode   : " + CustomPerm.configManager.getSettings().luckPermsFallbackMode);
        helper.succeed();
    }

    /** A9.2: debug for an online player, then the degraded report for an offline name. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void debugReportsOnlineAndOffline(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer ignored = TestPlayer.join(helper.getLevel(), "cp_g_debug", 0)) {
            List<String> online = ServerCommands.run(server, "customperm debug cp_g_debug gamemode");
            expect(online, "=== Debug for /gamemode (cp_g_debug) [backend: " + CustomPerm.backendLabel() + "] ===");
            expect(online, "Command exists in dispatcher : true");
            if (ServerCommands.contains(online, "MISMATCH")) fail("Debug reported a wrapper mismatch: " + online);
        }
        List<String> offline = ServerCommands.run(server, "customperm debug cp_g_offline gamemode");
        expect(offline, "[OFFLINE]");
        helper.succeed();
    }

    /** A9.3: test reports GRANTED / DENIED, scan lists roots and filters by pattern. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void testAndScanReport(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_scan", 0);
             Grants ignored = Grants.allow(player, "customperm.command.gamemode")) {
            expect(ServerCommands.run(server, "customperm test cp_g_scan customperm.command.gamemode"),
                    "cp_g_scan :: customperm.command.gamemode -> GRANTED");
            expect(ServerCommands.run(server, "customperm test cp_g_scan customperm.command.give"), "-> DENIED");
        }
        List<String> all = ServerCommands.run(server, "customperm scan");
        expect(all, "/gamemode");
        expect(all, "[ MOD  ] /customperm");
        List<String> filtered = ServerCommands.run(server, "customperm scan game");
        expect(filtered, "/gamemode");
        if (ServerCommands.contains(filtered, "/teleport")) fail("scan game must not list /teleport: " + filtered);
        helper.succeed();
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) fail("Expected output containing '" + fragment + "', got: " + lines);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
