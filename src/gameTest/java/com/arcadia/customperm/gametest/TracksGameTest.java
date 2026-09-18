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
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.PlayersData;
import com.arcadia.customperm.perm.Expiry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Tracks through the commands: building one, moving a real player up and down it with the command tree
 * following, the refusals, and a deleted grade leaving the ladder. Internal mode: with LuckPerms, tracks
 * are LuckPerms' own.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class TracksGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String TRACK = "cp_k_ladder";
    private static final String LOW = "cp_k_member";
    private static final String MID = "cp_k_vip";
    private static final String TOP = "cp_k_staff";
    private static final String COMMAND = "defaultgamemode";
    private static final String NODE = "customperm.command." + COMMAND;

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_tracks")
    public static void aPlayerClimbsAndLeavesATrack(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_k_player", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            for (String grade : List.of(LOW, MID, TOP)) ServerCommands.run(server, "customperm grade create " + grade);
            ServerCommands.run(server, "customperm grade addperm " + MID + " " + NODE);

            expect(ServerCommands.run(server, "customperm track create " + TRACK), "Created track " + TRACK);
            expect(ServerCommands.run(server, "customperm track create " + TRACK), "Track already exists");
            expect(ServerCommands.run(server, "customperm track promote cp_k_player " + TRACK), "the track has no grade yet");
            ServerCommands.run(server, "customperm track append " + TRACK + " " + LOW);
            ServerCommands.run(server, "customperm track append " + TRACK + " " + TOP);
            expect(ServerCommands.run(server, "customperm track insert " + TRACK + " " + MID + " 2"),
                    TRACK + ": " + LOW + " > " + MID + " > " + TOP);
            expect(ServerCommands.run(server, "customperm track insert " + TRACK + " " + MID + " 1"), "is already rung 2");
            expect(ServerCommands.run(server, "customperm track append " + TRACK + " cp_k_ghost"), "No such grade");
            expect(ServerCommands.run(server, "customperm track list " + TRACK), LOW + " > " + MID + " > " + TOP);

            expect(ServerCommands.run(server, "customperm track promote cp_k_player " + TRACK),
                    "Put cp_k_player on " + TRACK + " at " + LOW);
            check(!player.canUse(COMMAND), "the first rung grants nothing here");

            // A temporary rung: promoting gives it up with its expiry, and the next one is for good.
            grades.userGradeExpiries.computeIfAbsent(uuid, k -> new java.util.HashMap<>()).put(LOW, Expiry.now() + 3600);
            long trees = player.commandTreesReceived();
            expect(ServerCommands.run(server, "customperm track promote cp_k_player " + TRACK),
                    "Promoted cp_k_player on " + TRACK + ": " + LOW + " -> " + MID);
            check(grades.userGrades.get(uuid).equals(List.of(MID)), "promoting must swap the rung: " + grades.userGrades.get(uuid));
            check(!grades.userGradeExpiries.containsKey(uuid), "the rung given up must take its expiry with it");
            check(player.canUse(COMMAND), "the new rung must grant at once");
            check(player.commandTreesReceived() > trees, "and the command tree must be sent again");

            ServerCommands.run(server, "customperm track promote cp_k_player " + TRACK);
            expect(ServerCommands.run(server, "customperm track promote cp_k_player " + TRACK), "already on the top rung");

            ServerCommands.run(server, "customperm grade assign cp_k_player " + LOW);
            expect(ServerCommands.run(server, "customperm track demote cp_k_player " + TRACK), "they hold several grades of it");
            ServerCommands.run(server, "customperm grade unassign cp_k_player " + LOW);

            ServerCommands.run(server, "customperm user denygrade cp_k_player " + MID);
            expect(ServerCommands.run(server, "customperm track demote cp_k_player " + TRACK), "refuses " + MID);
            ServerCommands.run(server, "customperm user undenygrade cp_k_player " + MID);

            ServerCommands.run(server, "customperm track demote cp_k_player " + TRACK);
            ServerCommands.run(server, "customperm track demote cp_k_player " + TRACK);
            expect(ServerCommands.run(server, "customperm track demote cp_k_player " + TRACK),
                    "Took cp_k_player off " + TRACK + ": " + LOW + " was its first rung");
            check(!grades.userGrades.containsKey(uuid), "demoting from the first rung must leave no grade of the track");
            expect(ServerCommands.run(server, "customperm track demote cp_k_player " + TRACK), "on no rung");

            expect(ServerCommands.run(server, "customperm grade delete " + MID), "Taken off track(s) " + TRACK);
            check(grades.tracks.get(TRACK).equals(List.of(LOW, TOP)), "a deleted grade must leave the track: " + grades.tracks.get(TRACK));
            expect(ServerCommands.run(server, "customperm track remove " + TRACK + " " + TOP), TRACK + ": " + LOW);
            expect(ServerCommands.run(server, "customperm track delete " + TRACK), "Deleted track " + TRACK);
            check(!grades.tracks.containsKey(TRACK), "the track must be gone");
        } finally {
            grades.tracks.remove(TRACK);
            for (String grade : List.of(LOW, MID, TOP)) grades.grades.remove(grade);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userGradeExpiries.remove(uuid);
                grades.userDeniedGrades.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /** The Players page: the tracks it carries, and promote and demote by name from the Tracks tab. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_tracks")
    public static void thePlayersPageMovesAPlayerAlongATrack(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_k_owner", 4);
             TestPlayer member = TestPlayer.join(helper.getLevel(), "cp_k_climber", 0)) {
            uuid = member.uuid().toString();
            for (String grade : List.of(LOW, MID)) ServerCommands.run(server, "customperm grade create " + grade);
            ServerCommands.run(server, "customperm track create " + TRACK);
            ServerCommands.run(server, "customperm track append " + TRACK + " " + LOW);
            ServerCommands.run(server, "customperm track append " + TRACK + " " + MID);

            act(owner, GuiAction.TRACK_PROMOTE, "cp_k_climber", TRACK);
            result(owner, "OK: Put cp_k_climber on " + TRACK + " at " + LOW);
            act(owner, GuiAction.TRACK_PROMOTE, "cp_k_climber", TRACK);
            result(owner, "OK: Promoted cp_k_climber on " + TRACK + ": " + LOW + " -> " + MID);
            check(grades.userGrades.get(uuid).equals(List.of(MID)), "the page must move the player: " + grades.userGrades.get(uuid));

            PlayersData page = owner.payloads(GuiPagePayload.class).stream()
                    .map(GuiPagePayload::data).filter(PlayersData.class::isInstance).map(PlayersData.class::cast)
                    .reduce((first, second) -> second).orElseThrow(() -> new GameTestAssertException("No Players page"));
            check(page.tracks().contains(new PlayersData.Track(TRACK, List.of(LOW, MID))),
                    "the page must carry the track and its rungs: " + page.tracks());

            act(owner, GuiAction.TRACK_DEMOTE, "cp_k_climber", "cp_k_nothing");
            result(owner, "FAIL: No such track: cp_k_nothing");
        } finally {
            grades.tracks.remove(TRACK);
            for (String grade : List.of(LOW, MID)) grades.grades.remove(grade);
            if (uuid != null) grades.userGrades.remove(uuid);
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    private static void act(TestPlayer player, GuiAction action, String... args) {
        player.clearReceived();
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.PLAYERS.id()),
                player.payloadContext());
    }

    private static void result(TestPlayer player, String prefix) {
        List<String> results = player.payloads(GuiActionResultPayload.class).stream()
                .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message()).toList();
        if (results.size() != 1 || !results.get(0).startsWith(prefix))
            throw new GameTestAssertException("Expected one result starting with '" + prefix + "', got " + results);
    }

    private static void expect(List<String> lines, String fragment) {
        check(lines.stream().anyMatch(line -> line.contains(fragment)), "expected '" + fragment + "' in " + lines);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
