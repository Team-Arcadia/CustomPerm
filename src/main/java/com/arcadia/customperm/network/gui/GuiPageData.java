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
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.StreamCodec;

/**
 * The data of one admin page. Each page has its own record and codec; this interface tags the
 * record with its page on the wire so {@link GuiPagePayload} needs a single channel for all pages.
 */
public sealed interface GuiPageData permits DashboardData, CommandsData {

    GuiPage page();

    StreamCodec<ByteBuf, GuiPageData> CODEC = StreamCodec.of(
            (buf, data) -> {
                GuiCodecs.CLIENT_NAME.encode(buf, data.page().id());
                switch (data) {
                    case DashboardData d -> DashboardData.CODEC.encode(buf, d);
                    case CommandsData d -> CommandsData.CODEC.encode(buf, d);
                }
            },
            buf -> {
                String id = GuiCodecs.CLIENT_NAME.decode(buf);
                GuiPage page = GuiPage.fromId(id);
                if (page == null) throw new DecoderException("Unknown admin page '" + id + "'");
                return switch (page) {
                    case DASHBOARD -> DashboardData.CODEC.decode(buf);
                    case COMMANDS -> CommandsData.CODEC.decode(buf);
                };
            });
}
