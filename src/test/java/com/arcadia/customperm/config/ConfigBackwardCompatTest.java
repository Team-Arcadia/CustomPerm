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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backward compatibility of the config files (AC4, NFR9, NFR10).
 *
 * <p>Checks that:</p>
 * <ul>
 *   <li>an empty {@code {}} JSON is accepted for every file</li>
 *   <li>missing files give default configs, with empty collections rather than null</li>
 *   <li>unknown JSON fields, as a later version would add, are ignored without an error</li>
 * </ul>
 *
 * <p>Every test is pure Java, with no {@code net.minecraft.*} or
 * {@code net.neoforged.*} (AR8 / INVARIANT-504).</p>
 */
class ConfigBackwardCompatTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Case: the files are absent, so the defaults are empty
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnEmptyCollections_whenConfigFilesAreMissing() throws Exception {
        // Arrange: no JSON file in tempDir
        ConfigManager mgr = new ConfigManager(tempDir);

        // Act
        boolean loaded = mgr.load();

        // Assert
        assertTrue(loaded, "load() must return true even when the files are absent");

        GradesConfig grades = mgr.getGrades();
        assertNotNull(grades.grades, "grades.grades must not be null");
        assertTrue(grades.grades.isEmpty(), "grades.grades must be empty");
        assertNotNull(grades.userGrades, "grades.userGrades must not be null");
        assertTrue(grades.userGrades.isEmpty(), "grades.userGrades must be empty");

        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases, "aliases.aliases must not be null");
        assertTrue(aliases.aliases.isEmpty(), "aliases.aliases must be empty");

        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands, "commands.grantedCommands must not be null");
        assertTrue(commands.grantedCommands.isEmpty(), "commands.grantedCommands must be empty");

        SettingsConfig settings = mgr.getSettings();
        assertEquals("deny", settings.luckPermsFallbackMode,
                "luckPermsFallbackMode must default to deny");

        RateLimitsConfig rateLimits = mgr.getRateLimits();
        assertNotNull(rateLimits.rules, "rateLimits.rules must not be null");
        assertTrue(rateLimits.rules.isEmpty(), "rateLimits.rules must be empty");
    }

    @Test
    void shouldDropNullEntries_whenHandEditedJsonContainsNulls() throws Exception {
        Files.writeString(tempDir.resolve("grades.json"),
                "{\"grades\":{\"ghost\":null,\"vip\":{\"permissions\":[null,\"a.b\"],\"deniedPermissions\":null}},"
                        + "\"userGrades\":{\"00000000-0000-0000-0000-000000000001\":null,"
                        + "\"00000000-0000-0000-0000-000000000002\":[null,\"vip\"]}}");
        Files.writeString(tempDir.resolve("aliases.json"),
                "{\"aliases\":{\"broken\":null,\"half\":[null,\"say hi\"],\"empty\":[null]}}");
        Files.writeString(tempDir.resolve("commands.json"),
                "{\"grantedCommands\":[null,\"tp\"],\"preserveOriginalRequires\":{\"tp\":null}}");

        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load());

        GradesConfig grades = mgr.getGrades();
        assertFalse(grades.grades.containsKey("ghost"));
        assertEquals(java.util.Set.of("a.b"), grades.grades.get("vip").permissions);
        assertNotNull(grades.grades.get("vip").deniedPermissions);
        assertFalse(grades.userGrades.containsKey("00000000-0000-0000-0000-000000000001"));
        assertEquals(java.util.List.of("vip"), grades.userGrades.get("00000000-0000-0000-0000-000000000002"));

        AliasesConfig aliases = mgr.getAliases();
        assertFalse(aliases.aliases.containsKey("broken"));
        assertFalse(aliases.aliases.containsKey("empty"));
        assertEquals(java.util.List.of("say hi"), aliases.aliases.get("half"));

        CommandsConfig commands = mgr.getCommands();
        assertEquals(java.util.Set.of("tp"), commands.grantedCommands);
        assertFalse(commands.shouldPreserveOriginalRequires("tp"));
    }

    // -------------------------------------------------------------------------
    // Case: an empty {} JSON, the minimum of backward compatibility
    // -------------------------------------------------------------------------

    @Test
    void shouldLoadGradesConfig_whenGradesJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("grades.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must return true for grades.json = {}");
        GradesConfig grades = mgr.getGrades();
        assertNotNull(grades.grades, "grades.grades must not be null after {}");
        assertTrue(grades.grades.isEmpty());
        assertNotNull(grades.userGrades, "grades.userGrades must not be null after {}");
        assertTrue(grades.userGrades.isEmpty());
    }

    @Test
    void shouldLoadAliasesConfig_whenAliasesJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("aliases.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must return true for aliases.json = {}");
        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases, "aliases.aliases must not be null after {}");
        assertTrue(aliases.aliases.isEmpty());
    }

    @Test
    void shouldLoadAliasesConfig_whenAliasesFieldIsExplicitNull() throws Exception {
        Files.writeString(tempDir.resolve("aliases.json"), "{\"aliases\":null}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must normalize aliases:null rather than reject the config");
        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases, "aliases.aliases must not stay null once normalized");
        assertTrue(aliases.aliases.isEmpty());
    }

    @Test
    void shouldLoadCommandsConfig_whenCommandsJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("commands.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must return true for commands.json = {}");
        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands, "commands.grantedCommands must not be null after {}");
        assertTrue(commands.grantedCommands.isEmpty());
    }

    @Test
    void shouldLoadCommandsConfig_whenGrantedCommandsFieldIsExplicitNull() throws Exception {
        Files.writeString(tempDir.resolve("commands.json"), "{\"grantedCommands\":null}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must normalize grantedCommands:null rather than reject the config");
        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands, "commands.grantedCommands must not stay null once normalized");
        assertTrue(commands.grantedCommands.isEmpty());
    }

    @Test
    void shouldLoadCommandsConfig_whenPreserveOriginalRequiresIsPresent() throws Exception {
        Files.writeString(tempDir.resolve("commands.json"),
                "{\"grantedCommands\":[\"gamemode\",\"adminpanel\"],\"preserveOriginalRequires\":{\"gamemode\":false,\"adminpanel\":true}}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must read preserveOriginalRequires");
        CommandsConfig commands = mgr.getCommands();
        assertFalse(commands.shouldPreserveOriginalRequires("gamemode"));
        assertTrue(commands.shouldPreserveOriginalRequires("adminpanel"));
    }

    @Test
    void shouldLoadSettingsConfig_whenSettingsJsonIsPresent() throws Exception {
        Files.writeString(tempDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"internal\"}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must read settings.json");
        assertEquals("internal", mgr.getSettings().luckPermsFallbackMode);
    }

    @Test
    void shouldNormalizeSettingsConfigToDeny_whenFallbackModeIsInvalid() throws Exception {
        Files.writeString(tempDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"unsafe\"}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must normalize an invalid mode rather than reject the config");
        assertEquals("deny", mgr.getSettings().luckPermsFallbackMode);
    }

    @Test
    void shouldLoadRateLimitsConfig_whenRateLimitsJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("ratelimits.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must return true for ratelimits.json = {}");
        RateLimitsConfig rateLimits = mgr.getRateLimits();
        assertNotNull(rateLimits.rules, "rateLimits.rules must not be null after {}");
        assertTrue(rateLimits.rules.isEmpty());
    }

    @Test
    void shouldLoadRateLimitsConfig_whenRulesFieldIsExplicitNull() throws Exception {
        Files.writeString(tempDir.resolve("ratelimits.json"), "{\"rules\":null}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must normalize rules:null rather than reject the config");
        assertNotNull(mgr.getRateLimits().rules);
        assertTrue(mgr.getRateLimits().rules.isEmpty());
    }

    @Test
    void shouldLoadRateLimitsConfig_whenRuleIsPresent() throws Exception {
        Files.writeString(tempDir.resolve("ratelimits.json"),
                "{\"rules\":{\"observable\":{\"enabled\":true,\"maxExecutions\":10,\"windowSeconds\":3600}}}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must read ratelimits.json");
        RateLimitsConfig.Rule rule = mgr.getRateLimits().rules.get("observable");
        assertNotNull(rule);
        assertTrue(rule.enabled);
        assertEquals(10, rule.maxExecutions);
        assertEquals(3600, rule.windowSeconds);
    }

    // -------------------------------------------------------------------------
    // Case: unknown fields are ignored, for compatibility with a later version
    // -------------------------------------------------------------------------

    @Test
    void shouldIgnoreUnknownFields_whenGradesJsonHasExtraKeys() throws Exception {
        // A grades.json as a later version would write it, with fields added
        String futureJson = "{"
                + "\"futureField\": \"someValue\","
                + "\"grades\": {},"
                + "\"userGrades\": {}"
                + "}";
        Files.writeString(tempDir.resolve("grades.json"), futureJson);
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must ignore unknown fields, for forward compatibility");
        GradesConfig grades = mgr.getGrades();
        assertNotNull(grades.grades);
        assertTrue(grades.grades.isEmpty());
    }

    @Test
    void shouldIgnoreUnknownFields_whenAliasesJsonHasExtraKeys() throws Exception {
        String futureJson = "{\"futureFlag\": true, \"aliases\": {}}";
        Files.writeString(tempDir.resolve("aliases.json"), futureJson);
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must ignore unknown fields in aliases.json");
        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases);
        assertTrue(aliases.aliases.isEmpty());
    }

    @Test
    void shouldIgnoreUnknownFields_whenCommandsJsonHasExtraKeys() throws Exception {
        String futureJson = "{\"schemaVersion\": 2, \"grantedCommands\": [\"gamemode\"]}";
        Files.writeString(tempDir.resolve("commands.json"), futureJson);
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must ignore unknown fields in commands.json");
        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands);
        assertTrue(commands.grantedCommands.contains("gamemode"),
                "the known 'grantedCommands' field must still be read");
    }

    // -------------------------------------------------------------------------
    // Case: an upgrade, where the existing configs are read without an error
    // -------------------------------------------------------------------------

    @Test
    void shouldLoadAllThreeConfigs_whenAllFilesHavePartialData() throws Exception {
        // The config files of a server being upgraded to a newer version
        Files.writeString(tempDir.resolve("grades.json"),
                "{\"grades\":{\"vip\":{\"name\":\"vip\",\"permissions\":[\"customperm.command.gamemode\"]}},\"userGrades\":{}}");
        Files.writeString(tempDir.resolve("aliases.json"),
                "{\"aliases\":{\"fly\":[\"gamemode spectator\"]}}");
        Files.writeString(tempDir.resolve("commands.json"),
                "{\"grantedCommands\":[\"gamemode\",\"give\"]}");

        ConfigManager mgr = new ConfigManager(tempDir);
        boolean loaded = mgr.load();

        assertTrue(loaded, "load() must succeed on existing, partial config files");

        GradesConfig grades = mgr.getGrades();
        assertEquals(1, grades.grades.size(), "the vip grade must be read");
        assertTrue(grades.grades.get("vip").permissions.contains("customperm.command.gamemode"),
                "the vip grade permission must be deserialized");

        AliasesConfig aliases = mgr.getAliases();
        assertEquals(1, aliases.aliases.size(), "the fly alias must be read");
        assertEquals("gamemode spectator", aliases.aliases.get("fly").get(0),
                "the step of the fly alias must be deserialized");

        assertEquals(2, mgr.getCommands().grantedCommands.size(), "2 commandes exposées");
    }
}
