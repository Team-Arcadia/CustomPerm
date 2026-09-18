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

import java.util.List;

/**
 * How long a temporary entry has left, in seconds, at the moment the page was built. Seconds left rather
 * than the moment it ends, so the page never depends on the client's clock agreeing with the server's.
 *
 * @param key {@code "allow:<node>"} or {@code "deny:<node>"}
 */
public record Remaining(String key, long seconds) {

    public static final StreamCodec<ByteBuf, Remaining> CODEC = StreamCodec.composite(
            GuiCodecs.TEXT, Remaining::key,
            ByteBufCodecs.VAR_LONG, Remaining::seconds,
            Remaining::new);

    public static final StreamCodec<ByteBuf, List<Remaining>> LIST = GuiCodecs.list(CODEC, GuiCodecs.SERVER_LIST_MAX);

    /** Seconds left on {@code kind:key} in {@code timers}, 0 for a permanent entry. */
    public static long of(List<Remaining> timers, String kind, String key) {
        String wanted = kind + ":" + key;
        for (Remaining timer : timers) {
            if (timer.key().equals(wanted)) return timer.seconds();
        }
        return 0;
    }
}
