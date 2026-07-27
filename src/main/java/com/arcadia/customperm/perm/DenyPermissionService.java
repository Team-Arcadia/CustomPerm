/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import net.minecraft.commands.CommandSourceStack;

public class DenyPermissionService implements PermissionService {
    @Override
    public boolean hasPermission(CommandSourceStack source, String node) {
        return false;
    }
}
