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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client outcome of an {@link LpEditPayload}. The editor shows it as a status line
 * rather than a chat message: the admin is looking at a screen, and a chat message posted
 * behind an open GUI is a message nobody reads.
 * <p>
 * LuckPerms mutations are asynchronous, so a rejection can arrive well after the click. The
 * message therefore always names what failed, never just "failed".
 */
public record LpEditResultPayload(boolean success, String message) implements CustomPacketPayload {

    public static final Type<LpEditResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "lp_edit_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LpEditResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.success);
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.message);
                    },
                    buf -> new LpEditResultPayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    public static LpEditResultPayload ok(String message) {
        return new LpEditResultPayload(true, message);
    }

    public static LpEditResultPayload fail(String message) {
        return new LpEditResultPayload(false, message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
