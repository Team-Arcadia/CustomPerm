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
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Admin alerts end to end: raised by the real triggers, shown by /customperm status, resolved by a
 * successful reload. Delivery to the chat of connected operators is covered by TestPlayerHarnessTest,
 * the de-duplication rule by AdminAlertsTest.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class AdminNotificationTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void failedReloadRaisesConfigAlertAndSuccessResolvesIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path grades = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("customperm").resolve("grades.json");
        try {
            CustomPerm.configManager.save();
            String valid = Files.readString(grades);
            try {
                Files.writeString(grades, "{ INVALID JSON !!!");
                ServerCommands.run(server, "customperm reload");
                if (!AdminNotifier.isActive(AdminAlerts.Key.CONFIG_LOAD_FAILED))
                    fail("A failed /customperm reload must raise the config alert.");
                String alert = AdminNotifier.activeAlerts().get(AdminAlerts.Key.CONFIG_LOAD_FAILED);
                if (!alert.contains("grades.json"))
                    fail("The config alert must name the invalid file, got: " + alert);
                if (ServerCommands.run(server, "customperm status").stream().noneMatch(line -> line.contains("grades.json")))
                    fail("/customperm status must list the active config alert.");
            } finally {
                Files.writeString(grades, valid);
                ServerCommands.run(server, "customperm reload");
            }
            if (AdminNotifier.isActive(AdminAlerts.Key.CONFIG_LOAD_FAILED))
                fail("A successful /customperm reload must resolve the config alert.");
            if (ServerCommands.run(server, "customperm status").stream().noneMatch(line -> line.contains("Admin alerts       : none")))
                fail("/customperm status must report no alert once resolved.");
        } catch (IOException e) {
            fail("IO error: " + e.getMessage());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void luckPermsAlertDescribesTheFallback(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean wasActive = AdminNotifier.isActive(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE);
        try {
            CustomPerm.raiseLuckPermsUnavailable("gametest");
            String alert = AdminNotifier.activeAlerts().get(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE);
            if (alert == null || !alert.contains("gametest") || !alert.contains("until the server restarts"))
                fail("LuckPerms alert must carry the reason and the lasting effect, got: " + alert);
            String expectedEffect = CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()
                    ? "internal grades" : "denies every permission";
            if (!alert.contains(expectedEffect))
                fail("LuckPerms alert must state what CustomPerm does instead (" + expectedEffect + "), got: " + alert);
            if (ServerCommands.run(server, "customperm status").stream().noneMatch(line -> line.contains("LuckPerms is unavailable")))
                fail("/customperm status must list the LuckPerms alert.");
        } finally {
            if (!wasActive) AdminNotifier.clear(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "gametest cleanup");
        }
        helper.succeed();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
