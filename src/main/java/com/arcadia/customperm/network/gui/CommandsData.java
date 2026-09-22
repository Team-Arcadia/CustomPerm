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

    /** Holders sent per exposed command, at most. */
    public static final int HOLDERS_MAX = 256;

    /**
     * A grade or a player with an entry for an exposed command's node: who decides where.
     *
     * @param id         the grade's name, or the player's UUID
     * @param everywhere {@code allow}, {@code deny}, or empty when nothing is held everywhere
     * @param servers    {@code name=allow} or {@code name=deny}, one per server their entries name
     */
    public record Holder(boolean player, String id, String label, String everywhere, List<String> servers) {
        public static final StreamCodec<ByteBuf, Holder> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Holder::player,
                GuiCodecs.TEXT, Holder::id,
                GuiCodecs.TEXT, Holder::label,
                GuiCodecs.TEXT, Holder::everywhere,
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Holder::servers,
                Holder::new);
    }

    /**
     * One root command.
     *
     * @param keepOriginal exposed with {@code preserveOriginalRequires}: the node adds to the command's own check
     * @param alias        the root is a CustomPerm alias; exposing it has no effect, aliases use their own node
     * @param rateLimited  an enabled rate limit targets this name
     * @param missing      exposed in the config but absent from the dispatcher
     */
    public record Row(String name, boolean exposed, boolean keepOriginal, boolean alias, boolean rateLimited,
                      boolean missing, List<String> servers, List<Holder> holders) {

        /** One exposed on every member. */
        public Row(String name, boolean exposed, boolean keepOriginal, boolean alias, boolean rateLimited,
                   boolean missing) {
            this(name, exposed, keepOriginal, alias, rateLimited, missing, List.of(), List.of());
        }

        public Row(String name, boolean exposed, boolean keepOriginal, boolean alias, boolean rateLimited,
                   boolean missing, List<String> servers) {
            this(name, exposed, keepOriginal, alias, rateLimited, missing, servers, List.of());
        }


        public static final StreamCodec<ByteBuf, Row> CODEC = StreamCodec.of(
                (buf, r) -> {
                    GuiCodecs.TEXT.encode(buf, r.name);
                    int flags = (r.exposed ? 1 : 0) | (r.keepOriginal ? 2 : 0) | (r.alias ? 4 : 0)
                            | (r.rateLimited ? 8 : 0) | (r.missing ? 16 : 0);
                    ByteBufCodecs.VAR_INT.encode(buf, flags);
                    GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX).encode(buf, r.servers);
                    GuiCodecs.list(Holder.CODEC, HOLDERS_MAX).encode(buf, r.holders);
                },
                buf -> {
                    String name = GuiCodecs.TEXT.decode(buf);
                    int flags = ByteBufCodecs.VAR_INT.decode(buf);
                    return new Row(name, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                            (flags & 8) != 0, (flags & 16) != 0, GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX).decode(buf),
                            GuiCodecs.list(Holder.CODEC, HOLDERS_MAX).decode(buf));
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
