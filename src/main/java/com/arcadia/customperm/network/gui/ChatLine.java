/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.Expiry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * One prefix or suffix of a grade or a player, as a page shows it: its priority, its raw text with codes,
 * the seconds it has left, 0 when it is for good, and the world it is limited to, empty for everywhere.
 */
public record ChatLine(boolean suffix, int priority, String text, long remaining, String context) {

    public static final StreamCodec<ByteBuf, ChatLine> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChatLine::suffix,
            ByteBufCodecs.VAR_INT, ChatLine::priority,
            GuiCodecs.TEXT, ChatLine::text,
            ByteBufCodecs.VAR_LONG, ChatLine::remaining,
            GuiCodecs.TEXT, ChatLine::context,
            ChatLine::new);

    public static final StreamCodec<ByteBuf, List<ChatLine>> LIST = GuiCodecs.list(CODEC, GuiCodecs.SERVER_LIST_MAX);

    /**
     * A holder's prefixes then suffixes, highest priority first, then those limited to a world, by world,
     * leaving out what has run out.
     */
    public static List<ChatLine> of(List<GradesConfig.ChatEntry> prefixes, List<GradesConfig.ChatEntry> suffixes,
                                    java.util.Map<String, ? extends GradesConfig.Scoped> scopes) {
        List<ChatLine> lines = new ArrayList<>();
        long now = Expiry.now();
        add(lines, prefixes, false, now, "");
        add(lines, suffixes, true, now, "");
        if (scopes != null) new java.util.TreeMap<>(scopes).forEach((context, scope) -> {
            add(lines, scope.prefixes, false, now, context);
            add(lines, scope.suffixes, true, now, context);
        });
        return lines.size() > GuiCodecs.SERVER_LIST_MAX ? lines.subList(0, GuiCodecs.SERVER_LIST_MAX) : lines;
    }

    private static void add(List<ChatLine> lines, List<GradesConfig.ChatEntry> entries, boolean suffix, long now,
                            String context) {
        if (entries == null) return;
        entries.stream().filter(entry -> entry.alive(now))
                .sorted(java.util.Comparator.comparingInt((GradesConfig.ChatEntry e) -> e.priority).reversed())
                .forEach(entry -> lines.add(new ChatLine(suffix, entry.priority, entry.text,
                        entry.expires > 0 ? entry.expires - now : 0, context)));
    }

    /**
     * The texts of one kind that apply everywhere, the one that shows first first: what {@code chat/ChatStack}
     * formats for the preview, which stands for no world in particular.
     */
    public static List<String> texts(List<ChatLine> lines, boolean suffix) {
        return lines.stream().filter(line -> line.suffix() == suffix && line.context().isEmpty())
                .sorted(java.util.Comparator.comparingInt(ChatLine::priority).reversed())
                .map(ChatLine::text).distinct().toList();
    }
}
