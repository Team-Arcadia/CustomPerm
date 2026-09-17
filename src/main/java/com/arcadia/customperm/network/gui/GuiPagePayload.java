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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: one admin page with its data. The server is the authority for what the admin
 * sees: the client never builds a page from local state, it only draws what this payload carries.
 *
 * @param open true when the admin asked for this page (command, navigation): the client opens it.
 *             False for a refresh the server pushes after an action: the client applies it only if
 *             that page is still on screen, so a late refresh never reopens a closed interface.
 */
public record GuiPagePayload(boolean open, GuiContext context, GuiPageData data) implements CustomPacketPayload {

    public static final Type<GuiPagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "gui_page"));

    public static final StreamCodec<ByteBuf, GuiPagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GuiPagePayload::open,
            GuiContext.CODEC, GuiPagePayload::context,
            GuiPageData.CODEC, GuiPagePayload::data,
            GuiPagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
