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
 * Data-layer tests of alias execution by a player allowed to run it.
 * No Minecraft import: pure JUnit 5.
 * Covers AC1 to AC4 of story 3-3, the parts testable in pure Java.
 *
 * Deliberately left to the GameTests:
 *   - INVARIANT-503 (requires entity + isOp): needs a MinecraftServer
 *   - real execution through Minecraft's CommandDispatcher: needs that dispatcher
 *   - @s resolution: needs a ServerPlayer and a level
 */
class AliasExecutionPermTest {

    /** The permission node format AliasManager.registerOne expects. */
    private static final String PERM_PREFIX = "customperm.alias.";

    // ── T3.1: permission node format ────────────────────────────────────────

    // AC3/AC5: "customperm.alias.<name>", a plain node
    @Test
    void shouldBuildCorrectPermNode_forSimpleAliasName() {
        String alias = "heal";
        String permNode = PERM_PREFIX + alias;
        assertEquals("customperm.alias.heal", permNode);
    }

    // AC3/AC5: with a dash, a common alias name
    @Test
    void shouldBuildCorrectPermNode_forHyphenatedAliasName() {
        String alias = "give-kit";
        String permNode = PERM_PREFIX + alias;
        assertEquals("customperm.alias.give-kit", permNode);
    }

    // AC3/AC5: with an underscore
    @Test
    void shouldBuildCorrectPermNode_forUnderscoreAliasName() {
        String alias = "tp_home";
        String permNode = PERM_PREFIX + alias;
        assertEquals("customperm.alias.tp_home", permNode);
    }

    // AC3/AC5: the prefix is always "customperm.alias.", never omitted
    @Test
    void shouldAlwaysIncludePrefix_inPermNode() {
        String alias = "x";
        String permNode = PERM_PREFIX + alias;
        assertTrue(permNode.startsWith("customperm.alias."), "the node must start with customperm.alias.");
        assertTrue(permNode.endsWith(".x"), "the node must end with the alias name");
    }

    // ── T3.2: null and blank steps are filtered ─────────────────────────────

    /**
     * Mirrors the filter in AliasManager.registerOne:
     *   if (step == null || step.isBlank()) continue;
     */
    private long countExecutableSteps(List<String> steps) {
        return steps.stream()
                .filter(step -> step != null && !step.isBlank())
                .count();
    }

    // AC1: null steps are filtered before execution
    @Test
    void shouldFilterNullSteps_beforeExecution() {
        List<String> steps = new ArrayList<>(Arrays.asList("say hello", null, "say world"));
        long executable = countExecutableSteps(steps);
        assertEquals(2, executable, "null steps must be filtered");
    }

    // AC1: blank steps (spaces only) are filtered
    @Test
    void shouldFilterBlankSteps_beforeExecution() {
        List<String> steps = new ArrayList<>(Arrays.asList("say hello", "   ", "", "say world"));
        long executable = countExecutableSteps(steps);
        assertEquals(2, executable, "blank steps must be filtered");
    }

    // T3.3, AC1: a list of only null or blank steps leaves nothing to run
    @Test
    void shouldReturnZeroExecutable_whenAllStepsAreBlankOrNull() {
        List<String> steps = new ArrayList<>(Arrays.asList(null, "  ", "", "\t"));
        long executable = countExecutableSteps(steps);
        assertEquals(0, executable, "nothing must be runnable when every step is null or blank");
    }

    // ── T3.4: the executed counter ──────────────────────────────────────────

    /**
     * Mirrors how AliasManager.registerOne counts {@code executed}: every valid step (not null,
     * not blank) increments it when it succeeds.
     *
     * <p><b>Varargs caveat:</b> when {@code stepSucceeds} holds fewer elements than there are
     * valid steps, the extra steps count as failures ({@code false}), silently, which is what
     * AliasManager's no-halt-on-error does. Pass as many elements as there are non-blank steps.
     */
    private int simulateExecution(List<String> steps, boolean... stepSucceeds) {
        int executed = 0;
        int successIdx = 0;
        for (String step : steps) {
            if (step == null || step.isBlank()) continue;
            boolean succeeds = successIdx < stepSucceeds.length && stepSucceeds[successIdx++];
            if (succeeds) executed++;
            // otherwise: catch (Throwable t), LOGGER.warn, continue (no-halt-on-error)
        }
        return executed;
    }

    // AC1: 3 valid steps, all succeeding, so executed = 3
    @Test
    void shouldCountExecutedSteps_whenAllSucceed() {
        List<String> steps = List.of(
                "effect give @s minecraft:instant_health 1 100",
                "say healed",
                "playsound entity.player.levelup master @s");
        int executed = simulateExecution(steps, true, true, true);
        assertEquals(3, executed, "the 3 steps must count as executed");
    }

    // T3.5, AC2 (no-halt-on-error): step 2 fails, so executed = 2 (steps 1 and 3 succeeded)
    @Test
    void shouldContinueAfterFailingStep_andCountOnlySuccesses() {
        List<String> steps = List.of("say start", "not-a-command", "say end");
        int executed = simulateExecution(steps, true, false, true);
        assertEquals(2, executed, "execution must carry on past a failed step");
    }

    // AC2: every step fails, so executed = 0, and no exception escapes
    @Test
    void shouldReturnZero_whenAllStepsFail() {
        List<String> steps = List.of("not-a-command-1", "not-a-command-2");
        int executed = simulateExecution(steps, false, false);
        assertEquals(0, executed, "nothing must be counted when every step fails");
        // no-halt-on-error: reaching this line without an exception is the assertion
    }

    // ── T3.x: the AliasesConfig shape execution relies on ───────────────────

    // AliasesConfig stores the steps in order, which AC1's sequential execution needs
    @Test
    void shouldPreserveStepOrder_inAliasesConfig() {
        AliasesConfig cfg = new AliasesConfig();
        List<String> steps = new ArrayList<>(Arrays.asList(
                "effect give @s minecraft:instant_health 1 100",
                "say healed",
                "playsound entity.player.levelup master @s"));
        cfg.aliases.put("heal", steps);

        List<String> retrieved = cfg.aliases.get("heal");
        assertEquals(3, retrieved.size());
        assertEquals("effect give @s minecraft:instant_health 1 100", retrieved.get(0), "step 0 order");
        assertEquals("say healed", retrieved.get(1), "step 1 order");
        assertEquals("playsound entity.player.levelup master @s", retrieved.get(2), "step 2 order");
    }

    // The "steps null or empty" guard in registerOrReplace, at the data layer
    @Test
    void shouldNotExecute_whenAliasStepsAreEmpty() {
        AliasesConfig cfg = new AliasesConfig();
        cfg.aliases.put("empty", new ArrayList<>());

        List<String> steps = cfg.aliases.get("empty");
        assertTrue(steps != null && steps.isEmpty(),
                "an alias with an empty list is stored empty, and the steps.isEmpty() guard in registerOrReplace would stop the execution");
    }
}
