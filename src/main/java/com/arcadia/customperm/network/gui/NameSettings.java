/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.config.SettingsConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Whether names are decorated and how, carried by the pages that edit prefixes so their preview shows
 * what players will see, and says so when nothing shows yet.
 */
public record NameSettings(boolean decorate, String format, Stack prefix, Stack suffix) {

    public static final StreamCodec<ByteBuf, NameSettings> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NameSettings::decorate,
            GuiCodecs.TEXT, NameSettings::format,
            Stack.CODEC, NameSettings::prefix,
            Stack.CODEC, NameSettings::suffix,
            NameSettings::new);

    /** How several prefixes show, as {@code settings.json} says; see {@link SettingsConfig.ChatStack}. */
    public record Stack(boolean stacked, int limit, String start, String middle, String end) {
        public static final StreamCodec<ByteBuf, Stack> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Stack::stacked,
                ByteBufCodecs.VAR_INT, Stack::limit,
                GuiCodecs.TEXT, Stack::start,
                GuiCodecs.TEXT, Stack::middle,
                GuiCodecs.TEXT, Stack::end,
                Stack::new);

        public static Stack of(SettingsConfig.ChatStack stack) {
            return new Stack(stack.stacked(), stack.limit, stack.start, stack.middle, stack.end);
        }

        /** Back into the settings shape, for {@code chat/ChatStack} to format the preview with. */
        public SettingsConfig.ChatStack settings() {
            SettingsConfig.ChatStack stack = new SettingsConfig.ChatStack();
            stack.mode = stacked ? SettingsConfig.ChatStack.STACKED : SettingsConfig.ChatStack.HIGHEST;
            stack.limit = limit;
            stack.start = start;
            stack.middle = middle;
            stack.end = end;
            return stack;
        }
    }
}
