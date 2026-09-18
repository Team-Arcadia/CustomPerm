/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.chat;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.perm.ChatMeta;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Puts the chat prefix and suffix around a player's name.
 *
 * <p><strong>The name, never the message.</strong> Chat messages are signed since 1.19. NeoForge's
 * {@code ServerChatEvent} can replace a message, but what it returns travels as the message's unsigned
 * content, which the client marks as modified by the server, and cancelling it to broadcast a system
 * message instead loses reporting and the secure chat indicator. The sender's name is not part of what is
 * signed: vanilla binds it from {@code player.getDisplayName()} when it broadcasts, and NeoForge builds that
 * name through {@link PlayerEvent.NameFormat}. Decorating it there leaves every message signed and
 * reportable, untouched.
 *
 * <p>What that costs: the name is decorated wherever the game shows it, not only in chat. Death and
 * advancement messages, {@code /msg}, {@code /me}, the join message and the tab list carry the prefix too;
 * the name above a player's head does not, the client drawing it from its own data. The frame around the
 * name ({@code <Name> message}) is vanilla's chat type and stays as it is.
 *
 * <p>Off by default ({@code decorateNames}), since another chat mod may already decorate names. Both
 * backends feed it: the grades without LuckPerms, LuckPerms' own prefix and suffix with it, which it
 * stores but nothing on NeoForge shows.
 *
 * <p>A nickname replaces the name itself, the {@code {name}} of the format, and applies even with
 * decoration off: it was set on purpose for that one player. The team colour still applies around it, and
 * vanilla's hover on a name keeps showing the real one.
 */
public final class NameDecoration {

    private NameDecoration() {
    }

    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Component decorated = decorate(player, event.getDisplayname());
        if (decorated != null) event.setDisplayname(decorated);
    }

    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // The tab list shows the name the game already built, team colours included, so it reads the same.
        if (nickname(player) != null || enabled() && !meta(player).isEmpty()) {
            event.setDisplayName(player.getDisplayName());
        }
    }

    /** The decorated name, or {@code null} when there is nothing to change: no nickname, nothing to decorate. */
    private static Component decorate(ServerPlayer player, Component name) {
        String nickname = nickname(player);
        Component shown = nickname == null ? name : LegacyText.parse(nickname);
        if (!enabled()) return nickname == null ? null : shown;
        ChatMeta meta = meta(player);
        if (meta.isEmpty()) return nickname == null ? null : shown;
        return format(CustomPerm.configManager.getSettings().nameFormat, meta.prefix(), shown, meta.suffix());
    }

    private static String nickname(ServerPlayer player) {
        return CustomPerm.configManager == null ? null : com.arcadia.customperm.admin.NickAdmin.nickname(player.getUUID());
    }

    private static boolean enabled() {
        return CustomPerm.configManager != null && CustomPerm.permissions != null
                && CustomPerm.configManager.getSettings().decorateNames;
    }

    private static ChatMeta meta(ServerPlayer player) {
        ChatMeta meta = PermissionService.get().chatMeta(player);
        return meta == null ? ChatMeta.NONE : meta;
    }

    /**
     * Builds {@code format} with its three placeholders: {@code {prefix}}, {@code {name}} and
     * {@code {suffix}}. The text between them takes {@code &} codes like a prefix; the name is inserted
     * as the component it is, so a team colour or another mod's decoration survives.
     */
    public static MutableComponent format(String format, String prefix, Component name, String suffix) {
        MutableComponent result = Component.empty();
        String rest = format == null || !format.contains(SettingsConfig.NAME_PLACEHOLDER)
                ? SettingsConfig.DEFAULT_NAME_FORMAT : format;
        while (!rest.isEmpty()) {
            int at = rest.indexOf('{');
            int end = at < 0 ? -1 : rest.indexOf('}', at);
            if (at < 0 || end < 0) {
                result.append(LegacyText.parse(rest));
                break;
            }
            if (at > 0) result.append(LegacyText.parse(rest.substring(0, at)));
            switch (rest.substring(at, end + 1)) {
                case "{prefix}" -> result.append(LegacyText.parse(prefix));
                case "{suffix}" -> result.append(LegacyText.parse(suffix));
                case SettingsConfig.NAME_PLACEHOLDER -> result.append(name);
                default -> result.append(LegacyText.parse(rest.substring(at, end + 1)));
            }
            rest = rest.substring(end + 1);
        }
        return result;
    }

    /**
     * Builds one player's names again: after a change to what they hold, or to the settings. The tab list
     * is sent again only when the name there changed.
     */
    public static void refresh(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    public static void refreshAll(MinecraftServer server) {
        if (server != null) server.getPlayerList().getPlayers().forEach(NameDecoration::refresh);
    }
}
