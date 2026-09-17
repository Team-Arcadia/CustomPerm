/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import java.util.Locale;

/**
 * The pages of the admin interface. A page travels by name, never by ordinal, so a malformed or
 * outdated request is a rejected string rather than an index into the wrong page.
 */
public enum GuiPage {
    DASHBOARD,
    COMMANDS,
    ALIASES,
    RATE_LIMITS,
    GRADES;

    /** Name used on the wire and as the {@code /customperm gui <page>} argument. */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace("_", "");
    }

    /** Resolves a page id; {@code null} for anything unknown. */
    public static GuiPage fromId(String id) {
        for (GuiPage page : values()) {
            if (page.id().equals(id)) return page;
        }
        return null;
    }
}
