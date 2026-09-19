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
 * The Help page. Its text ships with the client, so the server sends nothing but the order to open it; going
 * through the server like every other page keeps one way in, and the same access check.
 */
public record HelpData() implements GuiPageData {

    public static final HelpData INSTANCE = new HelpData();
    public static final StreamCodec<ByteBuf, HelpData> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public GuiPage page() {
        return GuiPage.HELP;
    }
}
