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
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Direct command exposure with a connected player, in both backends (test procedure 1.0.5, A4 and
 * A9.4; audit retest R02). The node is granted through whichever backend is active.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class CommandExposureGameTest {

    private static final String TEMPLATE = "empty_3x3";

    /** A4.1: nothing is exposed by default; a regular player keeps the vanilla refusal. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unexposedCommandKeepsVanillaRefusal(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_x_default", 0);
             Grants ignored = Grants.allow(player, "customperm.command.gamemode")) {
            if (player.canUse("gamemode"))
                fail("An unexposed /gamemode must stay op-only, even with customperm.command.gamemode granted.");
        }
        helper.succeed();
    }

    /** A4.2: exposing and granting opens the command, removing the exposure closes it again. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void exposeGrantRunThenRemove(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_x_expose", 0);
             Grants ignored = Grants.allow(player, "customperm.command.gamemode");
             Exposure exposure = Exposure.of(server, "gamemode")) {
            if (!player.canUse("gamemode")) fail("Exposed and granted /gamemode must be usable.");
            player.exec("gamemode spectator");
            if (player.player().gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
                fail("/gamemode spectator did not apply to the granted player.");

            exposure.close();
            if (player.canUse("gamemode")) fail("Removing the exposure must restore the op-only requirement.");
        } catch (CommandSyntaxException e) {
            fail("Granted player could not run /gamemode: " + e.getMessage());
        }
        helper.succeed();
    }

    /** Negative control for A4.2: exposed but not granted stays closed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void exposedButNotGrantedStaysClosed(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_x_nogrant", 0);
             Exposure ignored = Exposure.of(helper.getLevel().getServer(), "gamemode")) {
            if (player.canUse("gamemode")) fail("Exposing /gamemode must not open it to players without the node.");
        }
        helper.succeed();
    }

    /**
     * The exposure gate re-asserted over other permission handlers must stop granting as soon as the
     * command is no longer exposed, even if its removal skipped the re-assertion (a direct edit of the
     * exposed set, a failed reassert). Without the check inside the gate, LuckPerms mode let any holder
     * of customperm.command.gamemode run a command that was no longer exposed.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void staleExposureGateGrantsNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_x_stale", 0);
             Grants ignored = Grants.allow(player, "customperm.command.gamemode")) {
            ServerCommands.run(server, "customperm command add gamemode");
            if (!player.canUse("gamemode")) fail("Setup: exposed and granted /gamemode must be usable.");
            exposed.remove("gamemode");  // bypasses the re-assertion that normally restores the requirement
            if (player.canUse("gamemode")) fail("A stale exposure gate still granted /gamemode after un-exposure.");
        } finally {
            exposed.add("gamemode");
            ServerCommands.run(server, "customperm command remove gamemode");
        }
        helper.succeed();
    }

    /** A4.3: /customperm itself, unknown commands and duplicates are refused with a clear message. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void exposureGuardsRefuseInvalidRequests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        expect(ServerCommands.run(server, "customperm command add customperm"), "Cannot expose /customperm itself.");
        expect(ServerCommands.run(server, "customperm command add cp_no_such_command"), "does not exist on this server");
        try (Exposure ignored = Exposure.of(server, "gamemode")) {
            expect(ServerCommands.run(server, "customperm command add gamemode"), "is already exposed");
        }
        helper.succeed();
    }

    /** A4.4: operators keep access to an exposed command without holding its node. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void operatorsKeepAccessWithoutTheNode(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_x_op", 2);
             Exposure ignored = Exposure.of(helper.getLevel().getServer(), "gamemode")) {
            if (!op.canUse("gamemode")) fail("A level-2 operator lost /gamemode after it was exposed.");
        }
        helper.succeed();
    }

    /** A9.4 / R02: CustomPerm grants never open /customperm to a non-operator. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void custompermStaysOperatorOnly(GameTestHelper helper) {
        // "*" is LuckPerms' own "everything, like op" grant, so it is only meaningful for the internal resolver.
        String[] nodes = CustomPerm.isLuckPermsActive()
                ? new String[] {"customperm.*"}
                : new String[] {"*", "customperm.*"};
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_x_admin", 0);
             Grants ignored = Grants.allow(player, nodes)) {
            if (player.canUse("customperm")) fail("A non-operator holding " + List.of(nodes) + " must not see /customperm.");
            player.clearReceived();
            player.type("customperm status");
            if (player.chatContains("CustomPerm Status")) fail("A non-operator could run /customperm status.");
        }
        helper.succeed();
    }

    /** Procedure 1.0.5 B7.2: access granted to a player survives a disconnect and reconnect. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void accessSurvivesReconnection(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "cp_x_relog");
        try (Exposure ignored = Exposure.of(helper.getLevel().getServer(), "gamemode")) {
            TestPlayer first = TestPlayer.join(helper.getLevel(), profile, 0, true);
            try (Grants grants = Grants.allow(first, "customperm.command.gamemode")) {
                first.close();
                try (TestPlayer again = TestPlayer.join(helper.getLevel(), profile, 0, true)) {
                    if (!again.canUse("gamemode")) fail("Reconnected player lost access to exposed /gamemode.");
                    if (again.commandTreesReceived() < 1) fail("Reconnected player received no command tree.");
                }
            } finally {
                first.close();
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

    /** Exposes a command for the duration of a test through the real admin command, restoring it after. */
    static final class Exposure implements AutoCloseable {
        private final MinecraftServer server;
        private final String command;
        private boolean added;

        private Exposure(MinecraftServer server, String command) {
            this.server = server;
            this.command = command;
        }

        static Exposure of(MinecraftServer server, String command) {
            Exposure exposure = new Exposure(server, command);
            if (!CustomPerm.configManager.getCommands().grantedCommands.contains(command)) {
                ServerCommands.run(server, "customperm command add " + command);
                exposure.added = true;
            }
            return exposure;
        }

        @Override
        public void close() {
            if (added) {
                ServerCommands.run(server, "customperm command remove " + command);
                added = false;
            }
        }
    }
}
