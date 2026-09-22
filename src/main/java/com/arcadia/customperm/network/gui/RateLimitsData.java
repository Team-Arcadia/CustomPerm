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
public record RateLimitsData(List<Rule> rules, List<String> unlimited, boolean holdersInLuckPerms) implements GuiPageData {

    /** Levels sent per rule, at most. */
    public static final int LEVELS_MAX = 256;

    public RateLimitsData(List<Rule> rules, List<String> unlimited) {
        this(rules, unlimited, false);
    }

    /**
     * One level of a rule: a member's own limit ({@code SERVER}), or a grade's or a player's value.
     *
     * @param kind        {@code SERVER}, {@code GRADE} or {@code PLAYER}
     * @param context     where a grade's or a player's value applies, empty for everywhere
     * @param secondsLeft time left of a temporary value, 0 when it does not expire
     */
    public record Level(String kind, String holder, String value, String context, long secondsLeft) {
        public static final StreamCodec<ByteBuf, Level> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Level::kind,
                GuiCodecs.TEXT, Level::holder,
                GuiCodecs.TEXT, Level::value,
                GuiCodecs.TEXT, Level::context,
                ByteBufCodecs.VAR_LONG, Level::secondsLeft,
                Level::new);
    }

    /** What a rule's name currently designates; a rule on neither waits until its target exists. */
    public enum Target { EXPOSED_COMMAND, ALIAS, NONE }

    /**
     * One rule.
     *
     * @param immediate usage history written after every accepted use instead of with the world save
     * @param scope     who shares the budget in cluster mode: server, network or server names
     */
    public record Rule(String name, int max, int windowSeconds, boolean enabled, boolean immediate, Target target,
                       String scope, List<String> servers, List<Level> levels) {

        /** One enforced on every member. */
        public Rule(String name, int max, int windowSeconds, boolean enabled, boolean immediate, Target target,
                    String scope) {
            this(name, max, windowSeconds, enabled, immediate, target, scope, List.of(), List.of());
        }

        public Rule(String name, int max, int windowSeconds, boolean enabled, boolean immediate, Target target,
                    String scope, List<String> servers) {
            this(name, max, windowSeconds, enabled, immediate, target, scope, servers, List.of());
        }


        public static final StreamCodec<ByteBuf, Rule> CODEC = StreamCodec.of(
                (buf, r) -> {
                    GuiCodecs.TEXT.encode(buf, r.name);
                    ByteBufCodecs.VAR_INT.encode(buf, r.max);
                    ByteBufCodecs.VAR_INT.encode(buf, r.windowSeconds);
                    ByteBufCodecs.BOOL.encode(buf, r.enabled);
                    ByteBufCodecs.BOOL.encode(buf, r.immediate);
                    GuiCodecs.enumByName(Target.class).encode(buf, r.target);
                    GuiCodecs.TEXT.encode(buf, r.scope);
                    GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX).encode(buf, r.servers);
                    GuiCodecs.list(Level.CODEC, LEVELS_MAX).encode(buf, r.levels);
                },
                buf -> new Rule(
                        GuiCodecs.TEXT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        GuiCodecs.enumByName(Target.class).decode(buf),
                        GuiCodecs.TEXT.decode(buf),
                        GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX).decode(buf),
                        GuiCodecs.list(Level.CODEC, LEVELS_MAX).decode(buf)));
    }

    public static final StreamCodec<ByteBuf, RateLimitsData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Rule.CODEC, GuiCodecs.SERVER_LIST_MAX), RateLimitsData::rules,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), RateLimitsData::unlimited,
            ByteBufCodecs.BOOL, RateLimitsData::holdersInLuckPerms,
            RateLimitsData::new);

    @Override
    public GuiPage page() {
        return GuiPage.RATE_LIMITS;
    }
}
