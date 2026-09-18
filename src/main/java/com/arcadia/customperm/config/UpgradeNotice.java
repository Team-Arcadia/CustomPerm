/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.ConfigAdmin;
import com.arcadia.customperm.notify.AdminNotifier;

import java.util.List;

/**
 * Tells the server owner what changed when a configuration written by an older CustomPerm is loaded.
 *
 * <p>The version of the configuration is stored in {@code settings.json}. A file written before 1.1.0 has
 * none, which reads as version 0; a fresh install is stamped with the current version, so a new server is
 * never told about a migration it did not live through. The notice is given once: the log gets the full
 * list, the operators online are told, and an operator who joins while the notice is pending is told too, even
 * when they hold no CustomPerm node, since that is exactly the state the upgrade leaves them in.</p>
 */
public final class UpgradeNotice {

    private static volatile String pending;

    private UpgradeNotice() {
    }

    /** The upgrade message for operators, or {@code null} when nothing changed under this server. */
    public static String pending() {
        return pending;
    }

    /** For tests: forget the notice given this session. */
    public static void clear() {
        pending = null;
    }

    /**
     * Runs the notice for the configuration currently loaded, and stamps it with the current version so the
     * next start says nothing. Does nothing when the configuration is already current.
     */
    public static void onServerStarted() {
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        if (settings.configVersion >= SettingsConfig.CURRENT_CONFIG_VERSION) return;

        int from = settings.configVersion;
        CustomPerm.LOGGER.warn("[CustomPerm] This configuration was written by an older CustomPerm (version {} of the "
                + "settings format). What changed in 1.1.0:", from);
        for (String line : changes()) {
            CustomPerm.LOGGER.warn("[CustomPerm]   - {}", line);
        }
        CustomPerm.LOGGER.warn("[CustomPerm] Full details, in English and French: MIGRATION.md at the repository root.");

        pending = "CustomPerm was upgraded: administering it now needs op level 2 AND granted permissions "
                + "(customperm.admin, plus customperm.manage.<area> to change anything). Nobody holds them yet. "
                + "Grant them from the server console, then see MIGRATION.md.";
        // Told once to the operators online, and again to each operator who joins while it is pending: not an
        // active alert, which would sit on the dashboard until the next restart.
        AdminNotifier.tellOperators(pending);

        settings.configVersion = SettingsConfig.CURRENT_CONFIG_VERSION;
        String warning = ConfigAdmin.persist();
        if (warning != null) CustomPerm.LOGGER.warn("[CustomPerm] {}", warning);
    }

    /** What an admin coming from 1.0.x has to act on, shortest first. */
    private static List<String> changes() {
        return List.of(
                "/customperm and the admin interface need op level 2 AND customperm.admin; every change needs "
                        + "customperm.manage.<area>. Being operator is no longer enough, level 4 included. Grant the nodes "
                        + "from the console: customperm grade create admins / customperm grade addperm admins customperm.* / "
                        + "customperm grade assign <name> admins, or lp user <name> permission set customperm.* true.",
                "The host of a singleplayer or LAN world keeps access without a node, that world having no console.",
                "Internal grades resolve the most specific node first, like LuckPerms: a denied wildcard no longer beats a "
                        + "narrower allow. Check grades that deny a wildcard while allowing something under it.",
                "An explicit DENY now applies to operators, so a grade assigned to an op can restrict them.",
                "The in-game interface needs CustomPerm on the admin's client; network protocol 2, and TesseraUI is gone.",
                "/customperm grade assign|unassign take a player name, not a selector; offline players must have joined before.",
                "New settings.json fields: gateAllCommands, defaultGrade, playerCommandLog, maskPlayerCommandArguments, "
                        + "maskedCommands, logRetentionDays, configVersion, decorateNames, nameFormat. The defaults keep the "
                        + "previous behaviour.");
    }
}
