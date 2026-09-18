/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.perm.Contexts;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The server's static contexts, and what holds for a player: the keys an entry can be limited to beyond the
 * world and the game mode, like LuckPerms' {@code static-contexts}. Server thread only.
 */
public final class ContextAdmin {

    private ContextAdmin() {
    }

    private static Map<String, String> statics() {
        return CustomPerm.configManager.getSettings().staticContexts;
    }

    /** Sets {@code key} to {@code value} for every player on this server. */
    public static AdminResult set(MinecraftServer server, String rawKey, String rawValue) {
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        if (!Contexts.staticKey(key)) {
            return AdminResult.fail("'" + key + "' cannot be a static context: use letters, digits, _ . or -, "
                    + "and not world, gamemode, dimension-type or server, which the game or cluster mode sets.");
        }
        if (!Contexts.staticValue(value)) {
            return AdminResult.fail("Invalid value '" + value + "': use letters, digits and _ . : / + -, 64 at most.");
        }
        if (value.equals(statics().get(key))) return AdminResult.ok(key + " is already " + value + ". No change.");
        replace(key, value);
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return luckPermsNote(AdminResult.ok(key + "=" + value + " now holds for every player on this server.").warn(warning));
    }

    public static AdminResult unset(MinecraftServer server, String rawKey) {
        String key = rawKey.trim().toLowerCase(Locale.ROOT);
        if (!statics().containsKey(key)) return AdminResult.ok("No static context sets " + key + ". No change.");
        replace(key, null);
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(key + " no longer holds here: entries limited to it apply nowhere until it is set again.")
                .warn(warning);
    }

    /** A new map rather than an edit: the permission service rebuilds its cache when the map changes. */
    private static void replace(String key, String value) {
        Map<String, String> next = new TreeMap<>(statics());
        if (value == null) next.remove(key);
        else next.put(key, value);
        CustomPerm.configManager.getSettings().staticContexts = next;
    }

    /** The static contexts, as {@code key=value}, sorted. */
    public static List<String> describe() {
        return statics().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList();
    }

    /** What holds for {@code player} now, as the resolver sees it: world, game mode and the static contexts. */
    public static List<String> of(ServerPlayer player) {
        return Contexts.of(player.level().dimension().location().toString(),
                player.gameMode.getGameModeForPlayer().getName(), statics()).pairs();
    }

    private static AdminResult luckPermsNote(AdminResult result) {
        return CustomPerm.isLuckPermsActive()
                ? result.note("LuckPerms decides permissions now: its own static-contexts apply, not these.")
                : result;
    }
}
