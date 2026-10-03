/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import net.minecraft.commands.CommandSourceStack;

/**
 * Fail-closed backend when LuckPerms is required but unusable: no node is ever granted, so only the
 * vanilla permission level still opens anything.
 */
public class DenyPermissionService implements PermissionService {
    @Override
    public Tristate check(CommandSourceStack source, String node) {
        return Tristate.UNSET;
    }
}
