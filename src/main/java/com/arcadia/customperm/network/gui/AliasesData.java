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
 * Aliases page: every alias with its steps, whether it shadows a real command, and the rate limit
 * that applies to it, if any.
 */
public record AliasesData(List<Alias> aliases) implements GuiPageData {

    /** Most steps carried per alias; matches the limit the server enforces when editing. */
    public static final int STEPS_MAX = 64;

    /**
     * One alias.
     *
     * @param shadows      the alias replaces a real command of the same name while it exists
     * @param limitMax     executions allowed per window, 0 when no rule targets the alias
     * @param limitWindow  window in seconds, 0 when no rule
     * @param limitEnabled whether that rule is enforced
     */
    public record Alias(String name, List<String> steps, boolean shadows, int limitMax, int limitWindow,
                        boolean limitEnabled) {

        public static final StreamCodec<ByteBuf, Alias> CODEC = StreamCodec.of(
                (buf, a) -> {
                    GuiCodecs.TEXT.encode(buf, a.name);
                    GuiCodecs.list(GuiCodecs.TEXT, STEPS_MAX).encode(buf, a.steps);
                    ByteBufCodecs.BOOL.encode(buf, a.shadows);
                    ByteBufCodecs.VAR_INT.encode(buf, a.limitMax);
                    ByteBufCodecs.VAR_INT.encode(buf, a.limitWindow);
                    ByteBufCodecs.BOOL.encode(buf, a.limitEnabled);
                },
                buf -> new Alias(
                        GuiCodecs.TEXT.decode(buf),
                        GuiCodecs.list(GuiCodecs.TEXT, STEPS_MAX).decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        public boolean hasLimit() {
            return limitMax > 0;
        }
    }

    public static final StreamCodec<ByteBuf, AliasesData> CODEC =
            GuiCodecs.list(Alias.CODEC, GuiCodecs.SERVER_LIST_MAX).map(AliasesData::new, AliasesData::aliases);

    @Override
    public GuiPage page() {
        return GuiPage.ALIASES;
    }
}
