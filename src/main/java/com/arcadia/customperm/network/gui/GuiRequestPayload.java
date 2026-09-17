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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: "send me this page." Sent on navigation and by the refresh button; answered with
 * a {@link GuiPagePayload} that opens the page.
 *
 * @param page a {@link GuiPage#id()}
 */
public record GuiRequestPayload(String page) implements CustomPacketPayload {

    public static final Type<GuiRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "gui_request"));

    public static final StreamCodec<ByteBuf, GuiRequestPayload> STREAM_CODEC =
            GuiCodecs.CLIENT_NAME.map(GuiRequestPayload::new, GuiRequestPayload::page);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
