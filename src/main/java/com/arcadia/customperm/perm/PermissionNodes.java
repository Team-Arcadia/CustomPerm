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
 */
public final class PermissionNodes {

    /**
     * Write access to the in-game LuckPerms editor. Reading the editor needs op level 2, like
     * every other CustomPerm admin screen; changing the permission store needs this node on top.
     * <p>
     * The point of a separate node is delegation: a moderator can be handed the editor without
     * being handed {@code /lp} itself. Server owners (permission level 4) are allowed to write
     * without it, because the alternative is a fresh install where the owner opens the editor,
     * finds it read-only, and has no in-game way to grant themselves the node that would unlock it.
     */
    public static final String LP_EDIT = "customperm.gui.luckperms.edit";

    /**
     * Access to {@code /customperm}, the admin interface and admin alerts. It only restricts: op level 2
     * stays required, and an explicit DENY (or a denied {@code *} / {@code customperm.*}) takes access
     * away from an operator. Granting it to a non-operator opens nothing.
     */
    public static final String ADMIN = "customperm.admin";

    /**
     * Write access to one area of the admin interface. Same rule as {@link #LP_EDIT}: reading needs
     * op level 2, writing needs the area's node, level 4 bypasses. One node per area rather than a
     * single "edit everything" node, so a helper can be trusted with aliases without being able to
     * expose commands or change grades.
     */
    public static final String GUI_COMMANDS_EDIT = "customperm.gui.commands.edit";
    public static final String GUI_ALIASES_EDIT = "customperm.gui.aliases.edit";
    public static final String GUI_RATELIMITS_EDIT = "customperm.gui.ratelimits.edit";
    public static final String GUI_GRADES_EDIT = "customperm.gui.grades.edit";

    /** Every fixed node above, for command suggestions. */
    public static java.util.List<String> all() {
        return java.util.List.of(ADMIN, GUI_COMMANDS_EDIT, GUI_ALIASES_EDIT, GUI_RATELIMITS_EDIT, GUI_GRADES_EDIT, LP_EDIT);
    }

    private PermissionNodes() {
    }
}
