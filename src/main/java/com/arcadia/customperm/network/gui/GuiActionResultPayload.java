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
 * Server -> client: outcome of a {@link GuiActionPayload}, shown on the status line of the open
 * screen. The message names what happened ("Exposed /gamemode"), never a bare "done", because it
 * can arrive after the admin has moved on to something else.
 */
public record GuiActionResultPayload(boolean success, String message) implements CustomPacketPayload {

    public static final Type<GuiActionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "gui_action_result"));

    public static final StreamCodec<ByteBuf, GuiActionResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GuiActionResultPayload::success,
            GuiCodecs.TEXT, GuiActionResultPayload::message,
            GuiActionResultPayload::new);

    public static GuiActionResultPayload ok(String message) {
        return new GuiActionResultPayload(true, message);
    }

    public static GuiActionResultPayload fail(String message) {
        return new GuiActionResultPayload(false, message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
