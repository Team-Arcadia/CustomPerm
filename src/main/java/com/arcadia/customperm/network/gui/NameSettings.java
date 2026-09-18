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

/**
 * Whether names are decorated and how, carried by the pages that edit prefixes so their preview shows
 * what players will see, and says so when nothing shows yet.
 */
public record NameSettings(boolean decorate, String format) {

    public static final StreamCodec<ByteBuf, NameSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NameSettings::decorate,
            GuiCodecs.TEXT, NameSettings::format,
            NameSettings::new);
}
