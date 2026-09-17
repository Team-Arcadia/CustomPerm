/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.perm.AdminAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may read and who may change what in the admin interface. Server-side only; every packet
 * handler calls it again, whatever the client believes about its own permissions.
 */
public final class GuiAccess {

    private GuiAccess() {
    }

    /** Reading any page: the {@code /customperm} gate ({@link AdminAccess}). */
    public static boolean canRead(ServerPlayer player) {
        return AdminAccess.canAdminister(player);
    }

    /**
     * Writing to an area: read access, then the area's node. An explicit ALLOW opens it, an explicit DENY
     * closes it even to the server owner, and a node that is not set opens it at permission level 4 only,
     * so a fresh install's owner is not locked out by a node they would need the interface to grant.
     */
    public static boolean canEdit(ServerPlayer player, GuiArea area) {
        if (!canRead(player)) return false;
        CommandSourceStack source = player.createCommandSourceStack();
        return switch (AdminAccess.explicit(source, area.node())) {
            case ALLOW -> true;
            case DENY -> false;
            case UNSET -> source.hasPermission(4);
        };
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
