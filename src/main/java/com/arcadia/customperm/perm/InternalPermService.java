/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.config.ConfigManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InternalPermService implements PermissionService {
    private final ConfigManager config;
    /** One per dimension, built once: a permission check must not build a string per call. */
    private final Map<ResourceKey<Level>, Contexts> byWorld = new ConcurrentHashMap<>();

    public InternalPermService(ConfigManager config) {
        this.config = config;
    }

    @Override
    public Tristate check(CommandSourceStack source, String node) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return Tristate.UNSET;
        return PermissionResolver.check(config.getGrades(), player.getUUID(), node, config.getSettings().defaultGrade,
                contexts(player));
    }

    /** Where the player stands, not where the command runs: {@code execute in} does not change what they hold. */
    Contexts contexts(ServerPlayer player) {
        return byWorld.computeIfAbsent(player.level().dimension(),
                key -> Contexts.world(key.location().toString()));
    }

    @Override
    public ChatMeta chatMeta(ServerPlayer player) {
        String defaultGrade = config.getSettings().defaultGrade;
        return new ChatMeta(PermissionResolver.prefix(config.getGrades(), player.getUUID(), defaultGrade),
                PermissionResolver.suffix(config.getGrades(), player.getUUID(), defaultGrade));
    }
}
