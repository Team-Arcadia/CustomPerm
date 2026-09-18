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
import com.arcadia.customperm.admin.ConfigAdmin;
import com.arcadia.customperm.admin.ExpirySweeper;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.perm.Expiry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Temporary entries through the commands: durations read and refused, an entry that expires taking the
 * command away before anything sweeps it, and the sweep that tidies the file, records the removal and
 * sends the command tree again. Internal mode: with LuckPerms, LuckPerms keeps its own expiries.
 *
 * <p>The sweep runs every second on its own, so a test never counts on being the one that removes an
 * entry: it checks the state it leaves.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class TemporaryEntriesGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String GRADE = "cp_t_donor";
    private static final String COMMAND = "defaultgamemode";
    private static final String NODE = "customperm.command." + COMMAND;

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_temporary")
    public static void aTemporaryGrantExpiresAndIsSwept(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_t_player", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE);
            ServerCommands.run(server, "customperm grade create " + GRADE);

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 30x"),
                    "Invalid duration '30x'");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 30d extra"),
                    "Expected a node, then optionally a duration");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 30d"),
                    "Added " + NODE + " -> " + GRADE + " for 30d");
            long at = grades.grades.get(GRADE).permissionExpiries.getOrDefault(NODE, 0L);
            check(Math.abs(at - (Expiry.now() + 30L * 86400)) < 5, "30d must expire thirty days from now: " + at);

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE),
                    NODE + " on " + GRADE + " is now permanent");
            check(!grades.grades.get(GRADE).permissionExpiries.containsKey(NODE),
                    "adding it again without a duration must make it permanent");
            ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 1h");

            expect(ServerCommands.run(server, "customperm grade assign cp_t_player " + GRADE + " 7d"),
                    "Assigned " + GRADE + " -> cp_t_player for 7d");
            expect(ServerCommands.run(server, "customperm grade assign cp_t_player " + GRADE + " soon"),
                    "Invalid duration 'soon'");
            expectAny(ServerCommands.run(server, "customperm user list cp_t_player"), GRADE + " (7d left)", GRADE + " (6d 23h left)");
            check(player.canUse(COMMAND), "the temporary grade must grant the command while it lasts");

            // Time passes: the node is past its expiry. The resolver alone must stop granting it.
            grades.grades.get(GRADE).permissionExpiries.put(NODE, Expiry.now() - 1);
            check(!player.canUse(COMMAND), "an expired node must stop granting before any sweep");

            long trees = player.commandTreesReceived();
            ExpirySweeper.sweep(server);
            check(!grades.grades.get(GRADE).permissions.contains(NODE), "the sweep must remove the expired node");
            check(!grades.grades.get(GRADE).permissionExpiries.containsKey(NODE), "and its expiry");
            check(player.commandTreesReceived() > trees || !player.canUse(COMMAND),
                    "the players concerned must get their command tree again");
            check(ActivityLog.recent(LogKind.ADMIN, 50).stream().anyMatch(entry ->
                            entry.source().equals(LogEntry.SOURCE_EXPIRY)
                                    && entry.action().equals(GRADE + " no longer grants " + NODE)),
                    "the removal must be in the activity log");

            // The held grade expires the same way.
            grades.userGradeExpiries.get(uuid).put(GRADE, Expiry.now() - 1);
            ExpirySweeper.sweep(server);
            check(!grades.userGrades.containsKey(uuid), "an expired grade must no longer be held");
            check(!grades.userGradeExpiries.containsKey(uuid), "and its expiry must go with it");
        } finally {
            grades.grades.remove(GRADE);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userGradeExpiries.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /** A player's own temporary node and a temporary refusal, through the user commands. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_temporary")
    public static void aPlayersOwnEntriesTakeADurationToo(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_t_own", 0)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE + "_r");
            ServerCommands.run(server, "customperm grade create " + GRADE + "_r");

            expect(ServerCommands.run(server, "customperm user addperm cp_t_own customperm.command.seed 2h"),
                    "Added customperm.command.seed -> cp_t_own for 2h");
            expect(ServerCommands.run(server, "customperm user denygrade cp_t_own " + GRADE + "_r 1d"),
                    "cp_t_own now refuses " + GRADE + "_r for 1d");
            Map<String, Long> own = grades.userPermissionExpiries.get(uuid);
            check(own != null && own.containsKey("customperm.command.seed"), "the node's expiry must be stored");
            check(grades.userDeniedGradeExpiries.get(uuid).containsKey(GRADE + "_r"), "the refusal's too");
            List<String> listed = ServerCommands.run(server, "customperm user list cp_t_own");
            expectAny(listed, "customperm.command.seed (2h left)", "customperm.command.seed (1h 59m left)");
            expectAny(listed, GRADE + "_r (1d left)", GRADE + "_r (23h 59m left)");

            expect(ServerCommands.run(server, "customperm user removeperm cp_t_own customperm.command.seed"),
                    "Removed customperm.command.seed from cp_t_own");
            check(!grades.userPermissionExpiries.containsKey(uuid), "removing a node must forget its expiry");
        } finally {
            grades.grades.remove(GRADE + "_r");
            if (uuid != null) {
                grades.userPermissions.remove(uuid);
                grades.userPermissionExpiries.remove(uuid);
                grades.userDeniedGrades.remove(uuid);
                grades.userDeniedGradeExpiries.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) {
            throw new GameTestAssertException("Expected '" + fragment + "' in " + lines);
        }
    }

    /** A remaining time read within the same second as the grant, or the one after. */
    private static void expectAny(List<String> lines, String... fragments) {
        for (String fragment : fragments) {
            if (ServerCommands.contains(lines, fragment)) return;
        }
        throw new GameTestAssertException("Expected one of " + List.of(fragments) + " in " + lines);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
