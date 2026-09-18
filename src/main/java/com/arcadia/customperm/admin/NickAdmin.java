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
import com.arcadia.customperm.chat.LegacyText;
import com.arcadia.customperm.config.GradesConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.UsernameCache;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nicknames: the name a player is shown under, set by an admin for anyone or by a player for themselves.
 *
 * <p>Available on both backends: a nickname is not a permission, so it lives in {@code grades.json} beside
 * the prefixes and is read by the name decoration whoever decides permissions. The real name stays
 * reachable: vanilla's hover on a name shows it, and {@code /customperm user list} prints both.
 *
 * <p>A nickname that reads as another player's name, or as another player's nickname, is refused: that is
 * how a moderator gets impersonated. Letters are compared without their case, their codes, their spaces or
 * the separators {@code _ - .}.
 */
public final class NickAdmin {

    /** Longest nickname as players read it, codes aside: a Minecraft name is at most 16 characters. */
    public static final int PLAIN_MAX = 16;

    private NickAdmin() {
    }

    /** The nickname stored for {@code uuid}, codes included, or {@code null}. */
    public static String nickname(UUID uuid) {
        if (CustomPerm.configManager == null) return null;
        return CustomPerm.configManager.getGrades().userNicknames.get(uuid.toString());
    }

    /**
     * Why {@code raw} cannot be a nickname, or {@code null} when it can. Blank is not checked: it means
     * clearing. {@code codes} is whether colour and format codes are allowed to this setter.
     */
    public static String problem(String raw, boolean codes) {
        String text = raw.strip();
        String decoration = LegacyText.problem(text);
        if (decoration != null) return decoration;
        String plain = LegacyText.plain(text).strip();
        if (plain.isEmpty()) return "A nickname needs at least one visible character.";
        if (plain.codePointCount(0, plain.length()) > PLAIN_MAX) {
            return "A nickname is at most " + PLAIN_MAX + " characters, codes aside.";
        }
        if (!codes && !LegacyText.plain(text).equals(text)) {
            return "Colour and format codes in a nickname need customperm.nick.color.";
        }
        return null;
    }

    /**
     * How two names are compared: without case, codes, spaces or the separators a name may use, so
     * {@code &cSte ve} is Steve and {@code mod team} is mod_team.
     */
    public static String key(String text) {
        return LegacyText.plain(text).replaceAll("[\\s_.\\-]+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * The player whose name or nickname {@code raw} would read as, other than {@code self}, or {@code null}.
     *
     * @param names     every known player, UUID to name
     * @param nicknames every nickname, UUID string to text
     */
    public static String clash(String raw, UUID self, Map<UUID, String> names, Map<String, String> nicknames) {
        String wanted = key(raw);
        for (Map.Entry<UUID, String> known : names.entrySet()) {
            if (!known.getKey().equals(self) && key(known.getValue()).equals(wanted)) return known.getValue();
        }
        for (Map.Entry<String, String> other : nicknames.entrySet()) {
            if (other.getKey().equals(self.toString()) || !key(other.getValue()).equals(wanted)) continue;
            String owner = names.entrySet().stream().filter(e -> e.getKey().toString().equals(other.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse(other.getKey());
            return owner + "'s nickname";
        }
        return null;
    }

    /**
     * Sets or clears ({@code raw} blank) the nickname of {@code uuid}. {@code codes} is whether the setter may
     * use colour codes: always for an admin, with {@code customperm.nick.color} for a player's own.
     */
    public static AdminResult set(MinecraftServer server, UUID uuid, String name, String raw, boolean codes) {
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String text = raw.strip();
        String before = grades.userNicknames.get(uuid.toString());
        if (text.isEmpty()) {
            if (before == null) return AdminResult.ok(name + " has no nickname — no change.");
            grades.userNicknames.remove(uuid.toString());
            return saved(server, uuid, AdminResult.ok("Cleared the nickname of " + name));
        }
        String problem = problem(text, codes);
        if (problem != null) return AdminResult.fail(problem);
        if (text.equals(before)) return AdminResult.ok(name + " is already shown as " + text + " — no change.");
        // One's own name is never someone else's, even when the name cache still pairs it with an old account.
        String clash = key(text).equals(key(name)) ? null : clash(text, uuid, knownNames(server), grades.userNicknames);
        if (clash != null) {
            return AdminResult.fail("Refused: " + LegacyText.plain(text) + " reads as " + clash
                    + ". A nickname cannot pass for another player.");
        }
        grades.userNicknames.put(uuid.toString(), text);
        AdminResult result = AdminResult.ok(name + " is now shown as " + text);
        return saved(server, uuid, result);
    }

    private static AdminResult saved(MinecraftServer server, UUID uuid, AdminResult result) {
        String warning = ConfigAdmin.persist();
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (online != null) com.arcadia.customperm.chat.NameDecoration.refresh(online);
        return result.warn(warning);
    }

    /** Every player this server knows a name for, online ones first so a fresh name is never missed. */
    private static Map<UUID, String> knownNames(MinecraftServer server) {
        Map<UUID, String> names = new HashMap<>(UsernameCache.getMap());
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(p -> names.put(p.getUUID(), p.getGameProfile().getName()));
        }
        return names;
    }
}
