/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.CustomPerm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may read and who may change what in the admin interface. Server-side only; every packet
 * handler calls it again, whatever the client believes about its own permissions.
 */
public final class GuiAccess {

    private GuiAccess() {
    }

    /**
     * Reading any page: op level 2, like {@code /customperm}. Checked on a source rebuilt from the
     * player's real op level, never an elevated one (an alias runs its steps at level 4).
     */
    public static boolean canRead(ServerPlayer player) {
        return player.createCommandSourceStack().hasPermission(2);
    }

    /**
     * Writing to an area: its node on top of op level 2, with permission level 4 as the escape hatch,
     * so a server owner is never locked out by a node they would need the interface to grant.
     */
    public static boolean canEdit(ServerPlayer player, GuiArea area) {
        CommandSourceStack source = player.createCommandSourceStack();
        if (!source.hasPermission(2)) return false;
        if (source.hasPermission(4)) return true;
        try {
            return CustomPerm.permissions.hasGrantedNode(source, area.node());
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            CustomPerm.LOGGER.warn("[CustomPerm] Permission check for {} failed; denying interface writes.", area.node(), t);
            return false;
        }
    }

    /** One bit per area this player may write to. */
    public static int editMask(ServerPlayer player) {
        int mask = 0;
        for (GuiArea area : GuiArea.values()) {
            if (canEdit(player, area)) mask |= area.bit();
        }
        return mask;
    }
}
