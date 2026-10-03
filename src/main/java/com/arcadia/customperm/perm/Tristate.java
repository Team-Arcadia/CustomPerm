/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

/**
 * The explicit value of a permission node for a player: granted, refused, or not set at all. Keeping
 * "not set" apart from "refused" is what lets an operator keep the vanilla behaviour by default while
 * an explicit refusal still applies to them.
 */
public enum Tristate {
    ALLOW,
    DENY,
    UNSET
}
