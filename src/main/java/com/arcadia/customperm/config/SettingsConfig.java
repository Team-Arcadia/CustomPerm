/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.Locale;

public class SettingsConfig {
    public static final String LUCKPERMS_FALLBACK_DENY = "deny";
    public static final String LUCKPERMS_FALLBACK_INTERNAL = "internal";

    /**
     * Controls what happens when LuckPerms is loaded but becomes unavailable.
     *
     * deny     = fail closed and return false for CustomPerm permission checks.
     * internal = fall back to grades.json.
     */
    public String luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;

    /**
     * Internal backend only. {@code false}: only exposed commands read their
     * {@code customperm.command.<name>} node. {@code true}: every root command does, so an explicit DENY
     * (a denied {@code *} included) blocks any command, operators included, and an ALLOW opens any
     * command. Off by default: turning it on gives a grade holding {@code *} or
     * {@code customperm.command.*} every command of the server. No effect with LuckPerms installed,
     * which already gates every command.
     */
    public boolean gateAllCommands = false;

    /**
     * Internal grade applied to every player, below their assigned grades (like the LuckPerms
     * {@code default} group). Empty for none. This is what restricts a player made operator by mistake,
     * who has no grade of their own.
     */
    public String defaultGrade = "";

    public void normalize() {
        defaultGrade = defaultGrade == null ? "" : defaultGrade.trim();
        if (luckPermsFallbackMode == null) {
            luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;
            return;
        }

        luckPermsFallbackMode = luckPermsFallbackMode.trim().toLowerCase(Locale.ROOT);
        if (!LUCKPERMS_FALLBACK_DENY.equals(luckPermsFallbackMode)
                && !LUCKPERMS_FALLBACK_INTERNAL.equals(luckPermsFallbackMode)) {
            luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;
        }
    }

    public boolean useInternalLuckPermsFallback() {
        return LUCKPERMS_FALLBACK_INTERNAL.equals(luckPermsFallbackMode);
    }
}
