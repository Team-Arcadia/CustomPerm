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
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.config.UpgradeNotice;
import com.arcadia.customperm.gametest.support.TestPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Upgrade notice (backlog item 11): a configuration written before 1.1.0 is announced once, to the log, to the
 * operators online and to any operator who joins, even one holding no CustomPerm node, which is what an upgrade
 * leaves them as. Own batch: it touches the settings of the whole server.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeNoticeGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_upgrade_notice")
    public static void anOlderConfigurationIsAnnouncedOnceAndStamped(GameTestHelper helper) {
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        int before = settings.configVersion;
        try {
            settings.configVersion = 0;  // what a settings.json written by 1.0.x reads as
            UpgradeNotice.clear();
            UpgradeNotice.onServerStarted();

            if (UpgradeNotice.pending() == null) fail("The upgrade must leave a notice for operators.");
            if (settings.configVersion != SettingsConfig.CURRENT_CONFIG_VERSION)
                fail("The configuration must be stamped with the current version, got " + settings.configVersion);

            // An operator with no CustomPerm node still has to be told: that is exactly what the upgrade leaves.
            try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_u_op", 4)) {
                if (op.chat().stream().noneMatch(line -> line.contains("upgraded")))
                    fail("An operator without nodes was not told about the upgrade: " + op.chat());
            }

            UpgradeNotice.clear();
            UpgradeNotice.onServerStarted();
            if (UpgradeNotice.pending() != null) fail("A stamped configuration must say nothing.");
        } finally {
            settings.configVersion = before;
            UpgradeNotice.clear();
        }
        helper.succeed();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
