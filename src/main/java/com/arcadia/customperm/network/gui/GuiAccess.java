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

    /** Writing to an area: read access and the area's {@code customperm.manage.<area>} node, explicitly allowed. */
    public static boolean canEdit(ServerPlayer player, GuiArea area) {
        return AdminAccess.canManage(player, area.node());
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
