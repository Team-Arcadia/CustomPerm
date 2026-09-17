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
import com.arcadia.customperm.config.ConfigSnapshot;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.server.MinecraftServer;

/**
 * Configuration-wide operations: reload from disk. Called by {@code /customperm reload} and by the
 * admin interface, on the server thread.
 */
public final class ConfigAdmin {

    private ConfigAdmin() {
    }

    /**
     * Saves the config after a change. The change stays live in memory either way; what the admin
     * must know is that it will not survive a restart, and that a reload will discard it.
     *
     * @return {@code null} when saved, otherwise the warning to show
     */
    public static String persist() {
        if (CustomPerm.configManager.save()) return null;
        String reason = CustomPerm.configManager.isDiskWritable()
                ? "disk error, see the server log"
                : "a config file on disk is invalid; fix it, then run /customperm reload";
        return "[CustomPerm] Change applied in memory but NOT saved (" + reason + ").";
    }

    /** Pushes the command tree again to every player, after a change of what they may run. */
    public static void resyncCommands(MinecraftServer server) {
        if (server != null) server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p));
    }

    public static AdminResult reload(MinecraftServer server) {
        boolean reloaded = CustomPerm.configManager.load();
        CustomPerm.syncConfigAlert();
        if (!reloaded) {
            return AdminResult.fail(CustomPerm.configManager.isReloading()
                    ? "[CustomPerm] Reload already in progress — try again in a moment."
                    : "[CustomPerm] Reload failed — check server logs for details (invalid JSON or disk error).");
        }

        ConfigSnapshot snapshot = CustomPerm.configManager.getSnapshot();

        // 1. Tell the permission service about the new snapshot (no-op by default).
        PermissionService.get().onConfigReload(snapshot);

        // 2. Re-apply exposure and aliases to the live command tree.
        CustomPerm.treeReloader.onConfigReload(snapshot, server);

        // 3. Push the command tree again to every client (INVARIANT-501), always through
        //    server.execute() so it runs on the tick thread wherever the reload was triggered.
        if (server != null) {
            server.execute(() ->
                server.getPlayerList().getPlayers()
                    .forEach(p -> server.getCommands().sendCommands(p))
            );
        }

        CustomPerm.LOGGER.info("[CustomPerm] Configuration reloaded successfully");
        return AdminResult.ok("Configuration reloaded successfully.");
    }
}
