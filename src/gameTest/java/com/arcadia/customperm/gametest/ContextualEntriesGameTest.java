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
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * Entries limited to a world through the commands: contexts read and refused, a node that applies in the
 * Nether only, a player who carries the command tree of the world they stand in after a portal, and a grade
 * held in one world. Internal mode: with LuckPerms, contexts are LuckPerms' own.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ContextualEntriesGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String GRADE = "cp_c_nether";
    private static final String COMMAND = "defaultgamemode";
    private static final String NODE = "customperm.command." + COMMAND;
    private static final String NETHER = "world=minecraft:the_nether";

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_contextual")
    public static void aWorldNodeFollowsThePlayerAcrossWorlds(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_player", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE);
            ServerCommands.run(server, "customperm grade create " + GRADE);

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " world=bad name"),
                    "Expected a node, then optionally a duration");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " world=Bad!"),
                    "Invalid context 'world=Bad!'");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " server=lobby"),
                    "Invalid context 'server=lobby'");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " world=the_nether"),
                    "Added " + NODE + " -> " + GRADE + " in the_nether");
            check(grades.grades.get(GRADE).contexts.get(NETHER).permissions.contains(NODE),
                    "the node must be stored under the parsed context");
            check(!grades.grades.get(GRADE).permissions.contains(NODE), "and not everywhere");
            expect(ServerCommands.run(server, "customperm grade assign cp_c_player " + GRADE), "Assigned " + GRADE);

            teleport(server, player, Level.OVERWORLD);
            check(!player.canUse(COMMAND), "a node limited to the Nether must not grant in the overworld");

            long trees = player.commandTreesReceived();
            teleport(server, player, Level.NETHER);
            check(player.canUse(COMMAND), "the node must grant in the Nether");
            check(player.commandTreesReceived() > trees, "changing world must send the command tree again");

            trees = player.commandTreesReceived();
            teleport(server, player, Level.OVERWORLD);
            check(!player.canUse(COMMAND), "back in the overworld the node must stop granting");
            check(player.commandTreesReceived() > trees, "and the tree must be sent again on the way back");

            expect(ServerCommands.run(server, "customperm grade removeperm " + GRADE + " " + NODE),
                    NODE + " is not granted to " + GRADE + " — no change.");
            expect(ServerCommands.run(server, "customperm grade removeperm " + GRADE + " " + NODE + " world=the_nether"),
                    "Removed " + NODE + " from " + GRADE + " in the_nether");
            check(grades.grades.get(GRADE).contexts.isEmpty(), "an emptied context must leave the file");
        } finally {
            grades.grades.remove(GRADE);
            if (uuid != null) grades.userGrades.remove(uuid);
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_contextual")
    public static void aGradeAndPlayerNodesHeldInOneWorld(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_holder", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE);
            ServerCommands.run(server, "customperm grade create " + GRADE);
            ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE);

            expect(ServerCommands.run(server, "customperm grade assign cp_c_holder " + GRADE + " 7d world=the_nether"),
                    "Expected optionally a duration");
            expect(ServerCommands.run(server, "customperm grade assign cp_c_holder " + GRADE + " world=the_nether"),
                    "Assigned " + GRADE + " -> cp_c_holder in the_nether");
            check(grades.userContexts.get(uuid).get(NETHER).grades.contains(GRADE), "the grade must be held in the Nether");
            check(!grades.userGrades.containsKey(uuid), "and not everywhere");

            teleport(server, player, Level.OVERWORLD);
            check(!player.canUse(COMMAND), "a grade held in the Nether must not apply in the overworld");
            teleport(server, player, Level.NETHER);
            check(player.canUse(COMMAND), "it must apply in the Nether");

            expect(ServerCommands.run(server, "customperm user adddeny cp_c_holder " + NODE + " world=the_nether"),
                    "Denied " + NODE + " -> cp_c_holder in the_nether");
            check(!player.canUse(COMMAND), "the player's own contextual DENY must outrank their grade");
            expect(ServerCommands.run(server, "customperm user adddeny cp_c_holder " + NODE + " 1d world=the_nether"),
                    "Expected a node, then optionally a duration");
            expect(ServerCommands.run(server, "customperm user list cp_c_holder"),
                    "in the_nether: grade:" + GRADE + ", deny:" + NODE);

            expect(ServerCommands.run(server, "customperm user removedeny cp_c_holder " + NODE + " world=the_nether"),
                    "Removed the denial of " + NODE + " from cp_c_holder in the_nether");
            expect(ServerCommands.run(server, "customperm grade unassign cp_c_holder " + GRADE + " world=the_nether"),
                    "Unassigned " + GRADE + " from cp_c_holder in the_nether");
            check(!grades.userContexts.containsKey(uuid), "an emptied player entry must leave the file");
            check(!player.canUse(COMMAND), "unassigned, the grade must stop granting");

            ServerCommands.run(server, "customperm grade assign cp_c_holder " + GRADE + " world=the_nether");
            ServerCommands.run(server, "customperm grade delete " + GRADE);
            check(!grades.userContexts.containsKey(uuid), "deleting a grade must remove where it was held in a world");
        } finally {
            grades.grades.remove(GRADE);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userContexts.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    private static void teleport(MinecraftServer server, TestPlayer player, net.minecraft.resources.ResourceKey<Level> world) {
        ServerLevel level = server.getLevel(world);
        if (level == null) throw new GameTestAssertException("world not loaded: " + world.location());
        player.player().teleportTo(level, 0.5, 120, 0.5, Set.of(), 0, 0);
        if (player.player().level() != level) {
            throw new GameTestAssertException("the player did not reach " + world.location());
        }
    }

    private static void expect(List<String> lines, String fragment) {
        check(lines.stream().anyMatch(line -> line.contains(fragment)), "expected '" + fragment + "' in " + lines);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
