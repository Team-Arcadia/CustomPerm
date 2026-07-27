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
import net.minecraft.server.level.ServerPlayer;

public class InternalPermService implements PermissionService {
    private final ConfigManager config;

    public InternalPermService(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, String node) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        if (source.hasPermission(2)) return true;  // INVARIANT-201 : court-circuit OP
        return config.getGrades().userHasPermission(player.getUUID(), node);
    }
}
