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
 * Extension point telling the command tree that the configuration was reloaded.
 *
 * <p>{@link NoOpCommandTreeReloader} is enough on its own: the predicates of
 * {@code CommandTreeRewriter} are dynamic and read {@code configManager.getCommands()} at
 * evaluation time, so permissions stay coherent without any structural action.</p>
 *
 * <p>{@code CommandTreeRewriter} implements this interface and re-registers nodes when the
 * shape of the tree itself has to change.</p>
 */
public interface ICommandTreeReloader {
    /**
     * Called after every successful config hot-reload.
     *
     * @param snapshot the new config snapshot, already applied in ConfigManager
     * @param server   the Minecraft server, null when none is running
     */
    void onConfigReload(ConfigSnapshot snapshot, MinecraftServer server);
}
