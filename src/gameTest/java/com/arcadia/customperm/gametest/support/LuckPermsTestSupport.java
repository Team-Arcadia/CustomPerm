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
import net.luckperms.api.LuckPermsProvider;

/**
 * The only test class that touches the LuckPerms API. Kept apart so the GameTests still load on a
 * runtime without LuckPerms (CI): this class is resolved only when a caller has already checked that
 * LuckPerms is active.
 */
final class LuckPermsTestSupport {

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
}
