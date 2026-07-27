/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server marker: "send me a fresh {@link GuiSyncPayload}." Sent when a TesseraUI
 * screen opens and when the admin presses its Refresh button.
 */
public record RequestGuiSyncPayload() implements CustomPacketPayload {

    // createType(String) resolves its argument as a bare path under the "minecraft" namespace,
    // not "namespace:path" — build the Type explicitly to get our own "customperm" namespace.
    public static final Type<RequestGuiSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "request_gui_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGuiSyncPayload> STREAM_CODEC =
            StreamCodec.<RegistryFriendlyByteBuf, RequestGuiSyncPayload>unit(new RequestGuiSyncPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
