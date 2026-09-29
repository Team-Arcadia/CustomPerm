/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.cluster;

import com.arcadia.customperm.gametest.support.TestPlayer;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Players for {@code tools/cluster_smoke.py}, which drives cluster members over RCON: a check such as "refused on
 * the second member although the node is held" needs someone online to be refused. Only on the smoke members
 * ({@code -Dcustomperm.clusterSmoke=true}), never in the jar.
 *
 * <pre>
 *   cptest join &lt;name&gt;             a player joins this server, op level 0
 *   cptest leave &lt;name&gt;
 *   cptest can &lt;name&gt; &lt;command&gt;    whether the command is in their tree: "can true" or "can false"
 *   cptest run &lt;name&gt; &lt;command...&gt;  queues the command, typed as them on the next tick
 *   cptest result &lt;name&gt;           what they were told by it, or "(pending)"
 * </pre>
 */
@EventBusSubscriber(modid = "customperm")
public final class SmokePlayers {
    static final boolean ACTIVE = Boolean.getBoolean("customperm.clusterSmoke");
    private static final Map<String, TestPlayer> PLAYERS = new LinkedHashMap<>();
    private static final Map<String, String> RESULTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Queue<Runnable> QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private SmokePlayers() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!ACTIVE) return;
        event.getDispatcher().register(Commands.literal("cptest").requires(s -> s.hasPermission(4))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    // The offline UUID of the name, the same on every member, as a real player on an offline cluster.
                    PLAYERS.computeIfAbsent(name, n -> TestPlayer.join(ctx.getSource().getServer().overworld(),
                            new GameProfile(UUIDUtil.createOfflinePlayerUUID(n), n), 0, true));
                    return reply(ctx.getSource(), "joined " + name);
                })))
                .then(Commands.literal("leave").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    TestPlayer player = PLAYERS.remove(StringArgumentType.getString(ctx, "name"));
                    if (player != null) player.close();
                    return reply(ctx.getSource(), "left");
                })))
                .then(Commands.literal("can").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("command", StringArgumentType.word()).executes(ctx -> {
                            TestPlayer player = player(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
                            if (player == null) return 0;
                            player.drain();
                            return reply(ctx.getSource(), "can " + player.canUse(StringArgumentType.getString(ctx, "command")));
                        }))))
                // A command started from inside another one (this one, sent over RCON) is queued until that one ends,
                // so its answer cannot come back here: run queues it for the next tick, result reads what it said.
                .then(Commands.literal("run").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("command", StringArgumentType.greedyString()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            TestPlayer player = player(ctx.getSource(), name);
                            if (player == null) return 0;
                            String command = StringArgumentType.getString(ctx, "command");
                            RESULTS.remove(name);
                            QUEUE.add(() -> {
                                player.clearReceived();
                                player.type(command);
                                List<String> told = player.chat();
                                RESULTS.put(name, told.isEmpty() ? "(nothing)" : String.join(" | ", told));
                            });
                            return reply(ctx.getSource(), "queued");
                        }))))
                .then(Commands.literal("result").then(Commands.argument("name", StringArgumentType.word()).executes(ctx ->
                        reply(ctx.getSource(), RESULTS.getOrDefault(StringArgumentType.getString(ctx, "name"), "(pending)"))))));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Runnable next;
        while ((next = QUEUE.poll()) != null) next.run();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        PLAYERS.values().forEach(TestPlayer::close);
        PLAYERS.clear();
    }

    private static TestPlayer player(CommandSourceStack source, String name) {
        TestPlayer player = PLAYERS.get(name);
        if (player == null) source.sendFailure(Component.literal("no smoke player " + name + "; cptest join first"));
        return player;
    }

    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
