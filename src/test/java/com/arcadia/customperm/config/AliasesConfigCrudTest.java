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
 * Data-layer tests of alias create, read, update and delete.
 * No Minecraft import: pure JUnit 5 over AliasesConfig.
 * Couvre AC1-AC4 de l'histoire 3-1.
 */
class AliasesConfigCrudTest {

    private AliasesConfig freshConfig() {
        return new AliasesConfig();
        // AliasesConfig initialise aliases = new LinkedHashMap<>() — prêt à l'emploi
    }

    /**
     * Mirrors the parsing in CustomPermCommand.aliasAdd:
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

        assertTrue(cfg.aliases.containsKey("heal"), "the alias must be in the map");
        assertEquals(2, cfg.aliases.get("heal").size(), "the alias must hold 2 steps");
        assertEquals("effect give @s minecraft:instant_health 1 100", cfg.aliases.get("heal").get(0));
        assertEquals("say healed", cfg.aliases.get("heal").get(1));
    }

    // T3.2 — AC1 : écrasement d'un alias existant (comportement Map.put)
    @Test
    void shouldOverwriteAlias_whenNameAlreadyExists() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("greet", new ArrayList<>(List.of("say bonjour")));

        // A second call with different steps: Map.put overwrites
        List<String> newSteps = new ArrayList<>(List.of("say hello", "say world"));
        cfg.aliases.put("greet", newSteps);

        assertEquals(1, cfg.aliases.size(), "there must be a single entry for 'greet'");
        assertEquals(2, cfg.aliases.get("greet").size(), "the new steps must replace the old ones");
        assertEquals("say hello", cfg.aliases.get("greet").get(0));
    }

    // T3.3 — AC3 : suppression d'un alias existant
    @Test
    void shouldRemoveAlias_whenExists() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("alert", new ArrayList<>(List.of("say ALERT")));

        List<String> removed = cfg.aliases.remove("alert");

        assertNotNull(removed, "aliases.remove() must return the step list of an alias that exists");
        assertFalse(cfg.aliases.containsKey("alert"), "the deleted alias must be out of the map");
        assertTrue(cfg.aliases.isEmpty(), "the map must be empty once the only alias is deleted");
    }

    // T3.4, AC3 guard: deleting an alias that does not exist returns null
    @Test
    void shouldReturnNull_whenRemovingNonexistentAlias() {
        AliasesConfig cfg = freshConfig();

        List<String> removed = cfg.aliases.remove("missing");

        assertNull(removed, "aliases.remove() must return null for an alias that does not exist");
        assertTrue(cfg.aliases.isEmpty(), "the map must be left untouched");
    }

    // T3.5, AC4: listing the aliases with their step counts, at the data layer
    @Test
    void shouldListAliasNames_withStepCounts() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("heal", new ArrayList<>(List.of("effect give @s minecraft:instant_health 1 100")));
        cfg.aliases.put("kit", new ArrayList<>(List.of("give @s diamond_sword 1", "give @s diamond_pickaxe 1", "give @s bread 64")));

        assertEquals(2, cfg.aliases.size());
        assertEquals(1, cfg.aliases.get("heal").size(), "heal must hold 1 step");
        assertEquals(3, cfg.aliases.get("kit").size(), "kit must hold 3 steps");
        assertTrue(cfg.aliases.containsKey("heal"));
        assertTrue(cfg.aliases.containsKey("kit"));
    }

    // T3.6, AC1: parsing the steps out of a ';' separated string
    @Test
    void shouldParseMultipleSteps_fromSemicolonDelimited() {
        // Logique exacte de CustomPermCommand.aliasAdd
        String raw = "effect give @s minecraft:instant_health 1 100; say healed; playsound entity.player.levelup master @s";

        List<String> steps = parseSteps(raw);

        assertEquals(3, steps.size(), "3 steps must be parsed");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0));
        assertEquals("say healed", steps.get(1));
        assertEquals("playsound entity.player.levelup master @s", steps.get(2));
    }

    // T3.6b, parsing: the empty steps left by the split are filtered
    @Test
    void shouldIgnoreBlankSteps_inSemicolonDelimited() {
        // Cases "cmd1;;cmd2" and "  ; cmd2": blank and empty steps are filtered
        String raw = "say hello;  ; say world ; ";

        List<String> steps = parseSteps(raw);

        assertEquals(2, steps.size(), "empty and blank steps must be filtered");
        assertEquals("say hello", steps.get(0));
        assertEquals("say world", steps.get(1));
    }

    @Test
    void shouldReturnNoSteps_whenInputContainsOnlySeparators() {
        List<String> steps = parseSteps(";;;  ;");

        assertTrue(steps.isEmpty(), "a string of separators alone must produce no step");
    }

    // T3.7, AC4: a LinkedHashMap keeps the insertion order
    @Test
    void shouldPreserveInsertionOrder_withLinkedHashMap() {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put("alpha", new ArrayList<>(List.of("cmd_a")));
        cfg.aliases.put("beta",  new ArrayList<>(List.of("cmd_b")));
        cfg.aliases.put("gamma", new ArrayList<>(List.of("cmd_c")));

        List<String> keys = new ArrayList<>(cfg.aliases.keySet());

        assertEquals("alpha", keys.get(0), "the first alias inserted must come first");
        assertEquals("beta",  keys.get(1));
        assertEquals("gamma", keys.get(2), "the last alias inserted must come last");
    }
}
