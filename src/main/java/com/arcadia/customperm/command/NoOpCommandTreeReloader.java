/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.config.ConfigSnapshot;
import net.minecraft.server.MinecraftServer;

/**
 * No-op {@link ICommandTreeReloader}, used where the tree needs no structural work.
 *
 * <p>The predicates of CommandTreeRewriter are dynamic (they read
 * {@code configManager.getCommands()} at evaluation time), so permissions stay coherent
 * after a reload without anything being re-registered.</p>
 */
public class NoOpCommandTreeReloader implements ICommandTreeReloader {

    @Override
    public void onConfigReload(ConfigSnapshot snapshot, MinecraftServer server) {
        // Intentionally empty; CommandTreeRewriter carries the real implementation.
    }
}
