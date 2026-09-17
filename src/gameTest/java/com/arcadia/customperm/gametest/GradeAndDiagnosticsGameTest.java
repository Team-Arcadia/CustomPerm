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
import com.arcadia.customperm.config.GradesConfig;
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

    /**
     * A3.3, revised for 1.1.0: the most specific entry wins, like LuckPerms. An explicit ALLOW beats a DENY
     * on an ancestor (this used to be refused: W03), and a DENY still wins at the same level.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void mostSpecificEntryWins(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_deny", 0);
             Exposure gamemode = Exposure.of(server, "gamemode");
             Exposure time = Exposure.of(server, "time")) {
            try (Grants allow = Grants.allow(player, "customperm.command.gamemode");
                 Grants deny = Grants.deny(player, "customperm.*")) {
                if (!player.canUse("gamemode")) fail("An exact ALLOW must beat customperm.* in DENY.");
                if (player.canUse("time")) fail("customperm.* in DENY must still close what nothing more specific allows.");
            }
            try (Grants allow = Grants.allow(player, "customperm.command.gamemode", "customperm.command.time");
                 Grants deny = Grants.deny(player, "customperm.command.time")) {
                if (player.canUse("time")) fail("A DENY on the same node must beat the ALLOW.");
                if (!player.canUse("gamemode")) fail("A DENY on another node must not close /gamemode.");
            }
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

    /**
     * Grade weight: between two grades covering the same node just as specifically, the heaviest decides,
     * and the command that sets it applies live. A DENY still wins between equal weights.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void weightBreaksTiesBetweenGrades(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_weight", 0);
             Exposure gamemode = Exposure.of(server, "gamemode")) {
            expect(ServerCommands.run(server, "customperm grade create cp_gt_w_allow"), "Created grade");
            expect(ServerCommands.run(server, "customperm grade create cp_gt_w_deny"), "Created grade");
            grades.grades.get("cp_gt_w_allow").permissions.add("customperm.command.gamemode");
            grades.grades.get("cp_gt_w_deny").deniedPermissions.add("customperm.command.gamemode");
            grades.userGrades.put(player.uuid().toString(),
                    new java.util.ArrayList<>(List.of("cp_gt_w_allow", "cp_gt_w_deny")));

            if (player.canUse("gamemode")) fail("Equal weights must keep the DENY winning.");

            expect(ServerCommands.run(server, "customperm grade weight cp_gt_w_allow 10"),
                    "Weight of cp_gt_w_allow set to 10");
            if (!player.canUse("gamemode")) fail("The heavier grade must win the tie.");

            expect(ServerCommands.run(server, "customperm grade weight cp_gt_w_deny 10"), "set to 10");
            if (player.canUse("gamemode")) fail("Back to equal weights, the DENY must win again.");

            expect(ServerCommands.run(server, "customperm grade weight cp_gt_w_allow 10"), "already weighs 10");
            expect(ServerCommands.run(server, "customperm grade weight cp_gt_w_missing 5"), "No such grade");
            expect(ServerCommands.run(server, "customperm grade list"), "cp_gt_w_allow (weight 10)");

            // A more specific node in a weightless grade still beats a heavy wildcard DENY.
            grades.grades.get("cp_gt_w_deny").deniedPermissions.clear();
            grades.grades.get("cp_gt_w_deny").deniedPermissions.add("*");
            expect(ServerCommands.run(server, "customperm grade weight cp_gt_w_deny 1000"), "set to 1000");
            expect(ServerCommands.run(server, "customperm grade weight cp_gt_w_allow 0"), "set to 0");
            if (!player.canUse("gamemode")) fail("A weight must not beat a more specific node.");
        } finally {
            grades.grades.remove("cp_gt_w_allow");
            grades.grades.remove("cp_gt_w_deny");
            grades.userGrades.values().forEach(list -> list.removeAll(List.of("cp_gt_w_allow", "cp_gt_w_deny")));
            grades.userGrades.values().removeIf(List::isEmpty);
        }
        helper.succeed();
    }

    /**
     * Nodes carried by the player themselves: they outrank their grades at the same level, a more specific
     * grade node still wins, and the text commands apply live.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ownNodesOutrankGradesButNotSpecificity(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_g_own", 0);
             Exposure gamemode = Exposure.of(server, "gamemode");
             Exposure time = Exposure.of(server, "time")) {
            uuid = player.uuid().toString();
            try (Grants denied = Grants.deny(player, "customperm.command.gamemode")) {
                if (player.canUse("gamemode")) fail("The grade DENY must apply before the player carries anything.");

                expect(ServerCommands.run(server, "customperm user addperm cp_g_own customperm.command.gamemode"),
                        "Added customperm.command.gamemode -> cp_g_own");
                if (!player.canUse("gamemode")) fail("A node on the player must outrank their grade at the same level.");

                expect(ServerCommands.run(server, "customperm user addperm cp_g_own customperm.command.gamemode"),
                        "is already granted to cp_g_own");
                expect(ServerCommands.run(server, "customperm user list cp_g_own"), "own allow: customperm.command.gamemode");

                expect(ServerCommands.run(server, "customperm user removeperm cp_g_own customperm.command.gamemode"),
                        "Removed customperm.command.gamemode from cp_g_own");
                if (player.canUse("gamemode")) fail("Removing the node must hand the decision back to the grade.");
                if (grades.userPermissions.containsKey("cp_g_own")) fail("Entries are keyed by UUID, never by name.");
            }

            // A wildcard the player denies themselves does not beat a more specific node from a grade.
            try (Grants allowed = Grants.allow(player, "customperm.command.time")) {
                expect(ServerCommands.run(server, "customperm user adddeny cp_g_own *"), "Denied * -> cp_g_own");
                if (!player.canUse("time")) fail("An exact ALLOW in a grade must beat a * denied on the player.");
                if (player.canUse("gamemode")) fail("The * denied on the player must still close the rest.");
                expect(ServerCommands.run(server, "customperm user removedeny cp_g_own *"), "Removed the denial of *");
            }
            expect(ServerCommands.run(server, "customperm user addperm cp_g_nobody customperm.admin"), "Unknown player");
        } finally {
            if (uuid != null) {
                grades.userPermissions.remove(uuid);
                grades.userDeniedPermissions.remove(uuid);
            }
        }
        helper.succeed();
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) fail("Expected output containing '" + fragment + "', got: " + lines);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
