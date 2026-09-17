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
 * What currently answers CustomPerm's permission checks. The text commands print {@link #label()};
 * the admin interface receives the kind itself and adapts its navigation to it.
 */
public enum BackendKind {
    /** Internal grades from grades.json; LuckPerms is not installed. */
    INTERNAL("Internal"),
    /** LuckPerms resolves every node. */
    LUCKPERMS("LuckPerms"),
    /** LuckPerms is installed but unusable; internal grades answer until restart. */
    INTERNAL_FALLBACK("Internal — fallback from LuckPerms"),
    /** LuckPerms is installed but unusable; every managed permission is denied until restart. */
    DENY("Deny — LuckPerms unavailable");

    private final String label;

    BackendKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Whether CustomPerm's own grades decide permissions, so the grades area applies. */
    public boolean usesInternalGrades() {
        return this == INTERNAL || this == INTERNAL_FALLBACK;
    }
}
