/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void freshInstallIsStampedWithTheCurrentConfigVersion() {
        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load(), "a fresh install must load");
        assertEquals(SettingsConfig.CURRENT_CONFIG_VERSION, mgr.getSettings().configVersion,
            "no settings.json means a fresh install, not an upgrade");
    }

    @Test
    void settingsWrittenBeforeTheVersionFieldReadAsVersionZero() throws Exception {
        java.nio.file.Files.writeString(tempDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"internal\"}");
        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load(), "a 1.0.x settings.json must still load");
        assertEquals(0, mgr.getSettings().configVersion, "a file without the field is an upgrade to announce");
        assertEquals("internal", mgr.getSettings().luckPermsFallbackMode);
    }

    @Test
    void shouldServeConsistentSnapshot_underConcurrentReads() throws Exception {
        // Arrange
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();
        ConfigSnapshot expected = mgr.getSnapshot();

        int threadCount = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<ConfigSnapshot>> futures = new ArrayList<>();

        // Act: 50 threads read getSnapshot() at once
        for (int i = 0; i < threadCount; i++) {
            futures.add(exec.submit(mgr::getSnapshot));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS));

        // Assert: every thread sees the same instance, which the AtomicReference guarantees
        for (Future<ConfigSnapshot> f : futures) {
            assertSame(expected, f.get(),
                "every thread must get the same snapshot instance");
        }
    }

    @Test
    void shouldSerializeConcurrentSaves_withoutLeavingTemporaryFiles() throws Exception {
        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load());

        int threadCount = 16;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(exec.submit(mgr::save));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "every concurrent save must succeed");
        }
        assertTrue(mgr.load(), "the files written must still be readable afterwards");

        try (var stream = Files.list(tempDir)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                    "a successful save must leave no temporary file behind");
        }
    }

    @Test
    void shouldRejectConcurrentReload_whenReloadInProgress() throws Exception {
        // Arrange
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();

        // Stand in for a reload already running by setting the flag by hand
        // (package-private, this test being in the same package)
        mgr.reloading.set(true);

        // Act: a second reload while the first one is "running"
        boolean result = mgr.load();

        // Assert
        assertFalse(result, "a reload must be refused while another one is running");

        // Cleanup: clear the flag so it blocks nothing else
        mgr.reloading.set(false);
    }

    @Test
    void shouldRetainPreviousSnapshot_afterFailedReload() throws Exception {
        // Arrange: a valid config holding a grade, loaded from disk
        Files.writeString(tempDir.resolve("grades.json"),
                "{\"grades\":{\"vip\":{\"permissions\":[\"customperm.test\"]}}}");
        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load());
        ConfigSnapshot before = mgr.getSnapshot();

        // Act: another file becomes unparseable, so the next load fails
        Files.writeString(tempDir.resolve("aliases.json"), "{ nope");
        assertFalse(mgr.load(), "an unparseable aliases.json must fail the load");

        // Assert: the previous snapshot is kept, instance and content alike (INVARIANT-401)
        assertSame(before, mgr.getSnapshot(), "a failed reload must not replace the snapshot");
        assertTrue(mgr.getGrades().grades.containsKey("vip"),
                "the grades loaded before the failure must still answer");
    }

    // ─── H1.4: automatic backup and recovery from an invalid config ─────────────

    @Test
    void shouldRollbackSnapshot_whenGradesJsonIsInvalid() throws Exception {
        // Arrange: load a valid config first
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();
        ConfigSnapshot before = mgr.getSnapshot();

        // Corrupt grades.json with invalid JSON (JsonSyntaxException)
        Files.writeString(tempDir.resolve("grades.json"), "{ INVALID JSON !!!");

        // Act
        boolean result = mgr.load();

        // Assert, INVARIANT-401: the snapshot is unchanged and load() returns false
        assertFalse(result, "load() must return false when a JSON file is invalid");
        assertSame(before, mgr.getSnapshot(),
                "the snapshot must survive a failed reload unchanged (INVARIANT-401)");
    }

    @Test
    void shouldNotOverwriteInvalidFile_whenSavingAfterFailedLoad() throws Exception {
        String broken = "{\"grades\":{\"vip\":{\"permissions\":[\"a.b\"]}}";  // missing closing brace
        Files.writeString(tempDir.resolve("grades.json"), broken);

        ConfigManager mgr = new ConfigManager(tempDir);
        assertFalse(mgr.load(), "an unparseable grades.json must fail the load");
        assertFalse(mgr.isDiskWritable());

        // An admin command mutating the (empty) in-memory snapshot, then saving.
        mgr.getCommands().grantedCommands.add("gamemode");
        assertFalse(mgr.save(), "save must be refused while the disk holds an unparseable file");
        assertEquals(broken, Files.readString(tempDir.resolve("grades.json")),
                "the admin's file must survive untouched so it can be fixed");

        Files.writeString(tempDir.resolve("grades.json"), broken + "}");
        assertTrue(mgr.load(), "a fixed file must load");
        assertTrue(mgr.isDiskWritable());
        assertTrue(mgr.getGrades().grades.containsKey("vip"));
        assertTrue(mgr.save(), "saving resumes after a successful load");
    }

    @Test
    void shouldNameInvalidFiles_andForgetThemAfterSuccessfulLoad() throws Exception {
        Files.writeString(tempDir.resolve("aliases.json"), "{ nope");
        Files.writeString(tempDir.resolve("ratelimits.json"), "[");
        ConfigManager mgr = new ConfigManager(tempDir);

        assertFalse(mgr.load());
        assertEquals("invalid or empty aliases.json, ratelimits.json", mgr.getLastLoadFailure(),
                "admins are told which files to fix");

        Files.delete(tempDir.resolve("aliases.json"));
        Files.delete(tempDir.resolve("ratelimits.json"));
        assertTrue(mgr.load());
        assertNull(mgr.getLastLoadFailure());
    }

    @Test
    void shouldRefuseSaves_whenSuspendedBeforeAnyLoad() throws Exception {
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.suspendSaves("config initialisation failed");

        assertFalse(mgr.save(), "an empty snapshot that never came from disk must not be written");
        assertFalse(Files.exists(tempDir.resolve("grades.json")));
        assertEquals("config initialisation failed", mgr.getLastLoadFailure());
    }

    @Test
    void shouldCreateBackupFiles_afterSuccessfulLoad() throws Exception {
        // Arrange and act: a successful load() writes one .bak per config file into backup/
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();

        // Assert
        Path backupDir = tempDir.resolve("backup");
        assertTrue(Files.isDirectory(backupDir), "backup/ must exist after a successful load");

        long backupCount;
        try (var stream = Files.list(backupDir)) {
            backupCount = stream
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .count();
        }
        assertEquals(5L, backupCount,
                "exactly 5 .bak files must be written (grades, aliases, commands, settings, ratelimits)");
    }

    @Test
    void shouldMigrateLegacyConfigDirectory_whenNewDirectoryDoesNotExist() throws Exception {
        Path legacyDir = tempDir.resolve("customperm");
        Path newDir = tempDir.resolve("arcadia").resolve("customperm");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("grades.json"),
                "{\"grades\":{\"vip\":{\"permissions\":[\"customperm.test\"]}}}");
        Files.writeString(legacyDir.resolve("aliases.json"), "{\"aliases\":{}}");
        Files.writeString(legacyDir.resolve("commands.json"), "{\"grantedCommands\":[\"spawn\"]}");
        Files.writeString(legacyDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"internal\"}");

        ConfigManager mgr = new ConfigManager(newDir, legacyDir);

        assertTrue(mgr.load(), "load() must migrate the legacy config directory");
        assertTrue(Files.exists(newDir.resolve("grades.json")), "grades.json must be copied to the new directory");
        assertTrue(Files.exists(newDir.resolve("settings.json")), "settings.json must be copied to the new directory");
        assertTrue(Files.exists(legacyDir.resolve("grades.json")), "the migration must not delete the old file");
        assertTrue(mgr.getGrades().grades.containsKey("vip"), "the migrated config must be loaded");
        assertTrue(mgr.getCommands().grantedCommands.contains("spawn"), "the migrated commands.json must be loaded");
        assertEquals("internal", mgr.getSettings().luckPermsFallbackMode, "the migrated settings.json must be loaded");
    }

    @Test
    void shouldRetainOnlyThreeBackups_whenRotationLimitExceeded() throws Exception {
        // Arrange: create backup/ and drop older backups into it
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();   // writes the first set of backups, one .bak per file
        Path backupDir = tempDir.resolve("backup");

        // Add 3 more fake backups of grades.json, with old timestamps
        Files.writeString(backupDir.resolve("grades.json.2020-01-01T00-00-01.bak"), "{}");
        Files.writeString(backupDir.resolve("grades.json.2020-01-01T00-00-02.bak"), "{}");
        Files.writeString(backupDir.resolve("grades.json.2020-01-01T00-00-03.bak"), "{}");
        // grades.json now has 4 backups, so rotateBackups must delete one

        // Act: call rotateBackups directly (package-private, same package)
        mgr.rotateBackups(backupDir, "grades.json");

        // Assert: exactly 3 grades.json backups left, the oldest ones deleted
        long gradesBackups;
        try (var stream = Files.list(backupDir)) {
            gradesBackups = stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("grades.json.") && n.endsWith(".bak");
                    })
                    .count();
        }
        assertEquals(3L, gradesBackups,
                "the rotation must keep exactly 3 backups per file (AR10)");
        assertFalse(Files.exists(backupDir.resolve("grades.json.2020-01-01T00-00-01.bak")),
                "the oldest backup must be deleted");
    }
}
