/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.spark;

import com.arcadia.customperm.command.RateLimiter;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of {@link SparkScenario}: the simulated players each scenario needs, all run on the server thread.
 * Only active in the {@code sparkScenario} run.
 */
@EventBusSubscriber(modid = "customperm")
public final class SparkLoad {

    /** The command the S03 players hammer, and the limits of the procedure: a rule of 1 per hour, 10 per day. */
    static final String LIMITED = "list";
    static final String GRADE = "spark_vip";
    static final int GRADE_LIMIT = 10;
    static final int HAMMER_PLAYERS = 8;
    private static final int HAMMER_EVERY_TICKS = 5;

    private static final List<TestPlayer> HAMMERING = new ArrayList<>();
    private static int tick;
    private static int typed;
    private static int refused;

    private SparkLoad() {
    }

    /** Grades, the exposed and limited command, an alias, and {@code known} players holding a grade. */
    static void seed(MinecraftServer server, UUID admin, int known) {
        run(server, "customperm grade create " + GRADE);
        run(server, "customperm grade create spark_known");
        run(server, "customperm grade addperm " + GRADE + " customperm.spark.node");
        // An exposed command is gated behind its node: without it the players are refused before any limit counts.
        run(server, "customperm grade addperm " + GRADE + " customperm.command." + LIMITED);
        run(server, "customperm command add " + LIMITED);
        run(server, "customperm ratelimit set " + LIMITED + " 1 3600");
        run(server, "customperm ratelimit grade " + LIMITED + " " + GRADE + " " + GRADE_LIMIT + "/1d");
        run(server, "customperm alias add spark_alias say spark");
        com.arcadia.customperm.gametest.support.Grants.allow(admin, "customperm.*");
        // A player is known once they hold something: join, take a grade, leave, like a real player base.
        for (int i = 1; i <= known; i++) {
            String name = String.format("spark_p%03d", i);
            try (TestPlayer player = TestPlayer.join(server.overworld(), name, 0)) {
                run(server, "customperm grade assign " + name + " spark_known");
                player.drain();
            }
        }
    }

    /**
     * S01, server half: an admin who opens every page, which records the vocabulary sent to them, then
     * disconnects. Afterwards neither their player nor their vocabulary may stay reachable.
     */
    static void adminVisit(MinecraftServer server) {
        try (TestPlayer admin = TestPlayer.admin(server.overworld(), "spark_admin", 4)) {
            for (GuiPage page : List.of(GuiPage.DASHBOARD, GuiPage.GRADES, GuiPage.PLAYERS, GuiPage.COMMANDS,
                    GuiPage.RATE_LIMITS, GuiPage.ALIASES)) {
                GuiRequestHandler.open(admin.player(), page);
                admin.drain();
                admin.clearReceived();
            }
        }
    }

    /** S03: players of the limited grade, joined now; they start typing the limited command on the next tick. */
    static void startHammering(MinecraftServer server) {
        for (int i = 1; i <= HAMMER_PLAYERS; i++) {
            String name = "spark_rl_" + i;
            TestPlayer player = TestPlayer.join(server.overworld(), name, 0);
            run(server, "customperm grade assign " + name + " " + GRADE);
            HAMMERING.add(player);
        }
    }

    /** Commands the S03 players typed, and how many the limiter refused. */
    static int typed() {
        return typed;
    }

    static int refused() {
        return refused;
    }

    static void stopHammering() {
        HAMMERING.forEach(TestPlayer::close);
        HAMMERING.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HAMMERING.isEmpty() || ++tick % HAMMER_EVERY_TICKS != 0) return;
        for (TestPlayer player : HAMMERING) {
            player.type(LIMITED);
            typed++;
            // The harness keeps every packet it is sent; dropped here so the heap shows the mod, not the harness.
            player.drain();
            if (player.chatContains("Rate limit reached")) refused++;
            player.clearReceived();
        }
    }

    /** Timestamps the limiter holds for the limited command, over every player. */
    static int historySize() {
        try {
            Field field = RateLimiter.class.getDeclaredField("HISTORY");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<UUID, Deque<Long>>> history = (Map<String, Map<UUID, Deque<Long>>>) field.get(null);
            return history.entrySet().stream()
                    .filter(e -> e.getKey().equals(LIMITED) || e.getKey().endsWith(":" + LIMITED))
                    .flatMap(e -> e.getValue().values().stream())
                    .mapToInt(Deque::size)
                    .sum();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RateLimiter.HISTORY moved; update the spark scenario", e);
        }
    }

    /**
     * Admins the server still remembers a vocabulary for. {@code size()} purges the entries whose player was
     * collected, as any access to the weak map does, so this is the count the next page request would see.
     * Server thread only.
     */
    static int sentVocabularies() {
        try {
            Field field = GuiRequestHandler.class.getDeclaredField("SENT_VOCABULARY");
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(null)).size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GuiRequestHandler.SENT_VOCABULARY moved; update the spark scenario", e);
        }
    }

    static List<String> run(MinecraftServer server, String command) {
        return ServerCommands.run(server, command);
    }
}
