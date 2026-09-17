/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * LuckPerms editor page. Only an entry point: the editor's content travels on the dedicated LuckPerms
 * channel ({@code RequestLpSyncPayload} / {@code LpSyncPayload}), loaded per section because LuckPerms
 * storage reads are asynchronous and can be remote. The server never sends this page unless LuckPerms
 * is the active backend.
 *
 * @param section section to show first: {@link #GROUPS}, {@link #PLAYERS} or {@link #TRACKS}
 */
public record LuckPermsData(String section) implements GuiPageData {

    public static final String GROUPS = "groups";
    public static final String PLAYERS = "players";
    public static final String TRACKS = "tracks";

    public LuckPermsData {
        if (!section.equals(PLAYERS) && !section.equals(TRACKS)) section = GROUPS;
    }

    public static final StreamCodec<ByteBuf, LuckPermsData> CODEC =
            GuiCodecs.CLIENT_NAME.map(LuckPermsData::new, LuckPermsData::section);

    @Override
    public GuiPage page() {
        return GuiPage.LUCKPERMS;
    }
}
