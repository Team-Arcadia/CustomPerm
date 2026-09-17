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
 * The Players page: what each player holds in their own name, above their grades. Grades appear here as
 * read-only labels; they are edited on the Grades page, which is where they are defined.
 *
 * @param players      players carrying a node or a grade of their own, plus everyone online
 * @param knownPlayers every name the server has seen, for the completion of the add field
 * @param fallbackMode {@code settings.json} value, only to word the banner while LuckPerms is active
 */
public record PlayersData(List<Player> players, List<String> knownPlayers, String fallbackMode)
        implements GuiPageData {

    /** Most nodes carried per player, per kind. */
    public static final int NODES_MAX = 1024;

    /** {@code name} is the UUID when the server has never seen a name for it. */
    public record Player(String uuid, String name, boolean online, List<String> grades, List<String> allow,
                         List<String> deny) {

        public static final StreamCodec<ByteBuf, Player> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Player::uuid,
                GuiCodecs.TEXT, Player::name,
                ByteBufCodecs.BOOL, Player::online,
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Player::grades,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Player::allow,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Player::deny,
                Player::new);
    }

    public static final StreamCodec<ByteBuf, PlayersData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Player.CODEC, GuiCodecs.SERVER_LIST_MAX), PlayersData::players,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), PlayersData::knownPlayers,
            GuiCodecs.TEXT, PlayersData::fallbackMode,
            PlayersData::new);

    @Override
    public GuiPage page() {
        return GuiPage.PLAYERS;
    }
}
