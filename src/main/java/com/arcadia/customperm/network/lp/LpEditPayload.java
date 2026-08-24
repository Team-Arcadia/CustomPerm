/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.lp;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Client -> server: apply one LuckPerms mutation. This is the only packet in the mod that
 * writes to an external permission store, so it is also the only one with a real attack
 * surface — {@code LpRequestHandler} validates the operation name, the argument count and every
 * argument length, and re-checks the write permission server-side, before any LuckPerms call.
 * <p>
 * {@code refreshScope} / {@code refreshTarget} are the screen the admin is looking at. The
 * server replays them as a {@link RequestLpSyncPayload} after a successful edit so the editor
 * updates itself; without it the GUI would have to guess which of its own rows the mutation
 * touched, and would drift from the store on any partially-applied change.
 *
 * @param op   an {@link LpEditOp} name
 * @param args exactly {@link LpEditOp#arity()} arguments
 */
public record LpEditPayload(String op, List<String> args, String refreshScope, String refreshTarget)
        implements CustomPacketPayload {

    /** Per-argument cap. A permission node or a prefix well over this is a malformed packet. */
    public static final int MAX_ARG_LENGTH = 256;

    /** Hard cap on the argument list, independent of the per-op arity check. */
    public static final int MAX_ARGS = 8;

    public static final Type<LpEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "lp_edit"));

    // See LpDto.STRING_LIST — typed on ByteBuf because STRING_UTF8 is, and StreamCodec is
    // invariant in its buffer type.
    private static final StreamCodec<ByteBuf, List<String>> ARGS_CODEC =
            ByteBufCodecs.<ByteBuf, String>list(MAX_ARGS).apply(ByteBufCodecs.STRING_UTF8);

    public static final StreamCodec<RegistryFriendlyByteBuf, LpEditPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.op);
                        ARGS_CODEC.encode(buf, payload.args);
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.refreshScope);
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.refreshTarget);
                    },
                    buf -> new LpEditPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ARGS_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
