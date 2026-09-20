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
    /** Most arguments carried per alias; matches {@code AliasesConfig.MAX_PARAMETERS}. */
    public static final int PARAMS_MAX = 8;
    /** Most choices carried per argument; matches {@code AliasesConfig.MAX_CHOICES}. */
    public static final int CHOICES_MAX = 32;

    /**
     * One argument of an alias.
     *
     * @param type           player, integer, word or text
     * @param defaultValue   substituted when the argument is left out; empty when it has none
     * @param min            lowest accepted value, {@link Integer#MIN_VALUE} when unbounded
     * @param max            highest accepted value, {@link Integer#MAX_VALUE} when unbounded
     * @param choices        the only accepted words, empty when any word is accepted
     * @param allowSelectors whether an entity selector is accepted in the value
     */
    public record Param(String name, String type, boolean optional, String defaultValue, int min, int max,
                        List<String> choices, boolean allowSelectors) {

        public static final StreamCodec<ByteBuf, Param> CODEC = StreamCodec.of(
                (buf, p) -> {
                    GuiCodecs.TEXT.encode(buf, p.name);
                    GuiCodecs.TEXT.encode(buf, p.type);
                    ByteBufCodecs.BOOL.encode(buf, p.optional);
                    GuiCodecs.TEXT.encode(buf, p.defaultValue);
                    ByteBufCodecs.INT.encode(buf, p.min);
                    ByteBufCodecs.INT.encode(buf, p.max);
                    GuiCodecs.list(GuiCodecs.TEXT, CHOICES_MAX).encode(buf, p.choices);
                    ByteBufCodecs.BOOL.encode(buf, p.allowSelectors);
                },
                buf -> new Param(
                        GuiCodecs.TEXT.decode(buf),
                        GuiCodecs.TEXT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        GuiCodecs.TEXT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        GuiCodecs.list(GuiCodecs.TEXT, CHOICES_MAX).decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        public boolean hasMin() {
            return min != Integer.MIN_VALUE;
        }

        public boolean hasMax() {
            return max != Integer.MAX_VALUE;
        }

        /** {@code <name>} when required, {@code [name]} when it may be left out. */
        public String slot() {
            return (optional ? "[" : "<") + name + (optional ? "]" : ">");
        }

        /** The range as the interface shows it and as an edit sends it back: {@code 1..5}, or empty. */
        public String range() {
            if (!hasMin() && !hasMax()) return "";
            return (hasMin() ? String.valueOf(min) : "") + ".." + (hasMax() ? String.valueOf(max) : "");
        }
    }

    /**
     * One alias.
     *
     * @param shadows      the alias replaces a real command of the same name while it exists
     * @param limitMax     executions allowed per window, 0 when no rule targets the alias
     * @param limitWindow  window in seconds, 0 when no rule
     * @param limitEnabled whether that rule is enforced
     */
    public record Alias(String name, List<String> steps, List<Param> params, boolean shadows, int limitMax,
                        int limitWindow, boolean limitEnabled) {

        public static final StreamCodec<ByteBuf, Alias> CODEC = StreamCodec.of(
                (buf, a) -> {
                    GuiCodecs.TEXT.encode(buf, a.name);
                    GuiCodecs.list(GuiCodecs.TEXT, STEPS_MAX).encode(buf, a.steps);
                    GuiCodecs.list(Param.CODEC, PARAMS_MAX).encode(buf, a.params);
                    ByteBufCodecs.BOOL.encode(buf, a.shadows);
                    ByteBufCodecs.VAR_INT.encode(buf, a.limitMax);
                    ByteBufCodecs.VAR_INT.encode(buf, a.limitWindow);
                    ByteBufCodecs.BOOL.encode(buf, a.limitEnabled);
                },
                buf -> new Alias(
                        GuiCodecs.TEXT.decode(buf),
                        GuiCodecs.list(GuiCodecs.TEXT, STEPS_MAX).decode(buf),
                        GuiCodecs.list(Param.CODEC, PARAMS_MAX).decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        public boolean hasLimit() {
            return limitMax > 0;
        }

        /** The arguments as tab completion shows them: {@code <player> [count]}. */
        public String usage() {
            StringBuilder usage = new StringBuilder();
            for (Param param : params) {
                if (usage.length() > 0) usage.append(' ');
                usage.append(param.slot());
            }
            return usage.toString();
        }
    }

    public static final StreamCodec<ByteBuf, AliasesData> CODEC =
            GuiCodecs.list(Alias.CODEC, GuiCodecs.SERVER_LIST_MAX).map(AliasesData::new, AliasesData::aliases);

    @Override
    public GuiPage page() {
        return GuiPage.ALIASES;
    }
}
