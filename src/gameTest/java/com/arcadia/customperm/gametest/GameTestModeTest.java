/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Guards the two GameTest modes declared in build.gradle. Tests that only apply to one backend skip
 * themselves in the other; without this check a LuckPerms run that silently lost LuckPerms would
 * report every LuckPerms test as passed.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class GameTestModeTest {

    /** Set by the run configurations in build.gradle; absent when the server is started another way. */
    public static final String MODE_PROPERTY = "customperm.gametest.luckperms";

    @GameTest(template = "empty_3x3", timeoutTicks = 100)
    public static void backendMatchesTheRunMode(GameTestHelper helper) {
        String mode = System.getProperty(MODE_PROPERTY);
        if ("true".equals(mode) && !CustomPerm.isLuckPermsActive()) {
            throw new GameTestAssertException("LuckPerms mode, but the active backend is " + CustomPerm.backendLabel()
                    + ". Check that runGameTestServerLuckPerms copied the LuckPerms jar into its mods folder.");
        }
        if ("false".equals(mode) && CustomPerm.isLuckPermsPresent()) {
            throw new GameTestAssertException("Internal mode, but LuckPerms is loaded. Its mods folder must stay empty.");
        }
        helper.succeed();
    }
}
