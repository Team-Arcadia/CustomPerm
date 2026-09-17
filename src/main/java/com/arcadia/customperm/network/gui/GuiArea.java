/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.perm.PermissionNodes;

/**
 * An area of the admin interface with its own write permission. The client receives a bit per area
 * ({@link GuiContext#editMask()}) only to show a read-only screen instead of buttons that would be
 * refused; the server re-checks the node on every action.
 */
public enum GuiArea {
    COMMANDS(PermissionNodes.GUI_COMMANDS_EDIT),
    ALIASES(PermissionNodes.GUI_ALIASES_EDIT),
    RATE_LIMITS(PermissionNodes.GUI_RATELIMITS_EDIT),
    GRADES(PermissionNodes.GUI_GRADES_EDIT),
    LUCKPERMS(PermissionNodes.LP_EDIT);

    private final String node;

    GuiArea(String node) {
        this.node = node;
    }

    public String node() {
        return node;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }
}
