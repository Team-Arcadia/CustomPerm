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
 * Rate limits page: every configured rule, and the exposed commands and aliases that have none yet,
 * so a limit can be added without typing the target's name.
 *
 * @param unlimited exposed commands and aliases without a rule, sorted
 */
public record RateLimitsData(List<Rule> rules, List<String> unlimited) implements GuiPageData {

    /** What a rule's name currently designates; a rule on neither waits until its target exists. */
    public enum Target { EXPOSED_COMMAND, ALIAS, NONE }

    /**
     * One rule.
     *
     * @param immediate usage history written after every accepted use instead of with the world save
     */
    public record Rule(String name, int max, int windowSeconds, boolean enabled, boolean immediate, Target target) {

        public static final StreamCodec<ByteBuf, Rule> CODEC = StreamCodec.of(
                (buf, r) -> {
                    GuiCodecs.TEXT.encode(buf, r.name);
                    ByteBufCodecs.VAR_INT.encode(buf, r.max);
                    ByteBufCodecs.VAR_INT.encode(buf, r.windowSeconds);
                    ByteBufCodecs.BOOL.encode(buf, r.enabled);
                    ByteBufCodecs.BOOL.encode(buf, r.immediate);
                    GuiCodecs.enumByName(Target.class).encode(buf, r.target);
                },
                buf -> new Rule(
                        GuiCodecs.TEXT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        GuiCodecs.enumByName(Target.class).decode(buf)));
    }

    public static final StreamCodec<ByteBuf, RateLimitsData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Rule.CODEC, GuiCodecs.SERVER_LIST_MAX), RateLimitsData::rules,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), RateLimitsData::unlimited,
            RateLimitsData::new);

    @Override
    public GuiPage page() {
        return GuiPage.RATE_LIMITS;
    }
}
