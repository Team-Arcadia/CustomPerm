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
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.InternalPermService;
import com.arcadia.customperm.perm.LuckPermsService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GameTests for LuckPerms detection and InternalPermService behaviour (AC2 — story 6-2).
 *
 * Runs in both GameTest modes (see build.gradle): the detection tests adapt to the mode, and the
 * LuckPerms-only tests below skip themselves in the internal mode.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class LuckPermsIntegrationTest {

    private static final String TEMPLATE = "empty_3x3";

    /**
     * AC: the permissions backend is initialised (not null) after mod load.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void backendNotNull(GameTestHelper helper) {
        if (CustomPerm.permissions == null)
            fail("CustomPerm.permissions must not be null after mod initialisation.");
        helper.succeed();
    }

    /**
     * AC: backend selection is consistent with LuckPerms availability at runtime.
     *
     * In a clean GameTestServer (CI) without LP in run/mods/, InternalPermService is expected.
     * In a dev environment where LP is present in run/mods/, the LP-backed service is expected.
     * Either way, the selection must be coherent with ModList.isLoaded("luckperms").
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void backendSelectionMatchesLPAvailability(GameTestHelper helper) {
        boolean lpLoaded = ModList.get().isLoaded("luckperms");
        if (lpLoaded) {
            // LP present → must NOT have fallen back to InternalPermService
            if (CustomPerm.permissions instanceof InternalPermService)
                fail("LuckPerms is loaded but InternalPermService was selected — backend selection failed.");
        } else {
            // LP absent → InternalPermService must be selected
            if (!(CustomPerm.permissions instanceof InternalPermService))
                fail("Expected InternalPermService when LuckPerms is absent, got: "
                    + CustomPerm.permissions.getClass().getSimpleName());
        }
        helper.succeed();
    }

    /**
     * AC: DENY in {@code deniedPermissions} overrides ALLOW in {@code permissions}
     * for the same node (INVARIANT-101 — PermissionResolver.resolve).
     *
     * Uses a fresh GradesConfig (not the live configManager) for full isolation.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void denyOverridesAllowInInternalBackend(GameTestHelper helper) {
        GradesConfig grades = new GradesConfig();
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = "test_deny_allow";
        grade.permissions.add("customperm.command.fly");       // ALLOW
        grade.deniedPermissions.add("customperm.command.fly"); // DENY — must win
        grades.grades.put("test_deny_allow", grade);

        UUID uuid = UUID.randomUUID();
        grades.userGrades.put(uuid.toString(), new ArrayList<>(List.of("test_deny_allow")));

        if (grades.userHasPermission(uuid, "customperm.command.fly"))
            fail("DENY must override ALLOW on the same permission node (INVARIANT-101).");

        helper.succeed();
    }

    /**
     * Procedure 1.0.5 B1.4: removing a permission in LuckPerms resends the player's command tree without
     * a reconnect (UserDataRecalculateEvent hook). Own batch: other tests resend trees to every player.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_lp_resync")
    public static void luckPermsChangeResendsCommandTree(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_l_resync", 0);
        Grants grant = Grants.allow(player, "customperm.command.gamemode");
        ServerCommands.run(server, "customperm command add gamemode");
        if (!player.canUse("gamemode")) {
            cleanupResync(server, player, grant);
            fail("Setup: /gamemode granted through LuckPerms is not usable.");
        }
        player.clearReceived();
        grant.close();
        helper.runAfterDelay(40, () -> {
            try {
                if (player.canUse("gamemode")) fail("/gamemode still usable after the LuckPerms node was removed.");
                if (player.commandTreesReceived() < 1)
                    fail("Removing a LuckPerms node did not resend the command tree to the connected player.");
                helper.succeed();
            } finally {
                cleanupResync(server, player, grant);
            }
        });
    }

    private static void cleanupResync(MinecraftServer server, TestPlayer player, Grants grant) {
        grant.close();
        if (CustomPerm.configManager.getCommands().grantedCommands.contains("gamemode")) {
            ServerCommands.run(server, "customperm command remove gamemode");
        }
        player.close();
    }

    /**
     * Procedure 1.0.5 B2.1 + B2.2: once LuckPerms is marked unavailable, deny mode fails closed and
     * internal mode resolves from grades.json. The failure is simulated by flipping the service's
     * degraded flag; it is always restored. Own batch: while degraded, every permission check changes.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_lp_degradation")
    public static void degradedLuckPermsFollowsFallbackMode(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        LuckPermsService service = (LuckPermsService) CustomPerm.permissions;
        AtomicBoolean degraded = degradedFlag(service);
        var settings = CustomPerm.configManager.getSettings();
        String previousMode = settings.luckPermsFallbackMode;
        boolean alertWasActive = AdminNotifier.isActive(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE);
        TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_l_degraded", 0);
        Grants lpGrant = Grants.allow(player, "cp.degraded.node");
        GradesConfig grades = CustomPerm.configManager.getGrades();
        try {
            if (!service.hasPermission(player.source(), "cp.degraded.node")) fail("Setup: LuckPerms grant not visible.");
            degraded.set(true);
            if (CustomPerm.isLuckPermsActive()) fail("A degraded LuckPerms service must not report itself active.");

            settings.luckPermsFallbackMode = "deny";
            if (service.hasPermission(player.source(), "cp.degraded.node"))
                fail("B2.1: deny mode must fail closed, but the LuckPerms grant still applied.");
            if (!CustomPerm.backendLabel().contains("Deny")) fail("B2.1: status must report deny mode, got " + CustomPerm.backendLabel());

            settings.luckPermsFallbackMode = "internal";
            GradesConfig.Grade grade = new GradesConfig.Grade();
            grade.name = "cp_l_fallback";
            grade.permissions.add("cp.internal.node");
            grades.grades.put("cp_l_fallback", grade);
            grades.userGrades.put(player.uuid().toString(), new ArrayList<>(List.of("cp_l_fallback")));
            if (!service.hasPermission(player.source(), "cp.internal.node"))
                fail("B2.2: internal mode must resolve from grades.json.");
            if (service.hasPermission(player.source(), "cp.degraded.node"))
                fail("B2.2: internal mode must not keep using the LuckPerms grant.");
            if (!CustomPerm.backendLabel().contains("fallback")) fail("B2.2: status must report the internal fallback, got " + CustomPerm.backendLabel());
        } finally {
            degraded.set(false);
            settings.luckPermsFallbackMode = previousMode;
            grades.grades.remove("cp_l_fallback");
            grades.userGrades.remove(player.uuid().toString());
            lpGrant.close();
            player.close();
            if (!alertWasActive) AdminNotifier.clear(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "gametest cleanup");
        }
        helper.succeed();
    }

    private static AtomicBoolean degradedFlag(LuckPermsService service) {
        try {
            Field field = LuckPermsService.class.getDeclaredField("degraded");
            field.setAccessible(true);
            return (AtomicBoolean) field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Cannot reach LuckPermsService.degraded: " + e);
        }
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
