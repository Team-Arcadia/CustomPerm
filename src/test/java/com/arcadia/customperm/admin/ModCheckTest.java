/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reading which API a mod's classes name, on plain files standing for a mod jar. */
class ModCheckTest {

    @TempDir
    Path mod;

    @Test
    void countsTheClassesNamingLuckPermsAndNotesNeoForge() throws IOException {
        write("a/Hook.class", "Êþº¾Lnet/luckperms/api/LuckPerms;");
        write("a/Other.class", "calls net/luckperms/api/model/user/User and more");
        write("a/Perms.class", "net/neoforged/neoforge/server/permission/PermissionAPI");
        write("a/Plain.class", "net/minecraft/server/level/ServerPlayer");
        write("assets/readme.txt", "net/luckperms/api/ in a text file is not a class");

        assertEquals(new ModCheck.Usage(2, true), ModCheck.scan(mod));
    }

    @Test
    void aModNamingNeitherIsClean() throws IOException {
        write("b/Main.class", "net/luckperms/ap");
        assertEquals(new ModCheck.Usage(0, false), ModCheck.scan(mod));
    }

    @Test
    void theReportSaysWhatEachFindingMeansWithoutLuckPerms() {
        ModCheck.Report report = new ModCheck.Report(List.of(
                new ModCheck.Finding("Homes (homes)", new ModCheck.Usage(3, false)),
                new ModCheck.Finding("Claims (claims)", new ModCheck.Usage(1, true))), 12, 40, List.of());
        List<String> lines = ModCheck.lines(report, false);
        assertTrue(lines.get(0).startsWith("2 installed mod file(s)"));
        assertTrue(lines.get(1).contains("Homes (homes): 3 class(es). LuckPerms only"));
        assertTrue(lines.get(2).contains("Claims (claims): 1 class(es). Also uses NeoForge"));
        assertTrue(lines.get(3).startsWith("Without LuckPerms"));
        assertTrue(ModCheck.lines(report, true).get(3).startsWith("LuckPerms is installed"));

        List<String> clean = ModCheck.lines(new ModCheck.Report(List.of(), 12, 40, List.of("Broken (broken)")), false);
        assertTrue(clean.get(0).startsWith("No installed mod calls LuckPerms' API directly (12 mod files"));
        assertEquals("Could not be read: Broken (broken).", clean.get(1));
    }

    @Test
    void containsFindsANeedleAnywhereAndNotPastTheEnd() {
        byte[] needle = "abc".getBytes(StandardCharsets.US_ASCII);
        assertTrue(ModCheck.contains("abc".getBytes(StandardCharsets.US_ASCII), needle));
        assertTrue(ModCheck.contains("xxabc".getBytes(StandardCharsets.US_ASCII), needle));
        assertFalse(ModCheck.contains("xxab".getBytes(StandardCharsets.US_ASCII), needle));
        assertFalse(ModCheck.contains(new byte[0], needle));
    }

    private void write(String path, String content) throws IOException {
        Path file = mod.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1));
    }
}
