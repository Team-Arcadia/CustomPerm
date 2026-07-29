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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GameTests for hot-reload and rollback behaviour (AC2 — story 6-2).
 *
 * Covers the gap from story 6-1: {@code shouldRetainPreviousSnapshot_afterFailedReload()}
 * was a placeholder in JUnit5. {@link #rollbackOnCorruptGradesJson()} implements the
 * REAL INVARIANT-401 test: corrupt grades.json → load() returns false → snapshot unchanged.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class HotReloadTest {

    private static final String TEMPLATE = "empty_3x3";

    /**
     * AC: a grade added in-memory, saved, and reloaded from disk survives the roundtrip.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void hotReloadPreservesGrade(GameTestHelper helper) {
        GradesConfig grades = CustomPerm.configManager.getGrades();
        GradesConfig.Grade marker = new GradesConfig.Grade();
        marker.name = "_ht_test_grade";
        marker.permissions.add("test.hotreload.perm");
        grades.grades.put("_ht_test_grade", marker);

        try {
            CustomPerm.configManager.save();
            CustomPerm.configManager.load();  // new snapshot created from disk

            GradesConfig reloaded = CustomPerm.configManager.getGrades();
            if (!reloaded.grades.containsKey("_ht_test_grade"))
                fail("Grade did not survive save+load roundtrip (hotReloadPreservesGrade).");
            if (!reloaded.grades.get("_ht_test_grade").permissions.contains("test.hotreload.perm"))
                fail("Permission lost during save+load roundtrip.");

            helper.succeed();
        } finally {
            // Always remove the test marker — even if fail() throws GameTestAssertException
            CustomPerm.configManager.getGrades().grades.remove("_ht_test_grade");
            CustomPerm.configManager.save();
        }
    }

    /**
     * INVARIANT-401 — rollback: corrupt grades.json must cause load() to return false
     * AND leave the snapshot unchanged (transaction tout-ou-rien).
     *
     * This is the REAL test of {@code shouldRetainPreviousSnapshot_afterFailedReload()}
     * which was a placeholder in {@code ConfigManagerTest.java} (story 6-1 gap).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void rollbackOnCorruptGradesJson(GameTestHelper helper) {
        try {
            // Ensure files are on disk and get the grades.json path
            CustomPerm.configManager.save();
            Path gradesPath = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("customperm").resolve("grades.json");
            String validContent = Files.readString(gradesPath);

            // Capture snapshot before corruption
            var before = CustomPerm.configManager.getSnapshot();

            // Corrupt → test → restore; finally guarantees file is always restored even if
            // fail() throws GameTestAssertException before the restore line is reached (F1).
            try {
                Files.writeString(gradesPath, "{ INVALID JSON !!!");

                // load() must fail (return false)
                boolean result = CustomPerm.configManager.load();
                if (result)
                    fail("load() must return false when grades.json is invalid JSON (INVARIANT-401).");

                // Snapshot must be identical object (not replaced) — rollback guarantee
                if (CustomPerm.configManager.getSnapshot() != before)
                    fail("Snapshot was replaced after a failed load — INVARIANT-401 violated.");

                helper.succeed();
            } finally {
                // Always restore valid content and reload to clean state
                Files.writeString(gradesPath, validContent);
                CustomPerm.configManager.load();
            }

        } catch (IOException e) {
            fail("IO error in rollbackOnCorruptGradesJson: " + e.getMessage());
        }
    }

    /**
     * Placeholder — re-push of the client CommandTree after hot-reload requires connected
     * clients. GameTestServer runs without clients so this is deferred.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void hotReloadCommandTreeRepush(GameTestHelper helper) {
        // Placeholder: CommandTree re-push requires connected clients (not available in GameTestServer).
        helper.succeed();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
