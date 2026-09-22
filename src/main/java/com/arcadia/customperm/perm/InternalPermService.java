/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.chat.ChatStack;
import com.arcadia.customperm.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InternalPermService implements PermissionService {
    private final ConfigManager config;
    /**
     * One per dimension and game mode, built once: a permission check must not build a string per call. The
     * array is indexed by the game mode's id.
     */
    private final Map<ResourceKey<Level>, Contexts[]> byWorld = new ConcurrentHashMap<>();
    /** The static contexts the cache was built from; the settings replace the map whole when they change. */
    private volatile Map<String, String> builtFrom;

    public InternalPermService(ConfigManager config) {
        this.config = config;
    }

    @Override
    public Tristate check(CommandSourceStack source, String node) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return Tristate.UNSET;
        return PermissionResolver.check(config.getGrades(), player.getUUID(), node, config.getSettings().defaultGrade,
                contexts(player));
    }

    @Override
    public Tristate checkServerScoped(CommandSourceStack source, String node) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return Tristate.UNSET;
        return PermissionResolver.checkServerScoped(config.getGrades(), player.getUUID(), node,
                config.getSettings().defaultGrade, contexts(player));
    }

    /**
     * Where the player stands, not where the command runs: {@code execute in} does not change what they hold.
     * Their dimension, their game mode, and the server's static contexts.
     */
    public Contexts contexts(ServerPlayer player) {
        Map<String, String> statics = com.arcadia.customperm.cluster.Cluster.declared(config.getSettings().staticContexts);
        if (statics != builtFrom) {
            byWorld.clear();
            builtFrom = statics;
        }
        ResourceKey<Level> world = player.level().dimension();
        Contexts[] modes = byWorld.computeIfAbsent(world, key -> new Contexts[GameType.values().length]);
        GameType mode = player.gameMode.getGameModeForPlayer();
        Contexts built = modes[mode.getId()];
        if (built == null) {
            built = Contexts.of(world.location().toString(), mode.getName(), statics);
            modes[mode.getId()] = built;
        }
        return built;
    }

    @Override
    public String meta(ServerPlayer player, String key) {
        return PermissionResolver.meta(config.getGrades(), player.getUUID(), key, config.getSettings().defaultGrade,
                contexts(player));
    }

    @Override
    public ChatMeta chatMeta(ServerPlayer player) {
        var settings = config.getSettings();
        String defaultGrade = settings.defaultGrade;
        Contexts where = contexts(player);
        return new ChatMeta(
                ChatStack.format(PermissionResolver.prefixes(config.getGrades(), player.getUUID(), defaultGrade, where),
                        settings.prefixStack),
                ChatStack.format(PermissionResolver.suffixes(config.getGrades(), player.getUUID(), defaultGrade, where),
                        settings.suffixStack));
    }
}
