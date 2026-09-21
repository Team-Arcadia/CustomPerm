/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.lp.LpEditResultPayload;
import com.arcadia.customperm.network.lp.LpSyncPayload;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A real, connected {@link ServerPlayer} for GameTests, with a chosen permission level and a record of
 * every packet the server sends it.
 *
 * <p>Built like vanilla {@code GameTestHelper.makeMockServerPlayerInLevel()}: an in-memory
 * {@link EmbeddedChannel} stands in for the network, and {@code PlayerList.placeNewPlayer} runs the
 * real join sequence, NeoForge login event included. Nothing is decoded: packets written to the
 * channel are kept as objects and read back with {@link #drain()}.</p>
 *
 * <p>Always {@link #close()} it (try-with-resources): a player left in the list would receive the
 * broadcasts of later tests and skew their assertions.</p>
 *
 * <p>Known limit: after a vanilla {@code /reload} ({@code MinecraftServer.reloadResources}), a test player
 * that stayed connected during the reload remains in the player list with an open channel, but receives
 * no further packets. Tests that reload data packs reconnect their players afterwards. Whether a real
 * client behaves the same was not established; it is checked by hand in the test procedure.</p>
 */
public final class TestPlayer implements AutoCloseable {

    private final MinecraftServer server;
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private final List<Packet<?>> received = new ArrayList<>();

    /** Nodes granted by {@link #admin} or {@link #reader}, taken back on close. */
    private Grants access;

    private TestPlayer(MinecraftServer server, ServerPlayer player, EmbeddedChannel channel) {
        this.server = server;
        this.player = player;
        this.channel = channel;
    }

    /**
     * An operator who administers CustomPerm: {@code customperm.*}, granted before login. Being operator alone
     * gives no access; use {@link #join} for an operator without nodes.
     */
    public static TestPlayer admin(ServerLevel level, String name, int permissionLevel) {
        return withAccess(level, new GameProfile(UUID.randomUUID(), name), permissionLevel, true, "customperm.*");
    }

    public static TestPlayer admin(ServerLevel level, GameProfile profile, int permissionLevel, boolean modInstalledClientSide) {
        return withAccess(level, profile, permissionLevel, modInstalledClientSide, "customperm.*");
    }

    /** An operator who may enter /customperm and read every page, but change nothing: {@code customperm.admin}. */
    public static TestPlayer reader(ServerLevel level, String name, int permissionLevel) {
        return withAccess(level, new GameProfile(UUID.randomUUID(), name), permissionLevel, true, "customperm.admin");
    }

    private static TestPlayer withAccess(ServerLevel level, GameProfile profile, int permissionLevel,
                                         boolean modInstalledClientSide, String node) {
        if (CustomPerm.isLuckPermsActive()) LuckPermsTestSupport.loadUser(profile);
        Grants grants = Grants.allow(profile.getId(), node);
        TestPlayer player = join(level, profile, permissionLevel, modInstalledClientSide);
        player.access = grants;
        return player;
    }

    /** Joins a player with a random identity and CustomPerm installed client-side. */
    public static TestPlayer join(ServerLevel level, String name, int permissionLevel) {
        return join(level, new GameProfile(UUID.randomUUID(), name), permissionLevel, true);
    }

    /**
     * Joins a player with a fixed identity, so a test can disconnect and reconnect the "same" player.
     * {@code permissionLevel} replaces the ops list: 0 = regular player, 2 = operator, 4 = owner.
     *
     * <p>{@code modInstalledClientSide} decides what the channel negotiation would have agreed on.
     * NeoForge refuses to send CustomPerm payloads over a connection that did not negotiate them, and
     * this in-memory connection skips the handshake, so the channels are declared through NeoForge's
     * ad-hoc channel set. Without them the player behaves like a vanilla client.</p>
     */
    public static TestPlayer join(ServerLevel level, GameProfile profile, int permissionLevel,
                                  boolean modInstalledClientSide) {
        if (!profile.getName().matches("[A-Za-z0-9_]{1,16}")) {
            // Same rule as a real account; LuckPerms rejects anything else with a less obvious message.
            throw new IllegalArgumentException("Invalid test player name (1-16 letters, digits, _): " + profile.getName());
        }
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            protected int getPermissionLevel() {
                return permissionLevel;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        if (modInstalledClientSide) {
            ChannelAttributes.getOrCreateAdHocChannels(connection).addAll(List.of(
                    GuiPagePayload.TYPE.id(), GuiActionResultPayload.TYPE.id(),
                    com.arcadia.customperm.network.gui.GuiVocabularyPayload.TYPE.id(),
                    LpSyncPayload.TYPE.id(), LpEditResultPayload.TYPE.id()));
        }
        declareCompanionChannels(connection);
        if (CustomPerm.isLuckPermsActive()) {
            LuckPermsTestSupport.loadUser(profile);
        }
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return new TestPlayer(server, player, channel);
    }

    /**
     * Channels another mod on this server will write to on join, declared so the simulated connection accepts
     * them. Arcadia Lib registers its own without {@code optional()} and sends one from its join handler without
     * asking whether the channel is there, so a connection that does not declare them dies before any test runs.
     * This mirrors the deployment CustomPerm documents for cluster mode: both mods on the server and on the
     * client. It does not excuse the missing guard, it keeps the suite able to test anything at all.
     */
    private static void declareCompanionChannels(Connection connection) {
        if (!ModList.get().isLoaded("arcadia_lib")) return;
        ChannelAttributes.getOrCreateAdHocChannels(connection).addAll(List.of(
                ResourceLocation.fromNamespaceAndPath("arcadia_lib", "hub_permissions"),
                ResourceLocation.fromNamespaceAndPath("arcadia_lib", "open_hub")));
    }

    public ServerPlayer player() {
        return player;
    }

    public UUID uuid() {
        return player.getUUID();
    }

    public CommandSourceStack source() {
        return player.createCommandSourceStack();
    }

    /**
     * Runs a command exactly like the client typing it (errors are reported to the player, not
     * thrown). Read the outcome with {@link #chat()}.
     */
    public void type(String command) {
        server.getCommands().performPrefixedCommand(source(), command);
    }

    /** Runs a command through the dispatcher and returns its result; parse and permission errors throw. */
    public int exec(String command) throws CommandSyntaxException {
        return server.getCommands().getDispatcher().execute(command, source());
    }

    /** Whether the root command is usable, i.e. present in the command tree the client receives. */
    public boolean canUse(String rootCommand) {
        var node = server.getCommands().getDispatcher().getRoot().getChild(rootCommand);
        return node != null && node.canUse(source());
    }

    /** Moves every packet sent since the last call into the record and returns the whole record. */
    public List<Packet<?>> drain() {
        channel.runPendingTasks();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof Packet<?> packet) received.add(packet);
        }
        return received;
    }

    /** Forgets everything received so far, e.g. the join sequence. */
    public TestPlayer clearReceived() {
        drain();
        received.clear();
        return this;
    }

    /** Plain text of every system chat line received. */
    public List<String> chat() {
        return drain().stream()
                .filter(p -> p instanceof ClientboundSystemChatPacket)
                .map(p -> ((ClientboundSystemChatPacket) p).content().getString())
                .toList();
    }

    public boolean chatContains(String fragment) {
        return chat().stream().anyMatch(line -> line.contains(fragment));
    }

    /** How many full command trees the server pushed to this player. */
    public long commandTreesReceived() {
        return drain().stream().filter(p -> p instanceof ClientboundCommandsPacket).count();
    }

    /** Custom payloads (CustomPerm network packets) of the given type sent to this player. */
    public <T extends CustomPacketPayload> List<T> payloads(Class<T> type) {
        return drain().stream()
                .filter(p -> p instanceof ClientboundCustomPayloadPacket)
                .map(p -> ((ClientboundCustomPayloadPacket) p).payload())
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /**
     * The context a client-to-server payload from this player arrives with. Work is run immediately:
     * tests call handlers from the server thread already, which is where {@code enqueueWork} would
     * have put it.
     */
    public IPayloadContext payloadContext() {
        return new IPayloadContext() {
            @Override public ICommonPacketListener listener() { return player.connection; }
            @Override public Player player() { return player; }
            @Override public CompletableFuture<Void> enqueueWork(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }
            @Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }
            @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
            @Override public void handle(CustomPacketPayload payload) { throw new UnsupportedOperationException(); }
            @Override public void finishCurrentTask(ConfigurationTask.Type type) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** Disconnects the player through the normal logout path. */
    @Override
    public void close() {
        if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
            server.getPlayerList().remove(player);
        }
        channel.finishAndReleaseAll();
        if (access != null) access.close();
    }
}
