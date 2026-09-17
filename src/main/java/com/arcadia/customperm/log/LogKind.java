/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.log;

/** The two tabs of the activity log. */
public enum LogKind {
    /** Configuration and permission changes: CustomPerm commands, the admin interface, LuckPerms. */
    ADMIN("admin"),
    /** Commands typed by players, when the player log is on. */
    PLAYERS("players");

    private final String prefix;

    LogKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
