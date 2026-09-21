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

/** Server to client: what the interface's fields propose, sent ahead of a page when it changed. */
public record GuiVocabularyPayload(GuiVocabulary vocabulary) implements CustomPacketPayload {

    public static final Type<GuiVocabularyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "gui_vocabulary"));

    public static final StreamCodec<ByteBuf, GuiVocabularyPayload> STREAM_CODEC =
            GuiVocabulary.CODEC.map(GuiVocabularyPayload::new, GuiVocabularyPayload::vocabulary);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
