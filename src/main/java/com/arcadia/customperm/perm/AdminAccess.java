/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.GradesConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who administers CustomPerm: {@code /customperm}, the admin interface, the LuckPerms editor and admin
 * alerts share these gates.
 *
 * <p>A player needs op level 2 and an explicit ALLOW: {@link PermissionNodes#ADMIN} to enter, and the
 * {@code customperm.manage.<area>} node of an area to change it. The op level alone opens nothing, level 4
 * included, so a player made operator by mistake gets no access; the node alone opens nothing either, so a
 * {@code *} granted by mistake to a non-operator does not (audit retest R02). The console and other
 * non-player sources are never asked for a node, and a singleplayer or LAN world has no console, so its host
 * stands in for it: the server, or the world, can always be administered by someone.</p>
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    /**
     * Entry. Checked on a source rebuilt from the player's real op level, never an elevated one: an alias
     * runs its steps at level 4 and must not open {@code /customperm} (INVARIANT-503).
     */
    public static boolean canAdminister(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack();
        if (!source.hasPermission(2)) return false;
        return isWorldHost(player) || explicit(source, PermissionNodes.ADMIN) == Tristate.ALLOW;
    }

    public static boolean canAdminister(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return canAdminister(player);
        return source.hasPermission(2);
    }

    /** Entry plus the area's {@code customperm.manage.*} node. */
    public static boolean canManage(ServerPlayer player, String node) {
        if (!canAdminister(player)) return false;
        return isWorldHost(player) || explicit(player.createCommandSourceStack(), node) == Tristate.ALLOW;
    }

    /**
     * The host of a singleplayer or LAN world, who stands in for the console: an integrated server has no
     * console to grant the first node from, so the owner of the world would otherwise be locked out of their own
     * world. Never true on a dedicated server, where the console exists.
     */
    public static boolean isWorldHost(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && !server.isDedicatedServer() && server.isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean canManage(CommandSourceStack source, String node) {
        if (source.getEntity() instanceof ServerPlayer player) return canManage(player, node);
        return source.hasPermission(2);
    }

    /** Requirement for a {@code /customperm} subcommand that changes the area of {@code node}. */
    public static Predicate<CommandSourceStack> manage(String node) {
        return source -> canManage(source, node);
    }

    /** Whether a real operator is kept out only for lack of the entry node, which the server log explains. */
    public static boolean isOperatorWithoutAccess(ServerPlayer player) {
        return player.createCommandSourceStack().hasPermission(2) && !canAdminister(player);
    }

    /**
     * Whether any player the internal grades know holds the entry node. {@code false} means nobody can use
     * {@code /customperm} in game until it is granted from the console. Not meaningful under LuckPerms,
     * whose users are not listed here.
     */
    public static boolean anyInternalAdmin() {
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String defaultGrade = CustomPerm.configManager.getSettings().defaultGrade;
        for (String rawUuid : grades.userGrades.keySet()) {
            try {
                if (PermissionResolver.check(grades, UUID.fromString(rawUuid), PermissionNodes.ADMIN, defaultGrade) == Tristate.ALLOW) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed key in grades.json assigns nothing.
            }
        }
        return false;
    }

    /**
     * Explicit value of a node, never throwing into a command predicate or a packet handler: a failing
     * backend reads as DENY, so a broken permission store closes admin access instead of opening it.
     */
    public static Tristate explicit(CommandSourceStack source, String node) {
        try {
            return PermissionService.get().check(source, node);
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            CustomPerm.LOGGER.warn("[CustomPerm] Permission check for {} failed; treating it as denied.", node, t);
            return Tristate.DENY;
        }
    }
}
