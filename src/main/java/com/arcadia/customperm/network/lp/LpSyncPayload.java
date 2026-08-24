/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.lp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client reply to {@link RequestLpSyncPayload}, carrying one screen worth of
 * LuckPerms data. Also re-sent unsolicited after a successful edit, so the editor reflects the
 * store without a manual refresh.
 */
public record LpSyncPayload(LpDto.Snapshot snapshot) implements CustomPacketPayload {

    public static final Type<LpSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "lp_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LpSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> LpDto.Snapshot.CODEC.encode(buf, payload.snapshot),
                    buf -> new LpSyncPayload(LpDto.Snapshot.CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
