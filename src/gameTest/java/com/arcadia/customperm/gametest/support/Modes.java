/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.arcadia.customperm.CustomPerm;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Backend guards for tests that only make sense in one GameTest mode. A skipped test succeeds;
 * GameTestModeTest makes sure each mode really runs with the backend it claims.
 */
public final class Modes {

    private Modes() {
    }

    /** True when the internal backend is active; otherwise marks the test passed and returns false. */
    public static boolean internalOnly(GameTestHelper helper) {
        if (!CustomPerm.isLuckPermsActive()) return true;
        helper.succeed();
        return false;
    }

    /** True when LuckPerms is the active backend; otherwise marks the test passed and returns false. */
    public static boolean luckPermsOnly(GameTestHelper helper) {
        if (CustomPerm.isLuckPermsActive()) return true;
        helper.succeed();
        return false;
    }
}
