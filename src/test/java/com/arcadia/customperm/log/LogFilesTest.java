/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogFilesTest {

    private static final List<String> MASKED = List.of("msg", "login");

    @TempDir
    Path dir;

    @Test
    void fileNamesRoundTrip() {
        LocalDate day = LocalDate.of(2026, 9, 17);
        assertEquals("admin-2026-09-17.jsonl", LogFiles.fileName(LogKind.ADMIN, day));
        assertEquals(day, LogFiles.dayOf(LogKind.PLAYERS, "players-2026-09-17.jsonl"));
        assertNull(LogFiles.dayOf(LogKind.ADMIN, "players-2026-09-17.jsonl"));
        assertNull(LogFiles.dayOf(LogKind.ADMIN, "admin-2026-13-40.jsonl"));
        assertNull(LogFiles.dayOf(LogKind.ADMIN, "admin-2026-09-17.txt"));
    }

    @Test
    void purgeKeepsTheRetentionWindowAndForeignFiles() throws Exception {
        LocalDate today = LocalDate.of(2026, 9, 17);
        Path kept = touch("admin-2026-09-17.jsonl");
        Path lastKept = touch("players-2026-08-19.jsonl");
        Path old = touch("admin-2026-08-18.jsonl");
        Path foreign = touch("notes-2000-01-01.jsonl");

        List<Path> deleted = LogFiles.purge(dir, today, 30);

        assertEquals(List.of(old), deleted);
        assertTrue(Files.exists(kept));
        assertTrue(Files.exists(lastKept), "the 30th day, today included, is kept");
        assertTrue(Files.exists(foreign), "only log files are ever deleted");
    }

    @Test
    void zeroRetentionKeepsEverything() throws Exception {
        Path old = touch("admin-2000-01-01.jsonl");
        assertTrue(LogFiles.purge(dir, LocalDate.of(2026, 9, 17), 0).isEmpty());
        assertTrue(Files.exists(old));
    }

    @Test
    void newestFirstOrdersByDay() throws Exception {
        touch("admin-2026-09-01.jsonl");
        touch("admin-2026-09-17.jsonl");
        touch("admin-2025-12-31.jsonl");
        assertEquals(List.of("admin-2026-09-17.jsonl", "admin-2026-09-01.jsonl", "admin-2025-12-31.jsonl"),
                LogFiles.newestFirst(dir, LogKind.ADMIN).stream().map(p -> p.getFileName().toString()).toList());
    }

    @Test
    void maskingReplacesArgumentsOfListedCommandsOnly() {
        assertEquals("/msg [masked]", LogFiles.maskCommand("msg Alex see you", true, MASKED, 512));
        assertEquals("/minecraft:msg [masked]", LogFiles.maskCommand("/minecraft:msg Alex hi", true, MASKED, 512));
        assertEquals("/LOGIN [masked]", LogFiles.maskCommand("LOGIN hunter2", true, MASKED, 512));
        assertEquals("/msg", LogFiles.maskCommand("msg", true, MASKED, 512), "nothing to mask without arguments");
        assertEquals("/give Alex diamond", LogFiles.maskCommand("give Alex diamond", true, MASKED, 512));
        assertEquals("/msg Alex see you", LogFiles.maskCommand("msg Alex see you", false, MASKED, 512));
        assertEquals("/msgx a", LogFiles.maskCommand("msgx a", true, MASKED, 512), "roots are matched whole");
    }

    @Test
    void longCommandsAreCut() {
        assertEquals("/say aaaa...", LogFiles.maskCommand("say " + "a".repeat(100), true, MASKED, 9));
    }

    private Path touch(String name) throws Exception {
        return Files.writeString(dir.resolve(name), "{}\n");
    }
}
