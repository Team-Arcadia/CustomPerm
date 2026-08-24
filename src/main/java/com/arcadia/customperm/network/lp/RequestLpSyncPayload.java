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
 * Client -> server: "send me the LuckPerms data for this screen." Unlike
 * {@code RequestGuiSyncPayload}, which is a marker for one fixed snapshot, the LuckPerms editor
 * is paged — a group detail screen has no use for every user, and shipping the whole permission
 * store to open one screen would be a needlessly large packet on a populated server.
 *
 * @param scope  one of the {@code SCOPE_*} constants
 * @param target group name, track name or user UUID for the detail scopes; ignored otherwise.
 *               For {@link #SCOPE_USERS} this doubles as a search term.
 */
public record RequestLpSyncPayload(String scope, String target) implements CustomPacketPayload {

    public static final String SCOPE_GROUPS = "groups";
    public static final String SCOPE_GROUP = "group";
    public static final String SCOPE_USERS = "users";
    public static final String SCOPE_USER = "user";
    public static final String SCOPE_TRACKS = "tracks";

    /** Caps the username search term; also the length the server truncates to before lookup. */
    public static final int MAX_TARGET_LENGTH = 64;

    public static final Type<RequestLpSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "request_lp_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLpSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.scope);
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.target);
                    },
                    buf -> new RequestLpSyncPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    /** Convenience for the scopes that address nothing in particular. */
    public static RequestLpSyncPayload of(String scope) {
        return new RequestLpSyncPayload(scope, "");
    }

    /** The scope string echoed back in {@code LpDto.Snapshot.scope}, so replies can be matched. */
    public String scopeKey() {
        return target.isEmpty() ? scope : scope + ":" + target;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
