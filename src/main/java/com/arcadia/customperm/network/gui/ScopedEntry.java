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

import java.util.List;

/**
 * One entry limited to a context, as a page carries it.
 *
 * @param context the stored form, such as {@code world=minecraft:the_nether}
 * @param kind    {@code "allow"}, {@code "deny"} or {@code "grade"}
 * @param value   the node, or the grade name
 */
public record ScopedEntry(String context, String kind, String value) {

    public static final StreamCodec<ByteBuf, ScopedEntry> CODEC = StreamCodec.composite(
            GuiCodecs.TEXT, ScopedEntry::context,
            GuiCodecs.TEXT, ScopedEntry::kind,
            GuiCodecs.TEXT, ScopedEntry::value,
            ScopedEntry::new);

    public static final StreamCodec<ByteBuf, List<ScopedEntry>> LIST = GuiCodecs.list(CODEC, GuiCodecs.SERVER_LIST_MAX);

    public boolean deny() {
        return "deny".equals(kind);
    }
}
