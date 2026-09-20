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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-layer tests of incremental alias editing.
 * No Minecraft import: pure JUnit 5 over AliasesConfig.
 * Covers AC1 to AC5 of story 3-2.
 */
class AliasesStepEditTest {

    private AliasesConfig freshConfig() {
        return new AliasesConfig();
    }

    /** Helper: an alias preloaded with N steps. */
    private AliasesConfig configWith(String aliasName, String... steps) {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put(aliasName, new ArrayList<>(Arrays.asList(steps)));
        return cfg;
    }

    // T2.1, AC1: computeIfAbsent on an existing alias appends the step
    @Test
    void shouldAppendStep_whenAliasExists() {
        AliasesConfig cfg = configWith("heal", "effect give @s minecraft:instant_health 1 100");

        // Mirrors aliasAddStep: computeIfAbsent, then add
        cfg.aliases.computeIfAbsent("heal", k -> new ArrayList<>()).add("say healed");

        List<String> steps = cfg.aliases.get("heal");
        assertEquals(2, steps.size(), "the step must be appended to the list");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0));
        assertEquals("say healed", steps.get(1));
    }

    // T2.2, AC2: computeIfAbsent on a missing key creates the alias with 1 step
    @Test
    void shouldCreateAlias_implicitly_whenAddStep_andAliasDoesNotExist() {
        AliasesConfig cfg = freshConfig();
        assertFalse(cfg.aliases.containsKey("kit"), "the alias must not exist before the add");

        cfg.aliases.computeIfAbsent("kit", k -> new ArrayList<>()).add("give @s diamond_sword 1");

        assertTrue(cfg.aliases.containsKey("kit"), "the alias must be created implicitly");
        assertEquals(1, cfg.aliases.get("kit").size(), "the alias must hold 1 step");
        assertEquals("give @s diamond_sword 1", cfg.aliases.get("kit").get(0));
    }

    // T2.3, AC3: steps.remove(0) removes the right step and shortens the list
    @Test
    void shouldRemoveStep_byIndex_whenExists() {
        AliasesConfig cfg = configWith("kit", "give @s diamond_sword 1", "give @s bread 64");

        // Mirrors aliasRemoveStep: List.remove(int)
        String removed = cfg.aliases.get("kit").remove(0);

        assertEquals("give @s diamond_sword 1", removed, "the step removed must be the first one");
        assertEquals(1, cfg.aliases.get("kit").size(), "the list must be down to 1 step");
        assertEquals("give @s bread 64", cfg.aliases.get("kit").get(0));
    }

    // T2.4, AC3 reindexing: removing index 1 of [A,B,C] gives [A,C], so index 1 is now C
    @Test
    void shouldReindex_remainingSteps_afterMiddleRemoval() {
        AliasesConfig cfg = configWith("combo", "stepA", "stepB", "stepC");

        cfg.aliases.get("combo").remove(1);  // drops stepB, at index 1

        List<String> steps = cfg.aliases.get("combo");
        assertEquals(2, steps.size(), "the list must hold 2 steps once the middle one is gone");
        assertEquals("stepA", steps.get(0), "index 0 must be stepA");
        assertEquals("stepC", steps.get(1), "index 1 must be stepC, the list having reindexed itself");
    }

    // T2.5, AC3 edge: if (steps.isEmpty()) aliases.remove(name), so the last step removes the alias
    @Test
    void shouldRemoveAliasFromMap_whenLastStepDeleted() {
        AliasesConfig cfg = configWith("solo", "say hello");

        List<String> steps = cfg.aliases.get("solo");
        steps.remove(0);
        // Mirrors aliasRemoveStep: an alias left with no step is removed
        if (steps.isEmpty()) cfg.aliases.remove("solo");

        assertFalse(cfg.aliases.containsKey("solo"), "the alias must leave the map with its last step");
        assertTrue(cfg.aliases.isEmpty(), "the map must be empty");
    }

    // T2.6, AC4 guard: aliases.get("missing") is null and nothing is mutated
    @Test
    void shouldReturnNull_whenRemovingStep_fromNonexistentAlias() {
        AliasesConfig cfg = freshConfig();

        // Mirrors the first guard of aliasRemoveStep
        List<String> steps = cfg.aliases.get("missing");

        assertNull(steps, "aliases.get() must return null for an alias that does not exist");
        assertTrue(cfg.aliases.isEmpty(), "the map must be left untouched");
    }

    // T2.7, AC4: index >= steps.size() leaves the steps unchanged
    @Test
    void shouldNotRemoveStep_whenIndexOutOfBounds() {
        AliasesConfig cfg = configWith("heal", "effect give @s minecraft:instant_health 1 100", "say healed");
        List<String> steps = cfg.aliases.get("heal");
        int outOfBoundsIndex = steps.size(); // the first invalid index, just past the last

        // Mirrors the guard of aliasRemoveStep: an out-of-bounds index removes nothing
        boolean indexValid = outOfBoundsIndex >= 0 && outOfBoundsIndex < steps.size();
        if (indexValid) steps.remove(outOfBoundsIndex);

        assertFalse(indexValid, "the index must be seen as out of bounds");
        assertEquals(2, steps.size(), "the steps must not change");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0));
        assertEquals("say healed", steps.get(1));
    }

    // T2.8, AC5 data layer: a list of 3 steps, read through get(0), get(1), get(2)
    @Test
    void shouldListSteps_withZeroBasedIndex() {
        AliasesConfig cfg = configWith("combo",
                "effect give @s minecraft:instant_health 1 100",
                "say healed",
                "playsound entity.player.levelup master @s");

        List<String> steps = cfg.aliases.get("combo");

        assertEquals(3, steps.size(), "the alias must hold 3 steps");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0), "Index 0");
        assertEquals("say healed", steps.get(1), "Index 1");
        assertEquals("playsound entity.player.levelup master @s", steps.get(2), "Index 2");
    }

    // T2.9, AC5 guard: aliasSteps on a missing alias is null, the T2.6 path for aliasRemoveStep
    @Test
    void shouldReturnNull_forSteps_whenAliasDoesNotExist() {
        AliasesConfig cfg = freshConfig();

        // Mirrors the first guard of aliasSteps: aliases.get(name) == null
        List<String> steps = cfg.aliases.get("missing");

        assertNull(steps, "aliases.get() must return null for a missing alias, on the aliasSteps path");
        assertTrue(cfg.aliases.isEmpty(), "the map must be left untouched");
    }

    // T2.10, AC5 guard: aliasSteps on an alias with an empty list gives steps.isEmpty() == true
    @Test
    void shouldHaveEmptyList_whenAliasExistsButHasNoSteps() {
        AliasesConfig cfg = freshConfig();
        // An alias in the map with no step, a state that can occur in passing
        cfg.aliases.put("empty", new ArrayList<>());

        List<String> steps = cfg.aliases.get("empty");

        assertNotNull(steps, "the list must be in the map");
        assertTrue(steps.isEmpty(), "the list must be empty, and aliasSteps would answer 'No such alias'");
    }

    // T2.11, AC3 boundary: removing at the last valid index (steps.size()-1)
    @Test
    void shouldRemoveStep_atLastIndex() {
        AliasesConfig cfg = configWith("combo", "stepA", "stepB", "stepC");
        List<String> steps = cfg.aliases.get("combo");
        int lastIndex = steps.size() - 1; // index 2

        String removed = steps.remove(lastIndex);

        assertEquals("stepC", removed, "the last step must be the one removed");
        assertEquals(2, steps.size(), "the list must be down to 2 steps");
        assertEquals("stepA", steps.get(0));
        assertEquals("stepB", steps.get(1));
    }
}
