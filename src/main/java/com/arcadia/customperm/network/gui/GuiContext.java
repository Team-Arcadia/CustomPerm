/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.perm.BackendKind;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What every page needs besides its own data: which backend is active, what this admin may change,
 * and how many alerts are pending. Sent with each page so the navigation, the read-only state and
 * the alert badge are always current, whichever page the admin is on.
 *
 * @param editMask           one {@link GuiArea#bit()} per area this admin may write to
 * @param alertCount         active admin alerts, shown as a badge on the dashboard entry
 * @param luckPermsInstalled whether the LuckPerms mod is loaded, running or not
 * @param cluster            this server's cluster name, the members it hears and the parts it shares
 */
public record GuiContext(BackendKind backend, int editMask, int alertCount, boolean luckPermsInstalled,
                         ClusterView cluster) {

    public static final StreamCodec<ByteBuf, GuiContext> CODEC = StreamCodec.composite(
            GuiCodecs.enumByName(BackendKind.class), GuiContext::backend,
            ByteBufCodecs.VAR_INT, GuiContext::editMask,
            ByteBufCodecs.VAR_INT, GuiContext::alertCount,
            ByteBufCodecs.BOOL, GuiContext::luckPermsInstalled,
            ClusterView.CODEC, GuiContext::cluster,
            GuiContext::new);

    /** One outside a cluster. */
    public GuiContext(BackendKind backend, int editMask, int alertCount, boolean luckPermsInstalled) {
        this(backend, editMask, alertCount, luckPermsInstalled, ClusterView.NONE);
    }

    public boolean canEdit(GuiArea area) {
        return area.in(editMask);
    }

    public boolean luckPermsActive() {
        return backend == BackendKind.LUCKPERMS;
    }
}
