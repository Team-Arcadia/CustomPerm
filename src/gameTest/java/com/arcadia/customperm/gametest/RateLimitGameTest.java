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
import com.arcadia.customperm.command.RateLimitPersistence;
import com.arcadia.customperm.command.RateLimiter;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Per-player rate limits with connected players, in both backends (procedure 1.0.5 A6 and A7, audit
 * retest R04). Players are level-2 operators so /gamemode is usable without any exposure: limits apply
 * to every player whatever their permissions, which is exactly what is under test.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class RateLimitGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String REFUSED = "Rate limit reached for /gamemode";

    /** A6.1: the call past the limit is refused with the retry delay and the configured limit. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void callPastTheLimitIsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_limit", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 3 60");
            player.clearReceived();
            int refused = runGamemode(player, 4);
            if (refused != 1) fail("Expected exactly the 4th call refused, got " + refused + " refusal(s): " + player.chat());
            if (!player.chatContains("(max 3 per 60s)") || !player.chatContains("try again in"))
                fail("Refusal must state the retry delay and the limit, got: " + player.chat());
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** A6.3: every sub-command of the same root shares one counter. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void subCommandsShareOneCounter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_shared", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 2 60");
            player.clearReceived();
            player.type("gamemode creative");
            player.type("gamemode survival");
            player.type("gamemode adventure");
            if (!player.chatContains(REFUSED)) fail("Alternating sub-commands bypassed the limit: " + player.chat());
            if (player.player().gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                fail("The refused third call was executed anyway.");
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** A6.4 + A6.5: counters are per player, and the console is never limited. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void countersArePerPlayerAndConsoleIsExempt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer first = TestPlayer.join(helper.getLevel(), "cp_r_first", 2);
             TestPlayer second = TestPlayer.join(helper.getLevel(), "cp_r_second", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            first.clearReceived();
            second.clearReceived();
            if (runGamemode(first, 2) != 1) fail("Setup: first player should be limited after one call.");
            if (runGamemode(second, 1) != 0) fail("A second player was limited by the first player's usage.");
            for (int i = 0; i < 3; i++) {
                List<String> out = ServerCommands.run(server, "gamemode creative cp_r_first");
                if (ServerCommands.contains(out, REFUSED)) fail("The console must not be rate limited.");
            }
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** A6.6 + A6.7: list, disable, enable, remove, and Brigadier refusing a zero limit or window. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ruleManagementCommands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_manage", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            expect(ServerCommands.run(server, "customperm ratelimit list"), "/gamemode  1 per 60s  [enabled]");

            expect(ServerCommands.run(server, "customperm ratelimit disable gamemode"), "disabled");
            player.clearReceived();
            if (runGamemode(player, 3) != 0) fail("A disabled rule still limited the player.");
            expect(ServerCommands.run(server, "customperm ratelimit list"), "[disabled]");

            expect(ServerCommands.run(server, "customperm ratelimit enable gamemode"), "enabled (1 per 60s)");
            expect(ServerCommands.run(server, "customperm ratelimit list"), "persistence=world_save");
            expect(ServerCommands.run(server, "customperm ratelimit persistence gamemode immediate"),
                    "is now written after every accepted use (immediate)");
            ServerCommands.run(server, "customperm ratelimit set gamemode 2 60");
            expect(ServerCommands.run(server, "customperm ratelimit list"), "/gamemode  2 per 60s  [enabled]  persistence=immediate");
            expect(ServerCommands.run(server, "customperm ratelimit persistence gamemode sometimes"), "Unknown persistence mode");
            expect(ServerCommands.run(server, "customperm ratelimit persistence cp_r_none immediate"), "No rate limit configured for /cp_r_none");
            expect(ServerCommands.run(server, "customperm ratelimit remove gamemode"), "Rate limit for /gamemode removed.");
            expect(ServerCommands.run(server, "customperm ratelimit enable cp_r_none"), "No rate limit configured for /cp_r_none");

            if (ServerCommands.syntaxError(server, "customperm ratelimit set gamemode 0 10") == null)
                fail("A limit of 0 executions must be rejected by the argument parser.");
            if (ServerCommands.syntaxError(server, "customperm ratelimit set gamemode 3 0") == null)
                fail("A window of 0 seconds must be rejected by the argument parser.");
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** A7.2: reconnecting does not reset a player's counter. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void reconnectingDoesNotResetTheCounter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "cp_r_relog");
        try {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            try (TestPlayer first = TestPlayer.join(helper.getLevel(), profile, 2, true)) {
                first.clearReceived();
                runGamemode(first, 1);
            }
            try (TestPlayer again = TestPlayer.join(helper.getLevel(), profile, 2, true)) {
                again.clearReceived();
                if (runGamemode(again, 1) != 1) fail("Reconnecting reset the rate-limit counter.");
            }
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** A7.4: repeated reloads do not double-wrap commands, so the limit counts each call once. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reloadsDoNotDoubleCount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_reloads", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 3 60");
            for (int i = 0; i < 5; i++) ServerCommands.run(server, "customperm reload");
            player.clearReceived();
            int refused = runGamemode(player, 5);
            if (refused != 2) fail("Expected 3 accepted and 2 refused after 5 reloads, got " + refused + " refused.");
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** A7.6: removing a rule mid-window lets the next call through, with no residual refusal. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void removingRuleMidWindowUnblocks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_removed", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            player.clearReceived();
            if (runGamemode(player, 2) != 1) fail("Setup: the second call should be refused.");
            removeRule(server);
            player.clearReceived();
            if (runGamemode(player, 2) != 0) fail("Calls were still refused after the rule was removed: " + player.chat());
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** Aliases are limited under their own name, through the alias execution path. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasIsLimitedUnderItsName(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_alias", 2)) {
            ServerCommands.run(server, "customperm alias add cp_r_macro say hi");
            ServerCommands.run(server, "customperm ratelimit set cp_r_macro 1 60");
            player.clearReceived();
            player.type("cp_r_macro");
            player.type("cp_r_macro");
            if (!player.chatContains("Rate limit reached for /cp_r_macro")) fail("The alias limit was not enforced: " + player.chat());
        } finally {
            ServerCommands.run(server, "customperm ratelimit remove cp_r_macro");
            ServerCommands.run(server, "customperm alias remove cp_r_macro");
        }
        helper.succeed();
    }

    /**
     * A6.2: once the window has elapsed the command is accepted again. Own batch: it waits real time,
     * and a parallel test changing the /gamemode rule meanwhile would invalidate it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_ratelimit_window")
    public static void windowExpiryAcceptsAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_window", 2);
        try {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 1");
            player.clearReceived();
            if (runGamemode(player, 2) != 1) fail("Setup: the second call inside the 1s window should be refused.");
        } catch (RuntimeException e) {
            removeRule(server);
            player.close();
            throw e;
        }
        // 30 ticks = 1.5 s, comfortably past the 1-second window.
        helper.runAfterDelay(30, () -> {
            try {
                player.clearReceived();
                if (runGamemode(player, 1) != 0) fail("The call was still refused after the window elapsed: " + player.chat());
                helper.succeed();
            } finally {
                removeRule(server);
                player.close();
            }
        });
    }

    /**
     * A restart keeps the counters: history saved, memory wiped as on server stop, history loaded as on
     * server start, and the player is still limited. Own batch: it clears the limiter's global state.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_ratelimit_restart")
    public static void countersSurviveARestart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_restart", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 3600");
            player.clearReceived();
            if (runGamemode(player, 1) != 0) fail("Setup: the first call must be accepted.");

            RateLimitPersistence.write();
            RateLimiter.clearServerState();
            RateLimitPersistence.load();

            if (runGamemode(player, 1) != 1) fail("The counter was reset by the restart: " + player.chat());
            if (!historyFileMentions(player)) fail("The history file does not contain the player's use.");
        } finally {
            removeRule(server);
            RateLimitPersistence.write();
        }
        helper.succeed();
    }

    /**
     * A vanilla /reload rebuilds the whole command dispatcher; it used to wipe the counters with it and
     * hand every player a fresh quota. The player reconnects after the reload (see TestPlayer's known
     * limit). Own batch: the reload replaces the dispatcher for everyone.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "customperm_ratelimit_vanilla_reload")
    public static void vanillaReloadKeepsCounters(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "cp_r_datapack");
        ServerCommands.run(server, "customperm ratelimit set gamemode 1 3600");
        try (TestPlayer before = TestPlayer.join(helper.getLevel(), profile, 2, true)) {
            before.clearReceived();
            if (runGamemode(before, 2) != 1) {
                removeRule(server);
                fail("Setup: the second call before the reload must be refused.");
            }
        }
        CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        whenDone(helper, reload, 500, () -> {
            try (TestPlayer after = TestPlayer.join(helper.getLevel(), profile, 2, true)) {
                if (reload.isCompletedExceptionally()) fail("The vanilla reload failed.");
                after.clearReceived();
                if (runGamemode(after, 1) != 1) fail("A vanilla /reload reset the rate-limit counter: " + after.chat());
                helper.succeed();
            } finally {
                removeRule(server);
                RateLimitPersistence.write();
            }
        });
    }

    /** world_save: an accepted use is not written on its own, the next world save writes it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_ratelimit_world_save")
    public static void worldSaveModeWritesWithTheWorld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_worldsave", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 5 3600");
            RateLimitPersistence.write();
            runGamemode(player, 1);
            if (historyFileMentions(player)) fail("world_save wrote the history on use instead of with the world.");
            // What /save-all runs. Not the command itself: a vanilla /reload earlier in the run rebuilds the
            // dispatcher with the integrated-server selection, which has no /save-all on a GameTest server.
            server.saveEverything(false, true, true);
            if (!historyFileMentions(player)) fail("A world save did not write the rate-limit history.");
        } finally {
            removeRule(server);
            RateLimitPersistence.write();
        }
        helper.succeed();
    }

    /** immediate: the history is on disk as soon as the use is accepted. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_ratelimit_immediate")
    public static void immediateModeWritesOnEachUse(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_r_immediate", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 5 3600");
            ServerCommands.run(server, "customperm ratelimit persistence gamemode immediate");
            RateLimitPersistence.write();
            if (historyFileMentions(player)) fail("Setup: the player must not be in the history yet.");
            runGamemode(player, 1);
            if (!historyFileMentions(player)) fail("immediate did not write the history right after the use.");
        } finally {
            removeRule(server);
            RateLimitPersistence.write();
        }
        helper.succeed();
    }

    private static boolean historyFileMentions(TestPlayer player) {
        Path file = RateLimitPersistence.file();
        if (file == null) fail("No rate-limit history file: RateLimitPersistence did not see the server start.");
        try {
            return Files.exists(file) && Files.readString(file).contains(player.uuid().toString());
        } catch (IOException e) {
            fail("Cannot read " + file + ": " + e.getMessage());
            return false;
        }
    }

    private static void whenDone(GameTestHelper helper, CompletableFuture<?> future, int ticksLeft, Runnable check) {
        if (!future.isDone() && ticksLeft > 0) {
            helper.runAfterDelay(10, () -> whenDone(helper, future, ticksLeft - 10, check));
            return;
        }
        if (!future.isDone()) fail("The vanilla reload did not finish in time.");
        // Command re-assertion over other handlers runs on the next tick after RegisterCommandsEvent.
        helper.runAfterDelay(5, check);
    }

    /** Runs /gamemode {@code times} times, alternating modes, and returns how many calls were refused. */
    private static int runGamemode(TestPlayer player, int times) {
        long before = refusals(player);
        for (int i = 0; i < times; i++) {
            player.type(i % 2 == 0 ? "gamemode creative" : "gamemode survival");
        }
        return (int) (refusals(player) - before);
    }

    private static long refusals(TestPlayer player) {
        return player.chat().stream().filter(line -> line.contains(REFUSED)).count();
    }

    private static void removeRule(MinecraftServer server) {
        if (CustomPerm.configManager.getRateLimits().rules.containsKey("gamemode")) {
            ServerCommands.run(server, "customperm ratelimit remove gamemode");
        }
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) fail("Expected output containing '" + fragment + "', got: " + lines);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
