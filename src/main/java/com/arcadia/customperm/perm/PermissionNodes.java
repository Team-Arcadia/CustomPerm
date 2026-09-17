/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

/**
 * Permission nodes CustomPerm checks by name rather than deriving from a command name.
 * Resolved through whichever {@link PermissionService} is active, so the same node works
 * whether it was granted via {@code /lp} or via {@code grades.json}.
 *
 * <p>Administration needs op level 2 and explicitly granted nodes, whatever the op level: an operator
 * without them, level 4 included, sees neither {@code /customperm} nor the admin interface, so a player
 * made operator by mistake gets nothing. Only the console is never asked for a node. See
 * {@link AdminAccess}.</p>
 */
public final class PermissionNodes {

    /**
     * Entry to {@code /customperm} and the admin interface: status, lists, diagnostics, reading every
     * page and the logs, receiving admin alerts. Changing anything needs a {@code customperm.manage.*}
     * node on top.
     */
    public static final String ADMIN = "customperm.admin";

    /**
     * Changing one area, through its {@code /customperm} subcommands and its page of the interface alike.
     * One node per area, so a helper can be trusted with aliases without being able to expose commands
     * or change grades. {@code customperm.manage.*} or {@code customperm.*} grants them all.
     */
    public static final String MANAGE_COMMANDS = "customperm.manage.commands";
    public static final String MANAGE_ALIASES = "customperm.manage.aliases";
    public static final String MANAGE_RATELIMITS = "customperm.manage.ratelimits";
    public static final String MANAGE_GRADES = "customperm.manage.grades";
    /** Turning the player command log and argument masking on or off. */
    public static final String MANAGE_LOGS = "customperm.manage.logs";
    /** Reloading the configuration from disk. */
    public static final String MANAGE_CONFIG = "customperm.manage.config";
    /** Writing to LuckPerms through the in-game editor. */
    public static final String MANAGE_LUCKPERMS = "customperm.manage.luckperms";

    /** Every fixed node above, for command suggestions. */
    public static java.util.List<String> all() {
        return java.util.List.of(ADMIN, MANAGE_COMMANDS, MANAGE_ALIASES, MANAGE_RATELIMITS, MANAGE_GRADES, MANAGE_LOGS,
                MANAGE_CONFIG, MANAGE_LUCKPERMS);
    }

    private PermissionNodes() {
    }
}
