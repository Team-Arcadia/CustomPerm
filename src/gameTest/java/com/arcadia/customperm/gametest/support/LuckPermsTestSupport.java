/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.mojang.authlib.GameProfile;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

import java.util.Collection;
import java.util.UUID;

/**
 * The only test support class that touches the LuckPerms API. Kept apart so the GameTests still load
 * on a runtime without LuckPerms: this class is resolved only when a caller has already checked that
 * LuckPerms is active.
 */
public final class LuckPermsTestSupport {

    private LuckPermsTestSupport() {
    }

    /**
     * LuckPerms loads a user while the real client connects, before the player joins. A test player
     * skips that handshake, and LuckPerms' Brigadier requirement then denies every command to an
     * unknown user. Loading the user up front reproduces what a real login would have done.
     */
    static void loadUser(GameProfile profile) {
        LuckPermsProvider.get().getUserManager().loadUser(profile.getId(), profile.getName()).join();
    }

    /** Sets each node to {@code value} on the user, saves, and drops the permission cache. */
    public static void setNodes(UUID uuid, Collection<String> nodes, boolean value) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().loadUser(uuid).join();
        for (String node : nodes) {
            user.data().add(Node.builder(node).value(value).build());
        }
        api.getUserManager().saveUser(user).join();
        user.getCachedData().invalidate();
    }

    /** Removes every own node with one of these keys, whatever its value. */
    public static void clearNodes(UUID uuid, Collection<String> nodes) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().loadUser(uuid).join();
        user.data().clear(node -> nodes.contains(node.getKey()));
        api.getUserManager().saveUser(user).join();
        user.getCachedData().invalidate();
    }
}
