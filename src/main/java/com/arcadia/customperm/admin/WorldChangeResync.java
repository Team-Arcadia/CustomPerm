/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Resends a player's command tree when they change world. The tree is built per player, not per world, so
 * a permission limited to a world would otherwise keep the verdict of the world the player came from until
 * something else resynced them: a command offered where it now refuses, or missing where it is allowed.
 *
 * <p>A respawn needs nothing here: vanilla sends a respawned player their command tree already, built in
 * the world they respawn in.
 *
 * <p>On the internal backend this runs only while some entry is limited to a context, so a server that uses
 * none pays nothing on a portal. LuckPerms keeps its own data and answers per world already; whether one of
 * its nodes is contextual is not cheap to know, so there the tree is always resent.
 */
public final class WorldChangeResync {

    private WorldChangeResync() {
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() != event.getTo()) resync(event.getEntity());
    }

    private static void resync(net.minecraft.world.entity.player.Player entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!CustomPerm.isLuckPermsActive() && !CustomPerm.configManager.getGrades().hasContextualEntries()) return;
        GradeAdmin.resyncPlayer(player.getServer(), player.getUUID());
    }
}
