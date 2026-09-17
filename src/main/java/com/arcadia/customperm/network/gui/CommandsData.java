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
 * Exposed commands page: every root command of the dispatcher, plus exposed names the dispatcher no
 * longer has (a mod was removed), so a stale entry can still be seen and hidden.
 *
 * @param truncated true when the server had more commands than {@link GuiCodecs#SERVER_LIST_MAX}
 * @param gateAll   the {@code gateAllCommands} setting: every command reads its node, not only exposed ones
 *                  (never in effect with LuckPerms installed)
 */
public record CommandsData(List<Row> rows, boolean truncated, boolean gateAll) implements GuiPageData {

    /**
     * One root command.
     *
     * @param keepOriginal exposed with {@code preserveOriginalRequires}: the node adds to the command's own check
     * @param alias        the root is a CustomPerm alias; exposing it has no effect, aliases use their own node
     * @param rateLimited  an enabled rate limit targets this name
     * @param missing      exposed in the config but absent from the dispatcher
     */
    public record Row(String name, boolean exposed, boolean keepOriginal, boolean alias, boolean rateLimited,
                      boolean missing) {

        public static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.of(
                (buf, r) -> {
                    GuiCodecs.TEXT.encode(buf, r.name);
                    int flags = (r.exposed ? 1 : 0) | (r.keepOriginal ? 2 : 0) | (r.alias ? 4 : 0)
                            | (r.rateLimited ? 8 : 0) | (r.missing ? 16 : 0);
                    ByteBufCodecs.VAR_INT.encode(buf, flags);
                },
                buf -> {
                    String name = GuiCodecs.TEXT.decode(buf);
                    int flags = ByteBufCodecs.VAR_INT.decode(buf);
                    return new Row(name, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                            (flags & 8) != 0, (flags & 16) != 0);
                });
    }

    public static final StreamCodec<ByteBuf, CommandsData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Row.CODEC, GuiCodecs.SERVER_LIST_MAX), CommandsData::rows,
            ByteBufCodecs.BOOL, CommandsData::truncated,
            ByteBufCodecs.BOOL, CommandsData::gateAll,
            CommandsData::new);

    @Override
    public GuiPage page() {
        return GuiPage.COMMANDS;
    }
}
