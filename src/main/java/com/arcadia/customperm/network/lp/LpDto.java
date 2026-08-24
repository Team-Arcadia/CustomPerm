/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.lp;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Wire model for the LuckPerms editor screens. Every type here is plain Java — no
 * {@code net.luckperms.api.*} reference — precisely because these records are decoded on the
 * client, where LuckPerms is not installed. {@code LuckPermsAdminService} is the only class
 * allowed to translate between LuckPerms objects and these DTOs.
 * <p>
 * Contexts travel as a flattened {@code key=value;key=value} string rather than a nested map:
 * a context set is display data for the GUI and an exact-match key for edits, never something
 * the client interprets structurally, so a string keeps both the codec and the templates simple.
 */
public final class LpDto {

    private LpDto() {
    }

    /** Permanent expiry sentinel — LuckPerms models "no expiry" as the absence of a timestamp. */
    public static final long NO_EXPIRY = 0L;

    /** Sentinel for a group with no explicit weight node. */
    public static final int NO_WEIGHT = -1;

    // Typed on ByteBuf rather than RegistryFriendlyByteBuf: STRING_UTF8 is itself a
    // StreamCodec<ByteBuf, String>, and StreamCodec is invariant in its buffer type, so a
    // RegistryFriendlyByteBuf-typed list codec would not accept it. Encoding still works from a
    // RegistryFriendlyByteBuf, which is a ByteBuf.
    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8);

    /**
     * One LuckPerms node as shown in the editor.
     *
     * @param key      full node key ({@code minecraft.command.gamemode}, {@code group.admin}, ...)
     * @param value    true = allow, false = deny
     * @param contexts flattened context set, empty string for the global context
     * @param expiry   epoch seconds, or {@link #NO_EXPIRY}
     * @param type     LuckPerms {@code NodeType} name, used by the GUI to pick a tab and an icon
     */
    public record NodeDto(String key, boolean value, String contexts, long expiry, String type) {

        public static final StreamCodec<RegistryFriendlyByteBuf, NodeDto> CODEC = StreamCodec.of(
                (buf, n) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, n.key);
                    ByteBufCodecs.BOOL.encode(buf, n.value);
                    ByteBufCodecs.STRING_UTF8.encode(buf, n.contexts);
                    ByteBufCodecs.VAR_LONG.encode(buf, n.expiry);
                    ByteBufCodecs.STRING_UTF8.encode(buf, n.type);
                },
                buf -> new NodeDto(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)));

        public static final StreamCodec<RegistryFriendlyByteBuf, List<NodeDto>> LIST_CODEC =
                ByteBufCodecs.<RegistryFriendlyByteBuf, NodeDto>list().apply(CODEC);
    }

    /**
     * A LuckPerms group. In list scopes {@code nodes} is empty and only {@code nodeCount} is
     * populated — sending every node of every group would be a large packet for a screen that
     * shows one line per group.
     */
    public record GroupDto(String name, String displayName, int weight, String prefix, String suffix,
                           List<String> parents, List<NodeDto> nodes, int nodeCount) {

        public static final StreamCodec<RegistryFriendlyByteBuf, GroupDto> CODEC = StreamCodec.of(
                (buf, g) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, g.name);
                    ByteBufCodecs.STRING_UTF8.encode(buf, g.displayName);
                    ByteBufCodecs.VAR_INT.encode(buf, g.weight);
                    ByteBufCodecs.STRING_UTF8.encode(buf, g.prefix);
                    ByteBufCodecs.STRING_UTF8.encode(buf, g.suffix);
                    STRING_LIST.encode(buf, g.parents);
                    NodeDto.LIST_CODEC.encode(buf, g.nodes);
                    ByteBufCodecs.VAR_INT.encode(buf, g.nodeCount);
                },
                buf -> new GroupDto(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        STRING_LIST.decode(buf),
                        NodeDto.LIST_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)));

        public static final StreamCodec<RegistryFriendlyByteBuf, List<GroupDto>> LIST_CODEC =
                ByteBufCodecs.<RegistryFriendlyByteBuf, GroupDto>list().apply(CODEC);
    }

    /**
     * A LuckPerms user. Edits address a user by {@code uuid}, never by name: the name is a
     * display label that can change, and resolving it per edit would turn every click into an
     * async username lookup.
     */
    public record UserDto(String uuid, String username, String primaryGroup, boolean online,
                          List<String> parents, List<NodeDto> nodes, int nodeCount) {

        public static final StreamCodec<RegistryFriendlyByteBuf, UserDto> CODEC = StreamCodec.of(
                (buf, u) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, u.uuid);
                    ByteBufCodecs.STRING_UTF8.encode(buf, u.username);
                    ByteBufCodecs.STRING_UTF8.encode(buf, u.primaryGroup);
                    ByteBufCodecs.BOOL.encode(buf, u.online);
                    STRING_LIST.encode(buf, u.parents);
                    NodeDto.LIST_CODEC.encode(buf, u.nodes);
                    ByteBufCodecs.VAR_INT.encode(buf, u.nodeCount);
                },
                buf -> new UserDto(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        STRING_LIST.decode(buf),
                        NodeDto.LIST_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)));

        public static final StreamCodec<RegistryFriendlyByteBuf, List<UserDto>> LIST_CODEC =
                ByteBufCodecs.<RegistryFriendlyByteBuf, UserDto>list().apply(CODEC);
    }

    /** A LuckPerms track: an ordered ladder of group names used by promote/demote. */
    public record TrackDto(String name, List<String> groups) {

        public static final StreamCodec<RegistryFriendlyByteBuf, TrackDto> CODEC = StreamCodec.of(
                (buf, t) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, t.name);
                    STRING_LIST.encode(buf, t.groups);
                },
                buf -> new TrackDto(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        STRING_LIST.decode(buf)));

        public static final StreamCodec<RegistryFriendlyByteBuf, List<TrackDto>> LIST_CODEC =
                ByteBufCodecs.<RegistryFriendlyByteBuf, TrackDto>list().apply(CODEC);
    }

    /**
     * One editor screen worth of data. {@code scope} echoes the request so a late reply for a
     * screen the admin already left is discarded instead of overwriting the current one.
     *
     * @param canEdit whether this admin passed the write gate; false renders the editor read-only
     */
    public record Snapshot(String scope, boolean canEdit, List<GroupDto> groups, List<UserDto> users,
                           List<TrackDto> tracks) {

        public static final Snapshot EMPTY =
                new Snapshot("", false, List.of(), List.of(), List.of());

        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buf, s) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, s.scope);
                    ByteBufCodecs.BOOL.encode(buf, s.canEdit);
                    GroupDto.LIST_CODEC.encode(buf, s.groups);
                    UserDto.LIST_CODEC.encode(buf, s.users);
                    TrackDto.LIST_CODEC.encode(buf, s.tracks);
                },
                buf -> new Snapshot(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        GroupDto.LIST_CODEC.decode(buf),
                        UserDto.LIST_CODEC.decode(buf),
                        TrackDto.LIST_CODEC.decode(buf)));
    }
}
