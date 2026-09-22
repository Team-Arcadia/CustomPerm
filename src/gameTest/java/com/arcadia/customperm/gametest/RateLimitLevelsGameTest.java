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
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.cluster.Cluster;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.gametest.support.LuckPermsTestSupport;
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
 * A rate limit decided rule, then server, then grade, then player, with connected players. Players are level-2
 * operators so /gamemode runs without any exposure, as in {@link RateLimitGameTest}.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class RateLimitLevelsGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String REFUSED = "Rate limit reached for /gamemode";

    /** A grade's value replaces the rule for its players; a player's own value replaces their grade's. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aGradeThenAPlayerHaveTheLastWord(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        String grade = "cp_rl_vip";
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_rl_vip", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            GradeAdmin.create(grade);
            GradeAdmin.assign(server, player.player().getGameProfile(), grade);
            expect(ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " 3"),
                    "customperm.ratelimit.gamemode=3");

            player.clearReceived();
            if (runGamemode(player, 4) != 1) fail("The grade's 3 must replace the rule's 1: " + player.chat());
            expect(ServerCommands.run(server, "customperm ratelimit show gamemode cp_rl_vip"), "gets 3/1m");

            expect(ServerCommands.run(server, "customperm ratelimit player gamemode cp_rl_vip unlimited"),
                    "customperm.ratelimit.gamemode=unlimited");
            player.clearReceived();
            if (runGamemode(player, 5) != 0) fail("A player's unlimited must win over their grade: " + player.chat());
            expect(ServerCommands.run(server, "customperm ratelimit show gamemode cp_rl_vip"), "gets unlimited");
            expect(ServerCommands.run(server, "customperm ratelimit show gamemode"), "Grades: cp_rl_vip 3");
            expect(ServerCommands.run(server, "customperm ratelimit show gamemode"), "Players: cp_rl_vip unlimited");

            expect(ServerCommands.run(server, "customperm ratelimit player gamemode cp_rl_vip clear"), "Removed");
            player.clearReceived();
            if (runGamemode(player, 1) != 1) {
                fail("Back on the grade's 3, which the earlier uses already spent: " + player.chat());
            }
        } finally {
            GradeAdmin.delete(server, grade);
            removeRule(server);
        }
        helper.succeed();
    }

    /** A value that is not a limit is refused before anything is stored, and a value needs a rule to adjust. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aHolderValueIsCheckedBeforeItIsStored(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        String grade = "cp_rl_check";
        try {
            GradeAdmin.create(grade);
            expect(ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " 3"),
                    "No rate limit configured for /gamemode");
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            expect(ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " lots"), "is not a limit");
            expect(ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " 0/1h"), "is not a limit");
            if (CustomPerm.configManager.getGrades().grades.get(grade).meta.containsKey(RateLimitsConfig.metaKey("gamemode"))) {
                fail("A refused value must not be stored.");
            }
            expect(ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " 10/1h 2h"), "10/1h");
            Long expiry = CustomPerm.configManager.getGrades().grades.get(grade).metaExpiries.get(RateLimitsConfig.metaKey("gamemode"));
            if (expiry == null) fail("A duration after the value makes it temporary, like any entry.");
        } finally {
            GradeAdmin.delete(server, grade);
            removeRule(server);
        }
        helper.succeed();
    }

    /**
     * A member counting alone keeps a limit of its own; a shared counter refuses one, both ways. A grade's value
     * limited to this server wins over its value held everywhere.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aServerHasItsOwnLimitOnlyWhereItCountsAlone(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        var cluster = CustomPerm.configManager.getSettings().cluster;
        boolean enabled = cluster.enabled;
        String connection = cluster.connection;
        String name = cluster.serverName;
        String grade = "cp_rl_server";
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_rl_server", 2)) {
            cluster.enabled = true;
            cluster.connection = "direct";
            cluster.serverName = "alpha";
            if (!"alpha".equals(Cluster.identity())) fail("Setup: this member must be named alpha.");
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            expect(ServerCommands.run(server, "customperm ratelimit server gamemode here 3 60"), "3/1m on alpha");

            player.clearReceived();
            if (runGamemode(player, 4) != 1) fail("alpha's own 3 must replace the rule's 1: " + player.chat());

            expect(ServerCommands.run(server, "customperm ratelimit scope gamemode network"), "A shared counter means one limit");
            if (!"server".equals(CustomPerm.configManager.getRateLimits().get("gamemode").scope)) fail("A refused scope was stored.");

            ServerCommands.run(server, "customperm ratelimit set gamemode 2 60");
            if (CustomPerm.configManager.getRateLimits().get("gamemode").perServer == null) {
                fail("Redefining the rule's numbers must keep each server's own limit.");
            }
            expect(ServerCommands.run(server, "customperm ratelimit server gamemode alpha clear"), "follows the limit");
            expect(ServerCommands.run(server, "customperm ratelimit scope gamemode network"), "counted once");
            expect(ServerCommands.run(server, "customperm ratelimit server gamemode here 3 60"), "shares the counter");

            GradeAdmin.create(grade);
            GradeAdmin.assign(server, player.player().getGameProfile(), grade);
            ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " 2");
            ServerCommands.run(server, "customperm ratelimit grade gamemode " + grade + " 7 server=here");
            expect(ServerCommands.run(server, "customperm ratelimit show gamemode cp_rl_server"), "gets 7/1m");
        } finally {
            cluster.enabled = enabled;
            cluster.connection = connection;
            cluster.serverName = name;
            GradeAdmin.delete(server, grade);
            removeRule(server);
        }
        helper.succeed();
    }

    /** Redefining a rule's numbers used to drop its list of servers, making a limit for one member active everywhere. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void redefiningARuleKeepsItsServers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            CustomPerm.configManager.getRateLimits().get("gamemode").servers = new java.util.ArrayList<>(List.of("hub"));
            ServerCommands.run(server, "customperm ratelimit set gamemode 2 60");
            var rule = CustomPerm.configManager.getRateLimits().get("gamemode");
            if (rule.servers == null || !rule.servers.equals(List.of("hub"))) {
                fail("The list of servers was lost: " + rule.servers);
            }
            if (rule.maxExecutions != 2) fail("The new numbers were not applied.");
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

    /** Under LuckPerms, its own meta gives the player's limit: nothing to configure on CustomPerm's side. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void luckPermsMetaGivesThePlayersLimit(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        String key = RateLimitsConfig.metaKey("gamemode");
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_rl_lp", 2)) {
            ServerCommands.run(server, "customperm ratelimit set gamemode 1 60");
            LuckPermsTestSupport.setMeta(player.player().getUUID(), key, "3/1h");
            player.clearReceived();
            if (runGamemode(player, 4) != 1) fail("LuckPerms' meta 3/1h must replace the rule's 1: " + player.chat());
            expect(ServerCommands.run(server, "customperm ratelimit grade gamemode default 3"), "use /lp instead");
            expect(ServerCommands.run(server, "customperm ratelimit show gamemode"), "lp group vip meta set " + key);
            LuckPermsTestSupport.clearMeta(player.player().getUUID(), key);
        } finally {
            removeRule(server);
        }
        helper.succeed();
    }

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
