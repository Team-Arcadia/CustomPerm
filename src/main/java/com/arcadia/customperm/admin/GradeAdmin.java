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
import com.arcadia.customperm.config.GradesConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.UsernameCache;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Internal grades: named sets of ALLOW and DENY nodes assigned to players, online or not. Shared by
 * {@code /customperm grade} and the admin interface, server thread only. Every operation refuses while
 * LuckPerms is the active backend, where grades decide nothing.
 */
public final class GradeAdmin {

    private static final Pattern NAME = Pattern.compile("[0-9A-Za-z_\\-.+]{1,64}");
    private static final int NODE_MAX = 256;

    private GradeAdmin() {
    }

    private static GradesConfig grades() {
        return CustomPerm.configManager.getGrades();
    }

    /** The refusal while LuckPerms owns permissions, or {@code null} when grades apply. */
    public static AdminResult unavailable() {
        return CustomPerm.isLuckPermsActive()
                ? AdminResult.fail("[CustomPerm] Grade commands are disabled — use /lp instead.")
                : null;
    }

    public static AdminResult create(String name) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        if (!NAME.matcher(name).matches()) {
            return AdminResult.fail("Invalid grade name '" + name + "': use 1 to 64 letters, digits, _ - . or +.");
        }
        if (grades().grades.containsKey(name)) return AdminResult.fail("Grade already exists: " + name);
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grades().grades.put(name, grade);
        return AdminResult.ok("Created grade " + name).warn(ConfigAdmin.persist());
    }

    public static AdminResult delete(MinecraftServer server, String name) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        if (grades().grades.remove(name) == null) return AdminResult.fail("No such grade: " + name);
        Iterator<Map.Entry<String, List<String>>> iterator = grades().userGrades.entrySet().iterator();
        while (iterator.hasNext()) {
            List<String> assigned = iterator.next().getValue();
            if (assigned == null) {
                iterator.remove();
                continue;
            }
            assigned.removeIf(name::equals);
            if (assigned.isEmpty()) iterator.remove();
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok("Deleted grade " + name).warn(warning);
    }

    /**
     * Adds an ALLOW or a DENY node. A DENY in any of a player's grades wins over an ALLOW in another, so
     * a denied node stays denied whatever else the player holds.
     */
    public static AdminResult addNode(MinecraftServer server, String gradeName, String rawNode, boolean deny) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
        if (node.isEmpty() || node.length() > NODE_MAX || node.chars().anyMatch(Character::isWhitespace)) {
            return AdminResult.fail("Invalid permission node '" + node + "'.");
        }
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        Set<String> nodes = deny ? grade.deniedPermissions : grade.permissions;
        if (!nodes.add(node)) {
            return AdminResult.ok(node + " is already " + (deny ? "denied to " : "granted to ") + gradeName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok((deny ? "Denied " : "Added ") + node + " -> " + gradeName).warn(warning);
    }

    public static AdminResult removeNode(MinecraftServer server, String gradeName, String rawNode, boolean deny) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        Set<String> nodes = deny ? grade.deniedPermissions : grade.permissions;
        if (!nodes.remove(node)) {
            return AdminResult.ok(node + " is not " + (deny ? "denied to " : "granted to ") + gradeName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok((deny ? "Removed the denial of " : "Removed ") + node + " from " + gradeName).warn(warning);
    }

    public static AdminResult assign(MinecraftServer server, GameProfile profile, String gradeName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        if (!grades().grades.containsKey(gradeName)) return AdminResult.fail("No such grade: " + gradeName);
        List<String> list = grades().userGrades.computeIfAbsent(profile.getId().toString(), k -> new ArrayList<>());
        if (list.contains(gradeName)) {
            return AdminResult.ok(profile.getName() + " is already assigned to " + gradeName + " — no change.");
        }
        list.add(gradeName);
        String warning = ConfigAdmin.persist();
        resyncPlayer(server, profile.getId());
        return AdminResult.ok("Assigned " + gradeName + " -> " + profile.getName()).warn(warning);
    }

    public static AdminResult unassign(MinecraftServer server, UUID uuid, String displayName, String gradeName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        List<String> list = grades().userGrades.get(uuid.toString());
        if (list == null || !list.remove(gradeName)) {
            return AdminResult.ok(displayName + " is not assigned to " + gradeName + " — no change.");
        }
        if (list.isEmpty()) grades().userGrades.remove(uuid.toString());
        String warning = ConfigAdmin.persist();
        resyncPlayer(server, uuid);
        return AdminResult.ok("Unassigned " + gradeName + " from " + displayName).warn(warning);
    }

    /**
     * Resolves a player known to this server by name: online players, then any player who has joined
     * before (NeoForge's username cache). Never asks the session service: on an offline-mode server
     * that lookup invents a profile for any name, so a typo would silently grant a grade to nobody.
     */
    public static Optional<GameProfile> findKnownProfile(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return Optional.of(online.getGameProfile());
        return UsernameCache.getMap().entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(name))
                .findFirst()
                .map(entry -> new GameProfile(entry.getKey(), entry.getValue()));
    }

    /** Names of every player known to this server, for suggestions. */
    public static List<String> knownPlayerNames(MinecraftServer server) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        server.getPlayerList().getPlayers().forEach(p -> names.add(p.getGameProfile().getName()));
        names.addAll(UsernameCache.getMap().values());
        return new ArrayList<>(names);
    }

    /** Best display name for an assigned UUID, without any network lookup. */
    public static String displayName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        String known = UsernameCache.getLastKnownUsername(uuid);
        return known != null ? known : uuid.toString();
    }

    private static void resyncPlayer(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) server.getCommands().sendCommands(player);
    }
}
