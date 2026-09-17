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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Shared codecs and decode caps for the admin interface payloads.
 *
 * <p>Caps are enforced while decoding, before any object is built: a string or list over its cap
 * fails the packet instead of allocating what the sender claims. Client-to-server caps are tight
 * (a real client never sends more than a name and a few short arguments). Server-to-client lists
 * are capped too; snapshot builders stay under those caps themselves, so a large server shows a
 * truncated list rather than failing to encode.
 *
 * <p>Codecs are typed on {@link ByteBuf}: none of these payloads needs registry access, and it keeps
 * them round-trip testable with a plain buffer.
 */
public final class GuiCodecs {

    /** Longest identifier a client may send: page id, action name. */
    public static final int CLIENT_NAME_MAX = 48;
    /** Longest action argument: a permission node, a command line, a player name. */
    public static final int CLIENT_ARG_MAX = 256;
    /** Most arguments an action takes. */
    public static final int CLIENT_ARGS_MAX = 8;

    /** Most rows any server-to-client list carries. */
    public static final int SERVER_LIST_MAX = 4096;
    /** Most alerts carried by the dashboard. */
    public static final int ALERTS_MAX = 16;

    public static final StreamCodec<ByteBuf, String> CLIENT_NAME = ByteBufCodecs.stringUtf8(CLIENT_NAME_MAX);
    public static final StreamCodec<ByteBuf, String> CLIENT_ARG = ByteBufCodecs.stringUtf8(CLIENT_ARG_MAX);
    public static final StreamCodec<ByteBuf, List<String>> CLIENT_ARGS =
            ByteBufCodecs.<ByteBuf, String>list(CLIENT_ARGS_MAX).apply(CLIENT_ARG);

    public static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.STRING_UTF8;

    private GuiCodecs() {
    }

    public static <V> StreamCodec<ByteBuf, List<V>> list(StreamCodec<ByteBuf, V> element, int max) {
        return ByteBufCodecs.<ByteBuf, V>list(max).apply(element);
    }

    /** Enum by constant name. An unknown name fails the packet: both sides share one protocol version. */
    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumByName(Class<E> type) {
        return StreamCodec.of(
                (buf, value) -> ByteBufCodecs.STRING_UTF8.encode(buf, value.name()),
                buf -> {
                    String name = ByteBufCodecs.stringUtf8(64).decode(buf);
                    try {
                        return Enum.valueOf(type, name);
                    } catch (IllegalArgumentException e) {
                        throw new DecoderException("Unknown " + type.getSimpleName() + " '" + name + "'");
                    }
                });
    }
}
