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
 * Resends a player's command tree when they change world or game mode. The tree is built per player, not per
 * world, so a permission limited to a world or a game mode would otherwise keep the verdict of where the
 * player came from until something else resynced them: a command offered where it now refuses, or missing
 * where it is allowed.
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

    /**
     * The event comes before the mode changes, and the tree must be built in the new one: the resync waits for
     * the server's next task pass, by then the mode is set. A cancelled change leaves it where it was, and
     * the resync then sends the same tree again.
     */
    public static void onChangedGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getCurrentGameMode() == event.getNewGameMode()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        var server = player.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> resync(player)));
    }

    private static void resync(net.minecraft.world.entity.player.Player entity) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (!CustomPerm.isLuckPermsActive() && !CustomPerm.configManager.getGrades().hasContextualEntries()) return;
        GradeAdmin.resyncPlayer(player.getServer(), player.getUUID());
    }
}
