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
 * Logs page: the latest admin changes and player commands, newest first, with the player log settings.
 *
 * @param playerLog     whether player commands are being recorded
 * @param masking       whether the arguments of sensitive commands are masked
 * @param retentionDays days log files are kept, 0 for no limit
 */
public record LogsData(List<Entry> admin, List<Entry> players, boolean playerLog, boolean masking, int retentionDays)
        implements GuiPageData {

    /** Most entries sent per tab; older ones stay in the files. */
    public static final int ENTRIES_MAX = 300;

    public record Entry(long time, String actor, String source, String action, boolean success, String result) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Entry::time,
                GuiCodecs.TEXT, Entry::actor,
                GuiCodecs.TEXT, Entry::source,
                GuiCodecs.TEXT, Entry::action,
                ByteBufCodecs.BOOL, Entry::success,
                GuiCodecs.TEXT, Entry::result,
                Entry::new);
    }

    public static final StreamCodec<ByteBuf, LogsData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Entry.CODEC, ENTRIES_MAX), LogsData::admin,
            GuiCodecs.list(Entry.CODEC, ENTRIES_MAX), LogsData::players,
            ByteBufCodecs.BOOL, LogsData::playerLog,
            ByteBufCodecs.BOOL, LogsData::masking,
            ByteBufCodecs.VAR_INT, LogsData::retentionDays,
            LogsData::new);

    @Override
    public GuiPage page() {
        return GuiPage.LOGS;
    }
}
