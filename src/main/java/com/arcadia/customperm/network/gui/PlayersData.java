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
 * @param names        whether names carry their prefix, for the Chat tab's preview
 * @param tracks       every track with its rungs, lowest first, for the Tracks tab
 */
public record PlayersData(List<Player> players, List<String> knownPlayers, String fallbackMode, NameSettings names,
                          List<Track> tracks) implements GuiPageData {

    /** A ladder of grades, lowest first. */
    public record Track(String name, List<String> grades) {
        public static final StreamCodec<ByteBuf, Track> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Track::name,
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Track::grades,
                Track::new);
    }

    /** Most nodes carried per player, per kind. */
    public static final int NODES_MAX = 1024;

    /** {@code name} is the UUID when the server has never seen a name for it. */
    public record Player(String uuid, String name, boolean online, Held held, List<String> allow,
                         List<String> deny) {

        public static final StreamCodec<ByteBuf, Player> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Player::uuid,
                GuiCodecs.TEXT, Player::name,
                ByteBufCodecs.BOOL, Player::online,
                Held.CODEC, Player::held,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Player::allow,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Player::deny,
                Player::new);

        public List<String> grades() {
            return held.grades();
        }

        public List<String> refused() {
            return held.refused();
        }

        /** Their own prefixes then suffixes, highest priority first. */
        public List<ChatLine> chat() {
            return held.chat();
        }

        /** Seconds left on an own node ({@code "allow"} or {@code "deny"}), 0 when it is permanent. */
        public long remaining(String kind, String node) {
            return Remaining.of(held.timers(), kind, node);
        }

        /** Grades and nodes this player holds in one world only. */
        public List<ScopedEntry> scoped() {
            return held.scoped();
        }
    }

    /**
     * The grades a player holds, the ones they refuse wherever a grade of theirs would bring them, the
     * prefixes and suffixes they carry themselves, and what they hold in one world only.
     */
    public record Held(List<String> grades, List<String> refused, List<ChatLine> chat,
                       List<Remaining> timers, List<ScopedEntry> scoped) {
        public static final Held NONE = new Held(List.of(), List.of(), List.of(), List.of(), List.of());

        public static final StreamCodec<ByteBuf, Held> CODEC = StreamCodec.composite(
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Held::grades,
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Held::refused,
                ChatLine.LIST, Held::chat,
                Remaining.LIST, Held::timers,
                ScopedEntry.LIST, Held::scoped,
                Held::new);
    }

    public static final StreamCodec<ByteBuf, PlayersData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Player.CODEC, GuiCodecs.SERVER_LIST_MAX), PlayersData::players,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), PlayersData::knownPlayers,
            GuiCodecs.TEXT, PlayersData::fallbackMode,
            NameSettings.CODEC, PlayersData::names,
            GuiCodecs.list(Track.CODEC, GuiCodecs.SERVER_LIST_MAX), PlayersData::tracks,
            PlayersData::new);

    @Override
    public GuiPage page() {
        return GuiPage.PLAYERS;
    }
}
