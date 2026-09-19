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
import com.arcadia.customperm.perm.Expiry;
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
                    "Invalid duration 'name'");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 1d world=the_end 2h"),
                    "Expected a node, then optionally a duration");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " world=Bad!"),
                    "Invalid context 'world=Bad!'");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " server=lobby"),
                    "server= names a server of a cluster, and this server is in none");
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
                    "Assigned " + GRADE + " -> cp_c_holder in the_nether for 7d");
            check(grades.userContexts.get(uuid).get(NETHER).gradeExpiries.containsKey(GRADE),
                    "a duration and a world together must make the grade held there temporary");
            expect(ServerCommands.run(server, "customperm grade assign cp_c_holder " + GRADE + " world=the_nether"),
                    GRADE + " for cp_c_holder in the_nether is now permanent");
            check(grades.userContexts.get(uuid).get(NETHER).grades.contains(GRADE), "the grade must be held in the Nether");
            check(grades.userContexts.get(uuid).get(NETHER).gradeExpiries.isEmpty(), "and for good once told so");
            check(!grades.userGrades.containsKey(uuid), "and not everywhere");

            teleport(server, player, Level.OVERWORLD);
            check(!player.canUse(COMMAND), "a grade held in the Nether must not apply in the overworld");
            teleport(server, player, Level.NETHER);
            check(player.canUse(COMMAND), "it must apply in the Nether");

            expect(ServerCommands.run(server, "customperm user adddeny cp_c_holder " + NODE + " world=the_nether"),
                    "Denied " + NODE + " -> cp_c_holder in the_nether");
            check(!player.canUse(COMMAND), "the player's own contextual DENY must outrank their grade");
            expect(ServerCommands.run(server, "customperm user adddeny cp_c_holder " + NODE + " 1d world=the_nether"),
                    NODE + " for cp_c_holder in the_nether now expires in 1d");
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

    /** A parent, a refusal and a prefix limited to the Nether, followed by a real player moving between worlds. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_contextual")
    public static void aParentARefusalAndAPrefixHeldInOneWorld(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        var settings = CustomPerm.configManager.getSettings();
        String base = GRADE + "_base";
        String member = GRADE + "_member";
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_linked", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            grades.grades.remove(base);
            grades.grades.remove(member);
            ServerCommands.run(server, "customperm grade create " + base);
            ServerCommands.run(server, "customperm grade create " + member);
            ServerCommands.run(server, "customperm grade addperm " + base + " " + NODE);
            ServerCommands.run(server, "customperm grade assign cp_c_linked " + member);

            expect(ServerCommands.run(server, "customperm grade parent add " + member + " " + base + " world=the_nether"),
                    member + " now inherits " + base + " in the_nether");
            expect(ServerCommands.run(server, "customperm grade parent list " + member), "in the_nether: parent:" + base);
            teleport(server, player, Level.OVERWORLD);
            check(!player.canUse(COMMAND), "a parent inherited in the Nether must give nothing in the overworld");
            teleport(server, player, Level.NETHER);
            check(player.canUse(COMMAND), "it must be inherited in the Nether");

            expect(ServerCommands.run(server, "customperm user denygrade cp_c_linked " + base + " world=the_nether"),
                    "cp_c_linked now refuses " + base + " in the_nether");
            check(!player.canUse(COMMAND), "the player's refusal in the Nether must take the parent away there");
            expect(ServerCommands.run(server, "customperm user undenygrade cp_c_linked " + base + " world=the_nether"),
                    "cp_c_linked no longer refuses " + base + " in the_nether");
            check(player.canUse(COMMAND), "and give it back once withdrawn");

            settings.decorateNames = true;
            ServerCommands.run(server, "customperm grade prefix " + member + " add 0 [M] ");
            expect(ServerCommands.run(server, "customperm grade prefix " + member + " in the_nether add 0 [Hot] "),
                    "Prefix \"[Hot] \" at 0 -> " + member + " in the_nether");
            check(player.player().getDisplayName().getString().equals("[Hot] cp_c_linked"),
                    "in the Nether, the prefix limited to it shows first: " + player.player().getDisplayName().getString());
            teleport(server, player, Level.OVERWORLD);
            check(player.player().getDisplayName().getString().equals("[M] cp_c_linked"),
                    "back in the overworld, the name must follow: " + player.player().getDisplayName().getString());

            expect(ServerCommands.run(server, "customperm grade parent remove " + member + " " + base + " world=the_nether"),
                    member + " no longer inherits " + base + " in the_nether");
            expect(ServerCommands.run(server, "customperm grade prefix " + member + " in the_nether remove 0"),
                    "Removed the prefix \"[Hot] \" at 0 from " + member + " in the_nether");
            check(grades.grades.get(member).contexts.isEmpty(), "an emptied context must leave the file");
        } finally {
            settings.decorateNames = false;
            grades.grades.remove(base);
            grades.grades.remove(member);
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

    /** Entries both limited to a world and temporary: they apply there while they last, then the sweep removes them. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_contextual")
    public static void aTemporaryEntryHeldInOneWorld(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String base = GRADE + "_tbase";
        String member = GRADE + "_tmember";
        String end = "world=minecraft:the_end";
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_timed", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            for (String name : List.of(base, member)) {
                grades.grades.remove(name);
                ServerCommands.run(server, "customperm grade create " + name);
            }
            ServerCommands.run(server, "customperm grade addperm " + base + " " + NODE);
            ServerCommands.run(server, "customperm grade assign cp_c_timed " + member);
            teleport(server, player, Level.NETHER);

            expect(ServerCommands.run(server, "customperm grade parent add " + member + " " + base + " world=the_nether 2h 1d"),
                    "Expected optionally a duration such as 30d and a world");
            expect(ServerCommands.run(server, "customperm grade parent add " + member + " " + base + " 2h 1d"),
                    "One duration at most");
            expect(ServerCommands.run(server, "customperm grade parent add " + member + " " + base + " 2h world=the_nether"),
                    member + " now inherits " + base + " in the_nether for 2h");
            check(player.canUse(COMMAND), "a temporary parent in the Nether must be inherited there while it lasts");
            List<String> listed = ServerCommands.run(server, "customperm grade parent list " + member);
            check(listed.stream().anyMatch(line -> line.contains("parent:" + base + " (2h left)")
                    || line.contains("parent:" + base + " (1h 59m left)")), "the listing must show the time left: " + listed);
            grades.grades.get(member).contexts.get(NETHER).parentExpiries.put(base, Expiry.now() - 1);
            check(!player.canUse(COMMAND), "an expired parent limited to a world must stop before any sweep");
            List<String> removed = ExpirySweeper.sweep(server);
            check(removed.contains(member + " in the_nether no longer inherits " + base), "the sweep must say so: " + removed);
            check(!grades.grades.get(member).contexts.containsKey(NETHER), "and leave no emptied world entry behind");

            // Either order.
            expect(ServerCommands.run(server, "customperm user addperm cp_c_timed " + NODE + " world=the_nether 1d"),
                    "Added " + NODE + " -> cp_c_timed in the_nether for 1d");
            check(player.canUse(COMMAND), "a temporary node of the player's own must apply in its world");
            expect(ServerCommands.run(server, "customperm user addperm cp_c_timed " + NODE + " world=the_nether"),
                    NODE + " for cp_c_timed in the_nether is now permanent");
            check(grades.userContexts.get(uuid).get(NETHER).permissionExpiries.isEmpty(), "the expiry must be gone");
            ServerCommands.run(server, "customperm user removeperm cp_c_timed " + NODE + " world=the_nether");
            check(!grades.userContexts.containsKey(uuid), "a removed node must take its world entry with it");

            expect(ServerCommands.run(server, "customperm grade assign cp_c_timed " + base + " 3h world=the_nether"),
                    "Assigned " + base + " -> cp_c_timed in the_nether for 3h");
            expect(ServerCommands.run(server, "customperm user denygrade cp_c_timed " + base + " world=the_end 1h"),
                    "cp_c_timed now refuses " + base + " in the_end for 1h");
            expect(ServerCommands.run(server, "customperm grade prefix " + member + " in the_nether addtemp 5 1h [Hot]"),
                    "Prefix \"[Hot]\" at 5 -> " + member + " in the_nether for 1h");
            check(player.canUse(COMMAND), "a grade held in the Nether for a while must apply there");
            expect(ServerCommands.run(server, "customperm user list cp_c_timed"), "grade:" + base + " (");

            grades.userContexts.get(uuid).get(NETHER).gradeExpiries.put(base, Expiry.now() - 1);
            check(!player.canUse(COMMAND), "an expired grade held in a world must stop applying before any sweep");
            grades.userContexts.get(uuid).get(end).refusedExpiries.put(base, Expiry.now() - 1);
            grades.grades.get(member).contexts.get(NETHER).prefixes.get(0).expires = Expiry.now() - 1;
            removed = ExpirySweeper.sweep(server);
            check(removed.contains("cp_c_timed in the_nether no longer holds " + base), "the grade must be swept: " + removed);
            check(removed.contains("cp_c_timed in the_end no longer refuses " + base), "the refusal too: " + removed);
            check(removed.contains(member + " in the_nether no longer shows the prefix \"[Hot]\" at 5"),
                    "and the prefix: " + removed);
            check(!grades.userContexts.containsKey(uuid), "every emptied world entry must leave the file");
            check(!grades.grades.get(member).contexts.containsKey(NETHER), "the grade's too");
        } finally {
            for (String name : List.of(base, member)) grades.grades.remove(name);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userContexts.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /** A game mode, a static context and several worlds, read the way the player stands. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_contextual")
    public static void aGameModeAStaticContextAndSeveralWorlds(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        var settings = CustomPerm.configManager.getSettings();
        java.util.Map<String, String> staticsBefore = settings.staticContexts;
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_modes", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE);
            ServerCommands.run(server, "customperm grade create " + GRADE);
            ServerCommands.run(server, "customperm grade assign cp_c_modes " + GRADE);
            teleport(server, player, Level.OVERWORLD);
            player.player().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " gamemode=creative"),
                    "Added " + NODE + " -> " + GRADE + " in creative");
            check(!player.canUse(COMMAND), "a node limited to creative must not apply in survival");
            player.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            check(player.canUse(COMMAND), "it must apply once the player is in creative");
            expect(ServerCommands.run(server, "customperm contexts cp_c_modes"), "gamemode=creative");
            ServerCommands.run(server, "customperm grade removeperm " + GRADE + " " + NODE + " gamemode=creative");

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " gamemode=flying"),
                    "Invalid context");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " region=eu"),
                    "Nothing on this server sets 'region'");
            expect(ServerCommands.run(server, "customperm contexts set world nether"), "cannot be a static context");
            expect(ServerCommands.run(server, "customperm contexts set region EU"),
                    "region=eu now holds for every player on this server");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " region=eu"),
                    "Added " + NODE + " -> " + GRADE + " in region=eu");
            check(player.canUse(COMMAND), "a node limited to a static context that holds must apply");
            expect(ServerCommands.run(server, "customperm contexts unset region"), "region no longer holds here");
            check(!player.canUse(COMMAND), "and stop once it no longer holds");
            ServerCommands.run(server, "customperm grade removeperm " + GRADE + " " + NODE + " region=eu");

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " world=the_end,world=the_nether"),
                    "Added " + NODE + " -> " + GRADE + " in the_end or the_nether");
            check(!player.canUse(COMMAND), "two worlds must not include the overworld");
            teleport(server, player, Level.NETHER);
            check(player.canUse(COMMAND), "either world must do");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " world=the_nether gamemode=creative"),
                    "One context at most");
        } finally {
            settings.staticContexts = staticsBefore;
            grades.grades.remove(GRADE);
            if (uuid != null) grades.userGrades.remove(uuid);
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /**
     * What an export writes must be what LuckPerms reads: on NeoForge it names the dimension {@code dimension-type},
     * and its {@code world} is the save's name, the same in every dimension.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_contextual")
    public static void luckPermsNamesTheDimensionDimensionType(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        java.util.UUID uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_lpdim", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid();
            com.arcadia.customperm.gametest.support.LuckPermsTestSupport.setContextNode(uuid, NODE, true,
                    "dimension-type", "the_nether");
            teleport(server, player, Level.OVERWORLD);
            check(!player.canUse(COMMAND), "a node limited to the Nether must not apply in the overworld");
            teleport(server, player, Level.NETHER);
            check(player.canUse(COMMAND), "LuckPerms must read dimension-type=the_nether as the Nether");
            com.arcadia.customperm.gametest.support.LuckPermsTestSupport.clearNodes(uuid, List.of(NODE));
            com.arcadia.customperm.gametest.support.LuckPermsTestSupport.setContextNode(uuid, NODE, true,
                    "world", "the_nether");
            check(!player.canUse(COMMAND), "LuckPerms' world context is not the dimension");
        } finally {
            if (uuid != null) com.arcadia.customperm.gametest.support.LuckPermsTestSupport.clearNodes(uuid, List.of(NODE));
        }
        helper.succeed();
    }

    private static void expect(List<String> lines, String fragment) {
        check(lines.stream().anyMatch(line -> line.contains(fragment)), "expected '" + fragment + "' in " + lines);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
