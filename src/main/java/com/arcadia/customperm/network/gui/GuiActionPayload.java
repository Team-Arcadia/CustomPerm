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

import java.util.List;

/**
 * Client -> server: perform one {@link GuiAction}. Nothing in it is trusted: the server resolves the
 * action by name, checks the argument count against {@link GuiAction#arity()}, re-checks op level 2
 * and the area's write node, and rate limits the admin before running anything.
 *
 * @param action a {@link GuiAction} name
 * @param args   exactly {@link GuiAction#arity()} arguments, each at most {@link GuiCodecs#CLIENT_ARG_MAX} characters
 * @param page   the page the admin is on ({@link GuiPage#id()}); refreshed after a successful action
 */
public record GuiActionPayload(String action, List<String> args, String page) implements CustomPacketPayload {

    public static final Type<GuiActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "gui_action"));

    public static final StreamCodec<ByteBuf, GuiActionPayload> STREAM_CODEC = StreamCodec.composite(
            GuiCodecs.CLIENT_NAME, GuiActionPayload::action,
            GuiCodecs.CLIENT_ARGS, GuiActionPayload::args,
            GuiCodecs.CLIENT_NAME, GuiActionPayload::page,
            GuiActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
