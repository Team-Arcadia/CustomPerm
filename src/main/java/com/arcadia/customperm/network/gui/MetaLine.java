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
import java.util.Map;
import java.util.TreeMap;

/**
 * One meta value of a grade or a player, as a page shows it: the seconds it has left, 0 when it is for good,
 * and the context it is limited to, empty for everywhere.
 */
public record MetaLine(String key, String value, long remaining, String context) {

    public static final StreamCodec<ByteBuf, MetaLine> CODEC = StreamCodec.composite(
            GuiCodecs.TEXT, MetaLine::key,
            GuiCodecs.TEXT, MetaLine::value,
            ByteBufCodecs.VAR_LONG, MetaLine::remaining,
            GuiCodecs.TEXT, MetaLine::context,
            MetaLine::new);

    public static final StreamCodec<ByteBuf, List<MetaLine>> LIST = GuiCodecs.list(CODEC, GuiCodecs.SERVER_LIST_MAX);

    /** A holder's meta by key, then that limited to a context, by context, leaving out what has run out. */
    public static List<MetaLine> of(Map<String, String> meta, Map<String, Long> expiries,
                                    Map<String, ? extends GradesConfig.Scoped> scopes) {
        List<MetaLine> lines = new ArrayList<>();
        long now = Expiry.now();
        add(lines, meta, expiries, "", now);
        if (scopes != null) new TreeMap<>(scopes).forEach((context, scope) -> add(lines, scope.meta, scope.metaExpiries, context, now));
        return lines.size() > GuiCodecs.SERVER_LIST_MAX ? lines.subList(0, GuiCodecs.SERVER_LIST_MAX) : lines;
    }

    private static void add(List<MetaLine> lines, Map<String, String> meta, Map<String, Long> expiries, String context,
                            long now) {
        if (meta == null) return;
        new TreeMap<>(meta).forEach((key, value) -> {
            Long at = expiries == null ? null : expiries.get(key);
            if (at != null && at <= now) return;
            lines.add(new MetaLine(key, value, at == null ? 0 : at - now, context));
        });
    }
}
