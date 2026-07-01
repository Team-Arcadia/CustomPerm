package com.arcadia.customperm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rétrocompatibilité des fichiers de configuration (AC4 — NFR9, NFR10).
 *
 * <p>Vérifie que :</p>
 * <ul>
 *   <li>un JSON vide {@code {}} est accepté pour chacun des fichiers</li>
 *   <li>des fichiers absents donnent des configs par défaut (collections vides, non null)</li>
 *   <li>des champs JSON inconnus (ajoutés dans une future version) sont ignorés sans erreur</li>
 * </ul>
 *
 * <p>Tous les tests sont purs Java — zéro import {@code net.minecraft.*} ou
 * {@code net.neoforged.*} (AR8 / INVARIANT-504).</p>
 */
class ConfigBackwardCompatTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Scénario : fichiers absents → défauts vides
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnEmptyCollections_whenConfigFilesAreMissing() throws Exception {
        // Arrange — aucun fichier JSON dans tempDir
        ConfigManager mgr = new ConfigManager(tempDir);

        // Act
        boolean loaded = mgr.load();

        // Assert
        assertTrue(loaded, "load() doit retourner true même si les fichiers n'existent pas");

        GradesConfig grades = mgr.getGrades();
        assertNotNull(grades.grades, "grades.grades ne doit pas être null");
        assertTrue(grades.grades.isEmpty(), "grades.grades doit être vide");
        assertNotNull(grades.userGrades, "grades.userGrades ne doit pas être null");
        assertTrue(grades.userGrades.isEmpty(), "grades.userGrades doit être vide");

        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases, "aliases.aliases ne doit pas être null");
        assertTrue(aliases.aliases.isEmpty(), "aliases.aliases doit être vide");

        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands, "commands.grantedCommands ne doit pas être null");
        assertTrue(commands.grantedCommands.isEmpty(), "commands.grantedCommands doit être vide");

        SettingsConfig settings = mgr.getSettings();
        assertEquals("deny", settings.luckPermsFallbackMode,
                "luckPermsFallbackMode doit être deny par défaut");

        RateLimitsConfig rateLimits = mgr.getRateLimits();
        assertNotNull(rateLimits.rules, "rateLimits.rules ne doit pas être null");
        assertTrue(rateLimits.rules.isEmpty(), "rateLimits.rules doit être vide");
    }

    // -------------------------------------------------------------------------
    // Scénario : JSON vide {} → rétrocompatibilité minimale
    // -------------------------------------------------------------------------

    @Test
    void shouldLoadGradesConfig_whenGradesJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("grades.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit retourner true pour grades.json = {}");
        GradesConfig grades = mgr.getGrades();
        assertNotNull(grades.grades, "grades.grades ne doit pas être null après {}");
        assertTrue(grades.grades.isEmpty());
        assertNotNull(grades.userGrades, "grades.userGrades ne doit pas être null après {}");
        assertTrue(grades.userGrades.isEmpty());
    }

    @Test
    void shouldLoadAliasesConfig_whenAliasesJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("aliases.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit retourner true pour aliases.json = {}");
        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases, "aliases.aliases ne doit pas être null après {}");
        assertTrue(aliases.aliases.isEmpty());
    }

    @Test
    void shouldLoadAliasesConfig_whenAliasesFieldIsExplicitNull() throws Exception {
        Files.writeString(tempDir.resolve("aliases.json"), "{\"aliases\":null}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit normaliser aliases:null sans rejeter la config");
        AliasesConfig aliases = mgr.getAliases();
        assertNotNull(aliases.aliases, "aliases.aliases ne doit pas rester null après normalisation");
        assertTrue(aliases.aliases.isEmpty());
    }

    @Test
    void shouldLoadCommandsConfig_whenCommandsJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("commands.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit retourner true pour commands.json = {}");
        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands, "commands.grantedCommands ne doit pas être null après {}");
        assertTrue(commands.grantedCommands.isEmpty());
    }

    @Test
    void shouldLoadCommandsConfig_whenGrantedCommandsFieldIsExplicitNull() throws Exception {
        Files.writeString(tempDir.resolve("commands.json"), "{\"grantedCommands\":null}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit normaliser grantedCommands:null sans rejeter la config");
        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands, "commands.grantedCommands ne doit pas rester null après normalisation");
        assertTrue(commands.grantedCommands.isEmpty());
    }

    @Test
    void shouldLoadCommandsConfig_whenPreserveOriginalRequiresIsPresent() throws Exception {
        Files.writeString(tempDir.resolve("commands.json"),
                "{\"grantedCommands\":[\"gamemode\",\"adminpanel\"],\"preserveOriginalRequires\":{\"gamemode\":false,\"adminpanel\":true}}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit lire preserveOriginalRequires");
        CommandsConfig commands = mgr.getCommands();
        assertFalse(commands.shouldPreserveOriginalRequires("gamemode"));
        assertTrue(commands.shouldPreserveOriginalRequires("adminpanel"));
    }

    @Test
    void shouldLoadSettingsConfig_whenSettingsJsonIsPresent() throws Exception {
        Files.writeString(tempDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"internal\"}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit lire settings.json");
        assertEquals("internal", mgr.getSettings().luckPermsFallbackMode);
    }

    @Test
    void shouldNormalizeSettingsConfigToDeny_whenFallbackModeIsInvalid() throws Exception {
        Files.writeString(tempDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"unsafe\"}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit normaliser un mode invalide sans rejeter la config");
        assertEquals("deny", mgr.getSettings().luckPermsFallbackMode);
    }

    @Test
    void shouldLoadRateLimitsConfig_whenRateLimitsJsonIsEmptyObject() throws Exception {
        Files.writeString(tempDir.resolve("ratelimits.json"), "{}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit retourner true pour ratelimits.json = {}");
        RateLimitsConfig rateLimits = mgr.getRateLimits();
        assertNotNull(rateLimits.rules, "rateLimits.rules ne doit pas être null après {}");
        assertTrue(rateLimits.rules.isEmpty());
    }

    @Test
    void shouldLoadRateLimitsConfig_whenRulesFieldIsExplicitNull() throws Exception {
        Files.writeString(tempDir.resolve("ratelimits.json"), "{\"rules\":null}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit normaliser rules:null sans rejeter la config");
        assertNotNull(mgr.getRateLimits().rules);
        assertTrue(mgr.getRateLimits().rules.isEmpty());
    }

    @Test
    void shouldLoadRateLimitsConfig_whenRuleIsPresent() throws Exception {
        Files.writeString(tempDir.resolve("ratelimits.json"),
                "{\"rules\":{\"observable\":{\"enabled\":true,\"maxExecutions\":10,\"windowSeconds\":3600}}}");
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit lire ratelimits.json");
        RateLimitsConfig.Rule rule = mgr.getRateLimits().rules.get("observable");
        assertNotNull(rule);
        assertTrue(rule.enabled);
        assertEquals(10, rule.maxExecutions);
        assertEquals(3600, rule.windowSeconds);
    }

    // -------------------------------------------------------------------------
    // Scénario : champs inconnus → ignorés (compat future version)
    // -------------------------------------------------------------------------

    @Test
    void shouldIgnoreUnknownFields_whenGradesJsonHasExtraKeys() throws Exception {
        // Simule un grades.json produit par une version future qui aurait ajouté des champs
        String futureJson = "{"
                + "\"futureField\": \"someValue\","
                + "\"grades\": {},"
                + "\"userGrades\": {}"
                + "}";
        Files.writeString(tempDir.resolve("grades.json"), futureJson);
        ConfigManager mgr = new ConfigManager(tempDir);

        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit ignorer les champs inconnus (forward compat)");
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

        assertTrue(loaded, "load() doit ignorer les champs inconnus dans aliases.json");
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

        assertTrue(loaded, "load() doit ignorer les champs inconnus dans commands.json");
        CommandsConfig commands = mgr.getCommands();
        assertNotNull(commands.grantedCommands);
        assertTrue(commands.grantedCommands.contains("gamemode"),
                "Le champ connu 'grantedCommands' doit être lu correctement");
    }

    // -------------------------------------------------------------------------
    // Scénario : upgrade simulé — configs existantes lues sans erreur
    // -------------------------------------------------------------------------

    @Test
    void shouldLoadAllThreeConfigs_whenAllFilesHavePartialData() throws Exception {
        // Simule des fichiers de config existants sur un serveur upgrading vers nouvelle version
        Files.writeString(tempDir.resolve("grades.json"),
                "{\"grades\":{\"vip\":{\"name\":\"vip\",\"permissions\":[\"customperm.command.gamemode\"]}},\"userGrades\":{}}");
        Files.writeString(tempDir.resolve("aliases.json"),
                "{\"aliases\":{\"fly\":[\"gamemode spectator\"]}}");
        Files.writeString(tempDir.resolve("commands.json"),
                "{\"grantedCommands\":[\"gamemode\",\"give\"]}");

        ConfigManager mgr = new ConfigManager(tempDir);
        boolean loaded = mgr.load();

        assertTrue(loaded, "load() doit réussir avec des fichiers de config partiels existants");

        GradesConfig grades = mgr.getGrades();
        assertEquals(1, grades.grades.size(), "vip grade doit être lu");
        assertTrue(grades.grades.get("vip").permissions.contains("customperm.command.gamemode"),
                "La permission du grade vip doit être désérialisée correctement");

        AliasesConfig aliases = mgr.getAliases();
        assertEquals(1, aliases.aliases.size(), "alias fly doit être lu");
        assertEquals("gamemode spectator", aliases.aliases.get("fly").get(0),
                "Le step de l'alias fly doit être désérialisé correctement");

        assertEquals(2, mgr.getCommands().grantedCommands.size(), "2 commandes exposées");
    }
}
