/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.arcadia.customperm.perm.lp.LuckPermsAdminService;
import com.mojang.authlib.GameProfile;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.track.Track;
import net.minecraft.gametest.framework.GameTestAssertException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The only test support class that touches the LuckPerms API. Kept apart so the GameTests still load
 * on a runtime without LuckPerms: this class is resolved only when a caller has already checked that
 * LuckPerms is active.
 *
 * <p>LuckPerms storage calls are asynchronous. Tests wait for them with a bounded timeout: if a future
 * ever needed the server thread the test is blocking, the test fails instead of hanging the run.</p>
 */
public final class LuckPermsTestSupport {

    private static final long TIMEOUT_SECONDS = 10;

    private LuckPermsTestSupport() {
    }

    /**
     * LuckPerms loads a user while the real client connects, before the player joins. A test player
     * skips that handshake, and LuckPerms' Brigadier requirement then denies every command to an
     * unknown user. Loading the user up front reproduces what a real login would have done.
     */
    static void loadUser(GameProfile profile) {
        await(api().getUserManager().loadUser(profile.getId(), profile.getName()));
    }

    /** Sets each node to {@code value} on the user, saves, and drops the permission cache. */
    public static void setNodes(UUID uuid, Collection<String> nodes, boolean value) {
        User user = await(api().getUserManager().loadUser(uuid));
        for (String node : nodes) {
            user.data().add(Node.builder(node).value(value).build());
        }
        await(api().getUserManager().saveUser(user));
        user.getCachedData().invalidate();
    }

    /** Sets one node on the user, limited to one LuckPerms context such as {@code dimension-type=the_nether}. */
    public static void setContextNode(UUID uuid, String node, boolean value, String key, String contextValue) {
        User user = await(api().getUserManager().loadUser(uuid));
        user.data().add(Node.builder(node).value(value).withContext(key, contextValue).build());
        await(api().getUserManager().saveUser(user));
        user.getCachedData().invalidate();
    }

    /** Removes every own node with one of these keys, whatever its value. */
    public static void clearNodes(UUID uuid, Collection<String> nodes) {
        User user = await(api().getUserManager().loadUser(uuid));
        user.data().clear(node -> nodes.contains(node.getKey()));
        await(api().getUserManager().saveUser(user));
        user.getCachedData().invalidate();
    }

    /** Makes a group inherit another for {@code seconds}: the editor offers durations on players only. */
    public static void addTemporaryParent(String group, String parent, long seconds) {
        await(api().getGroupManager().loadGroup(group)).ifPresent(loaded -> {
            loaded.data().add(net.luckperms.api.node.types.InheritanceNode.builder(parent)
                    .expiry(java.time.Duration.ofSeconds(seconds)).build());
            await(api().getGroupManager().saveGroup(loaded));
        });
    }

    /** Gives a group a display name for {@code seconds}: the editor sets display names for good only. */
    public static void addTemporaryDisplayName(String group, String text, long seconds) {
        await(api().getGroupManager().loadGroup(group)).ifPresent(loaded -> {
            loaded.data().add(net.luckperms.api.node.types.DisplayNameNode.builder(text)
                    .expiry(java.time.Duration.ofSeconds(seconds)).build());
            await(api().getGroupManager().saveGroup(loaded));
        });
    }

    /** Removes every own node of a group with one of these keys, whatever its value. */
    public static void clearGroupNodes(String group, Collection<String> nodes) {
        await(api().getGroupManager().loadGroup(group)).ifPresent(loaded -> {
            loaded.data().clear(node -> nodes.contains(node.getKey()));
            await(api().getGroupManager().saveGroup(loaded));
        });
    }

    /** Own nodes of a group as {@code key=value[contexts]} with {@code @expiring} for temporary ones. */
    public static List<String> groupNodes(String group) {
        return await(api().getGroupManager().loadGroup(group)).map(LuckPermsTestSupport::describe).orElse(List.of());
    }

    public static List<String> userNodes(UUID uuid) {
        return describe(await(api().getUserManager().loadUser(uuid)));
    }

    public static boolean groupExists(String group) {
        return await(api().getGroupManager().loadGroup(group)).isPresent();
    }

    public static String primaryGroup(UUID uuid) {
        return await(api().getUserManager().loadUser(uuid)).getPrimaryGroup();
    }

    public static List<String> trackGroups(String track) {
        return await(api().getTrackManager().loadTrack(track)).map(Track::getGroups).map(List::copyOf).orElse(List.of());
    }

    /** Deletes the groups and tracks a test created, ignoring the ones that do not exist. */
    public static void cleanup(Collection<String> groups, Collection<String> tracks) {
        for (String name : tracks) {
            Optional<Track> track = await(api().getTrackManager().loadTrack(name));
            track.ifPresent(t -> await(api().getTrackManager().deleteTrack(t)));
        }
        for (String name : groups) {
            Optional<Group> group = await(api().getGroupManager().loadGroup(name));
            group.ifPresent(g -> await(api().getGroupManager().deleteGroup(g)));
        }
    }

    /**
     * Applies one editor operation through {@link LuckPermsAdminService}, the code the GUI packet
     * handler calls, and returns {@code "OK: summary"} or {@code "FAIL: message"}.
     */
    public static String apply(com.arcadia.customperm.network.lp.LpEditOp op, String... args) {
        CompletableFuture<String> outcome = LuckPermsAdminService.apply(op, List.of(args))
                .handle((summary, error) -> error == null ? "OK: " + summary : "FAIL: " + message(error));
        return await(outcome);
    }

    private static String message(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause instanceof LuckPermsAdminService.LpEditException ? cause.getMessage()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static List<String> describe(PermissionHolder holder) {
        return holder.getNodes().stream()
                .map(node -> node.getKey() + "=" + node.getValue()
                        + (node.getContexts().isEmpty() ? "" : "[" + LuckPermsAdminService.formatContexts(node.getContexts()) + "]")
                        + (node.hasExpiry() ? "@expiring" : ""))
                .sorted()
                .toList();
    }

    private static LuckPerms api() {
        return LuckPermsProvider.get();
    }

    /** Waits for one LuckPerms answer by wall clock: GameTest ticks do not advance its executors. */
    public static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new GameTestAssertException("LuckPerms call did not complete within " + TIMEOUT_SECONDS + "s: " + e);
        }
    }
}
