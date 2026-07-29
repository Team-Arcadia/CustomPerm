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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche data pour le CRUD des alias.
 * Zéro import Minecraft — tests JUnit 5 purs sur AliasesConfig.
 * Couvre AC1-AC4 de l'histoire 3-1.
 */
class AliasesConfigCrudTest {

    private AliasesConfig freshConfig() {
        return new AliasesConfig();
        // AliasesConfig initialise aliases = new LinkedHashMap<>() — prêt à l'emploi
    }

    /**
     * Reproduit la logique de parsing de CustomPermCommand.aliasAdd :
     *   Arrays.stream(raw.split(";")).map(String::trim).filter(s -> !s.isEmpty()).collect(...)
     */
    private List<String> parseSteps(String raw) {
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // T3.1 — AC1 : création d'un nouvel alias
    @Test
    void shouldCreateAlias_whenNameDoesNotExist() {
        AliasesConfig cfg = freshConfig();
        List<String> steps = List.of("effect give @s minecraft:instant_health 1 100", "say healed");

        cfg.aliases.put("heal", new ArrayList<>(steps));

        assertTrue(cfg.aliases.containsKey("heal"), "L'alias doit être présent dans la map");
        assertEquals(2, cfg.aliases.get("heal").size(), "L'alias doit avoir 2 étapes");
        assertEquals("effect give @s minecraft:instant_health 1 100", cfg.aliases.get("heal").get(0));
        assertEquals("say healed", cfg.aliases.get("heal").get(1));
    }

    // T3.2 — AC1 : écrasement d'un alias existant (comportement Map.put)
    @Test
    void shouldOverwriteAlias_whenNameAlreadyExists() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("greet", new ArrayList<>(List.of("say bonjour")));

        // Seconde invocation avec steps différentes — Map.put écrase
        List<String> newSteps = new ArrayList<>(List.of("say hello", "say world"));
        cfg.aliases.put("greet", newSteps);

        assertEquals(1, cfg.aliases.size(), "Il ne doit y avoir qu'une seule entrée pour 'greet'");
        assertEquals(2, cfg.aliases.get("greet").size(), "Les nouvelles steps doivent remplacer les anciennes");
        assertEquals("say hello", cfg.aliases.get("greet").get(0));
    }

    // T3.3 — AC3 : suppression d'un alias existant
    @Test
    void shouldRemoveAlias_whenExists() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("alert", new ArrayList<>(List.of("say ALERT")));

        List<String> removed = cfg.aliases.remove("alert");

        assertNotNull(removed, "aliases.remove() doit retourner la liste d'étapes pour un alias existant");
        assertFalse(cfg.aliases.containsKey("alert"), "L'alias supprimé ne doit plus être dans la map");
        assertTrue(cfg.aliases.isEmpty(), "La map doit être vide après suppression du seul alias");
    }

    // T3.4 — AC3 guard : suppression d'un alias inexistant retourne null
    @Test
    void shouldReturnNull_whenRemovingNonexistentAlias() {
        AliasesConfig cfg = freshConfig();

        List<String> removed = cfg.aliases.remove("inexistant");

        assertNull(removed, "aliases.remove() doit retourner null pour un alias inexistant");
        assertTrue(cfg.aliases.isEmpty(), "La map ne doit pas être altérée");
    }

    // T3.5 — AC4 : listage des alias avec nombre d'étapes (data-layer)
    @Test
    void shouldListAliasNames_withStepCounts() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("heal", new ArrayList<>(List.of("effect give @s minecraft:instant_health 1 100")));
        cfg.aliases.put("kit", new ArrayList<>(List.of("give @s diamond_sword 1", "give @s diamond_pickaxe 1", "give @s bread 64")));

        assertEquals(2, cfg.aliases.size());
        assertEquals(1, cfg.aliases.get("heal").size(), "heal doit avoir 1 étape");
        assertEquals(3, cfg.aliases.get("kit").size(), "kit doit avoir 3 étapes");
        assertTrue(cfg.aliases.containsKey("heal"));
        assertTrue(cfg.aliases.containsKey("kit"));
    }

    // T3.6 — AC1 : parsing des steps depuis une chaîne délimitée par ';'
    @Test
    void shouldParseMultipleSteps_fromSemicolonDelimited() {
        // Logique exacte de CustomPermCommand.aliasAdd
        String raw = "effect give @s minecraft:instant_health 1 100; say healed; playsound entity.player.levelup master @s";

        List<String> steps = parseSteps(raw);

        assertEquals(3, steps.size(), "3 steps doivent être parsées");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0));
        assertEquals("say healed", steps.get(1));
        assertEquals("playsound entity.player.levelup master @s", steps.get(2));
    }

    // T3.6b — parsing : les steps vides après split sont filtrées
    @Test
    void shouldIgnoreBlankSteps_inSemicolonDelimited() {
        // Cas : "cmd1;;cmd2" ou "  ; cmd2" — blancs et vides filtrés
        String raw = "say hello;  ; say world ; ";

        List<String> steps = parseSteps(raw);

        assertEquals(2, steps.size(), "Les steps vides/blanches doivent être filtrées");
        assertEquals("say hello", steps.get(0));
        assertEquals("say world", steps.get(1));
    }

    @Test
    void shouldReturnNoSteps_whenInputContainsOnlySeparators() {
        List<String> steps = parseSteps(";;;  ;");

        assertTrue(steps.isEmpty(), "Une chaîne composée uniquement de séparateurs ne doit produire aucune step");
    }

    // T3.7 — AC4 : LinkedHashMap préserve l'ordre d'insertion
    @Test
    void shouldPreserveInsertionOrder_withLinkedHashMap() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("alpha", new ArrayList<>(List.of("cmd_a")));
        cfg.aliases.put("beta",  new ArrayList<>(List.of("cmd_b")));
        cfg.aliases.put("gamma", new ArrayList<>(List.of("cmd_c")));

        List<String> keys = new ArrayList<>(cfg.aliases.keySet());

        assertEquals("alpha", keys.get(0), "Premier alias inséré doit être en tête");
        assertEquals("beta",  keys.get(1));
        assertEquals("gamma", keys.get(2), "Dernier alias inséré doit être en queue");
    }
}
