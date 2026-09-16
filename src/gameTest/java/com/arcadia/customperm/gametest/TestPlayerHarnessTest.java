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
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.GuiSyncPayload;
import com.arcadia.customperm.network.NetworkHandler;
import com.arcadia.customperm.network.RequestGuiSyncPayload;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Method;

/**
 * Checks that {@link TestPlayer} really provides what the other GameTests rely on, then uses it for
 * the two behaviours that needed a connected player: GUI sync gating and admin alert delivery.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class TestPlayerHarnessTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void permissionLevelDrivesCommandAccess(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_h_player", 0);
             TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_h_op", 2)) {
            if (player.canUse("customperm")) fail("A level-0 player must not see /customperm.");
            if (!op.canUse("customperm")) fail("A level-2 player must see /customperm.");
            if (player.commandTreesReceived() < 1) fail("Joining must push a command tree to the player.");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void chatAndCommandTreeAreCaptured(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_h_chat", 2)) {
            op.clearReceived();
            op.type("customperm status");
            if (!op.chatContains("=== CustomPerm Status ==="))
                fail("Chat output of a typed command must be captured, got: " + op.chat());
            long before = op.commandTreesReceived();
            helper.getLevel().getServer().getCommands().sendCommands(op.player());
            if (op.commandTreesReceived() != before + 1) fail("A command tree resync must be captured.");
        }
        helper.succeed();
    }

    /**
     * RequestGuiSyncPayload handling is the only thing keeping grades and aliases on the server for
     * non-operators (the client command gate is UX only). Run with a real player, not a stub.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void guiSyncIsOnlyAnsweredForOperators(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_h_gui_player", 0);
             TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_h_gui_op", 2)) {
            player.clearReceived();
            op.clearReceived();
            requestGuiSync(player.payloadContext());
            requestGuiSync(op.payloadContext());
            if (!player.payloads(GuiSyncPayload.class).isEmpty())
                fail("A non-operator must receive no GUI snapshot.");
            var snapshots = op.payloads(GuiSyncPayload.class);
            if (snapshots.size() != 1) fail("An operator must receive exactly one GUI snapshot, got " + snapshots.size());
            if (!snapshots.get(0).backendLabel().equals(CustomPerm.backendLabel()))
                fail("The snapshot must describe the active backend.");
        }
        helper.succeed();
    }

    // Own batch: batches run one after another, so no parallel test can clear or re-raise this alert
    // while the delayed checks are pending.
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_alert_delivery")
    public static void adminAlertReachesOperatorsOnlyAndOnLogin(GameTestHelper helper) {
        String marker = "cp-harness-" + System.nanoTime();
        boolean wasActive = AdminNotifier.isActive(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE);
        TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_h_alert_pl", 0);
        TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_h_alert_op", 2);
        player.clearReceived();
        op.clearReceived();
        CustomPerm.raiseLuckPermsUnavailable(marker);

        // Broadcast is handed to the server thread, so it lands on a later tick: check once, two ticks on.
        helper.runAfterDelay(2, () -> {
            try {
                if (!op.chatContains(marker)) fail("An online operator must receive the alert, got: " + op.chat());
                if (player.chatContains(marker)) fail("A non-operator must not receive admin alerts.");
                try (TestPlayer lateOp = TestPlayer.join(helper.getLevel(), "cp_h_alert_late", 2)) {
                    if (!lateOp.chatContains(marker))
                        fail("An operator joining while the alert is active must receive it on login.");
                }
                op.clearReceived();
                CustomPerm.raiseLuckPermsUnavailable(marker);
                helper.runAfterDelay(2, () -> {
                    try {
                        if (op.chatContains(marker)) fail("An unchanged alert must not be sent twice.");
                        helper.succeed();
                    } finally {
                        cleanupAlertTest(player, op, wasActive);
                    }
                });
            } catch (RuntimeException e) {
                cleanupAlertTest(player, op, wasActive);
                throw e;
            }
        });
    }

    private static void cleanupAlertTest(TestPlayer player, TestPlayer op, boolean wasActive) {
        player.close();
        op.close();
        if (!wasActive) AdminNotifier.clear(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "harness test cleanup");
    }

    private static void requestGuiSync(IPayloadContext context) {
        try {
            Method handler = NetworkHandler.class.getDeclaredMethod(
                    "handleRequestSync", RequestGuiSyncPayload.class, IPayloadContext.class);
            handler.setAccessible(true);
            handler.invoke(null, new RequestGuiSyncPayload(), context);
        } catch (ReflectiveOperationException e) {
            fail("Could not invoke the GUI sync handler: " + e);
        }
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
