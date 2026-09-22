/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRoundTripHistory() throws IOException {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<String, Map<UUID, List<Long>>> history = Map.of(
                "gamemode", Map.of(alice, List.of(1_789_000_000_000L, 1_789_000_001_000L)),
                "heal", Map.of(bob, List.of(1_789_000_002_000L)));
        Path file = tempDir.resolve("data").resolve("customperm_ratelimits.json");

        RateLimitStore.write(file, history);

        assertEquals(history, RateLimitStore.read(file));
        assertTrue(Files.readString(file).contains("\"formatVersion\": " + RateLimitStore.FORMAT_VERSION));
        try (var stream = Files.list(file.getParent())) {
            assertFalse(stream.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "the atomic write must not leave its temporary file behind");
        }
    }

    @Test
    void shouldReturnEmptyHistory_whenFileDoesNotExist() throws IOException {
        assertTrue(RateLimitStore.read(tempDir.resolve("missing.json")).isEmpty());
    }

    @Test
    void shouldThrow_whenFileIsNotJson() throws IOException {
        Path file = tempDir.resolve("broken.json");
        Files.writeString(file, "{ not json");
        assertThrows(IOException.class, () -> RateLimitStore.read(file),
                "an unreadable file must be reported so the caller can set it aside, not overwritten silently");
    }

    @Test
    void shouldSkipEntriesItCannotUnderstand() throws IOException {
        UUID valid = UUID.randomUUID();
        Path file = tempDir.resolve("partial.json");
        Files.writeString(file, "{\"formatVersion\":1,\"history\":{"
                + "\"gamemode\":{\"not-a-uuid\":[1],\"" + valid + "\":[\"text\",5,true]},"
                + "\"weird\":42,"
                + "\"empty\":{\"" + UUID.randomUUID() + "\":[]}}}");

        Map<String, Map<UUID, List<Long>>> history = RateLimitStore.read(file);

        assertEquals(Map.of("gamemode", Map.of(valid, List.of(5L))), history);
    }

    @Test
    void shouldRoundTripTheWindowsInUse() throws IOException {
        Path file = tempDir.resolve("ratelimits-history.json");
        UUID player = UUID.randomUUID();
        Map<String, Map<Long, Long>> windows = Map.of("hub", Map.of(3_600_000L, 42L, 60_000L, 43L));
        RateLimitStore.write(file, Map.of("hub", Map.of(player, List.of(42L))), windows);

        assertEquals(windows, RateLimitStore.readWindows(file));
        assertEquals(Map.of("hub", Map.of(player, List.of(42L))), RateLimitStore.read(file));
    }

    @Test
    void aFileWithoutWindowsReadsAsBefore() throws IOException {
        Path file = tempDir.resolve("ratelimits-history.json");
        Files.writeString(file, "{\"formatVersion\": 1, \"history\": {}}");
        assertTrue(RateLimitStore.readWindows(file).isEmpty());
        assertTrue(RateLimitStore.readWindows(tempDir.resolve("missing.json")).isEmpty());

        Files.writeString(file, "{\"history\": {}, \"windows\": {\"hub\": {\"x\": 1, \"-5\": 2, \"60000\": \"no\", \"1000\": 7}}}");
        assertEquals(Map.of("hub", Map.of(1000L, 7L)), RateLimitStore.readWindows(file), "unusable entries are skipped");
    }
}
