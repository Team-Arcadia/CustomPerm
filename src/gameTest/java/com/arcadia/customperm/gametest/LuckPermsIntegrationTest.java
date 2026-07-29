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
import com.arcadia.customperm.perm.InternalPermService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTests for LuckPerms detection and InternalPermService behaviour (AC2 — story 6-2).
 *
 * LuckPerms is a {@code compileOnly} dependency — it is NOT present at GameTestServer
 * runtime. All tests therefore exercise the internal backend path.
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
     * Placeholder — testing the actual LuckPerms backend requires LuckPerms to be loaded
     * at runtime, which is not the case in the GameTestServer environment. Deferred.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void lpFallbackPlaceholder(GameTestHelper helper) {
        // Placeholder: LuckPerms not loaded in GameTestServer (compileOnly) — LP-specific
        // integration tests require a server with LP present. Deferred.
        helper.succeed();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
