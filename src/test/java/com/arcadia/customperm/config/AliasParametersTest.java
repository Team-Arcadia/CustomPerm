/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import com.arcadia.customperm.config.AliasesConfig.Parameter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules an alias argument follows: names, declaration order, the values a player may type, and
 * the substitution a step goes through. Pure Java, no Minecraft class, so the Brigadier wiring is
 * what the GameTests cover instead.
 */
class AliasParametersTest {

    private static Parameter param(String name, String type) {
        Parameter parameter = new Parameter(name, type);
        parameter.normalize();
        return parameter;
    }

    // ── names ───────────────────────────────────────────────────────────────

    @Test
    void shouldAcceptALowercaseName() {
        assertNull(AliasParameters.nameProblem("player"));
        assertNull(AliasParameters.nameProblem("target_2"));
    }

    @Test
    void shouldRefuseANameThatCannotBeTyped() {
        assertNotNull(AliasParameters.nameProblem(null));
        assertNotNull(AliasParameters.nameProblem(""));
        assertNotNull(AliasParameters.nameProblem("2fast"), "a name must start with a letter");
        assertNotNull(AliasParameters.nameProblem("Player"), "names are lowercase");
        assertNotNull(AliasParameters.nameProblem("has space"));
        assertNotNull(AliasParameters.nameProblem("a".repeat(17)));
    }

    @Test
    void shouldRefuseAnUnknownType() {
        assertNull(AliasParameters.typeProblem(AliasesConfig.TYPE_PLAYER));
        assertNull(AliasParameters.typeProblem(AliasesConfig.TYPE_TEXT));
        assertNotNull(AliasParameters.typeProblem("entity"));
        assertNotNull(AliasParameters.typeProblem(null));
    }

    // ── declaration order ───────────────────────────────────────────────────

    @Test
    void shouldRefuseASecondArgumentOfTheSameName() {
        List<Parameter> declared = List.of(param("target", AliasesConfig.TYPE_PLAYER));
        assertNotNull(AliasParameters.appendProblem(declared, param("target", AliasesConfig.TYPE_WORD)));
    }

    @Test
    void shouldRefuseAnythingAfterAGreedyArgument() {
        List<Parameter> declared = List.of(param("message", AliasesConfig.TYPE_TEXT));
        String problem = AliasParameters.appendProblem(declared, param("count", AliasesConfig.TYPE_INTEGER));
        assertNotNull(problem, "text reads the rest of the line, so nothing can follow it");
        assertTrue(problem.contains("message"), "the message names the argument in the way: " + problem);
    }

    @Test
    void shouldRefuseARequiredArgumentAfterAnOptionalOne() {
        Parameter first = param("count", AliasesConfig.TYPE_INTEGER);
        first.optional = true;
        assertNotNull(AliasParameters.appendProblem(List.of(first), param("target", AliasesConfig.TYPE_PLAYER)));
    }

    @Test
    void shouldAcceptAnOptionalArgumentAfterAnOptionalOne() {
        Parameter first = param("count", AliasesConfig.TYPE_INTEGER);
        first.optional = true;
        Parameter second = param("reason", AliasesConfig.TYPE_WORD);
        second.optional = true;
        assertNull(AliasParameters.appendProblem(List.of(first), second));
    }

    @Test
    void shouldRefuseAnOrderThatPutsGreedyInTheMiddle() {
        List<Parameter> parameters = List.of(
                param("message", AliasesConfig.TYPE_TEXT),
                param("count", AliasesConfig.TYPE_INTEGER));
        assertNotNull(AliasParameters.orderProblem(parameters));
    }

    @Test
    void shouldAcceptAnOrderThatEndsWithGreedy() {
        List<Parameter> parameters = List.of(
                param("target", AliasesConfig.TYPE_PLAYER),
                param("message", AliasesConfig.TYPE_TEXT));
        assertNull(AliasParameters.orderProblem(parameters));
    }

    // ── values ──────────────────────────────────────────────────────────────

    @Test
    void shouldRefuseAValueOutsideTheDeclaredChoices() {
        Parameter parameter = param("mode", AliasesConfig.TYPE_WORD);
        parameter.choices = new ArrayList<>(List.of("creative", "survival"));
        assertNull(AliasParameters.valueProblem(parameter, "creative"));
        assertNotNull(AliasParameters.valueProblem(parameter, "spectator"));
    }

    @Test
    void shouldRefuseASelectorUnlessTheArgumentAllowsOne() {
        Parameter parameter = param("message", AliasesConfig.TYPE_TEXT);
        assertNotNull(AliasParameters.valueProblem(parameter, "@a"));
        parameter.allowSelectors = true;
        assertNull(AliasParameters.valueProblem(parameter, "@a"));
    }

    @Test
    void shouldKeepTheSelectorGuardOnAWordThatCannotHoldOne() {
        // Brigadier's word reader refuses an at sign outright, so the guard is never reached; it
        // stays in place all the same, the check being on the value rather than on the type.
        Parameter parameter = param("target", AliasesConfig.TYPE_WORD);
        parameter.allowSelectors = true;
        parameter.normalize();
        assertFalse(parameter.allowSelectors, "only a text argument carries the switch");
        assertNotNull(AliasParameters.valueProblem(parameter, "@a"));
    }

    @Test
    void shouldReadOrdinaryTextThatHoldsAnAtSign() {
        Parameter parameter = param("message", AliasesConfig.TYPE_TEXT);
        assertNull(AliasParameters.valueProblem(parameter, "write to me@example"),
                "an at sign inside a word is not a selector");
        assertNotNull(AliasParameters.valueProblem(parameter, "hello @e"),
                "an at sign starting a word is");
    }

    @Test
    void shouldRefuseALineBreakInAValue() {
        assertNotNull(AliasParameters.valueProblem(param("message", AliasesConfig.TYPE_TEXT), "one\ntwo"));
    }

    // ── substitution ────────────────────────────────────────────────────────

    @Test
    void shouldSubstituteEveryDeclaredArgument() {
        assertEquals("tp Steve 0 64 0",
                AliasParameters.substitute("tp ${target} 0 ${height} 0",
                        Map.of("target", "Steve", "height", "64")));
    }

    @Test
    void shouldLeaveNbtBracesAlone() {
        String step = "give @s diamond_sword{Enchantments:[{id:sharpness,lvl:5}]} ${count}";
        assertEquals("give @s diamond_sword{Enchantments:[{id:sharpness,lvl:5}]} 3",
                AliasParameters.substitute(step, Map.of("count", "3")));
    }

    @Test
    void shouldLeaveAnUndeclaredPlaceholderAsTyped() {
        assertEquals("say ${nobody} and Steve",
                AliasParameters.substitute("say ${nobody} and ${target}", Map.of("target", "Steve")));
    }

    @Test
    void shouldLeaveAnUnclosedPlaceholderAsTyped() {
        assertEquals("say ${target", AliasParameters.substitute("say ${target", Map.of("target", "Steve")));
    }

    @Test
    void shouldSubstituteTheSameArgumentTwice() {
        assertEquals("say Steve, Steve",
                AliasParameters.substitute("say ${who}, ${who}", Map.of("who", "Steve")));
    }

    @Test
    void shouldReturnTheStepUntouchedWhenThereIsNothingToSubstitute() {
        assertEquals("say hello", AliasParameters.substitute("say hello", Map.of("who", "Steve")));
        assertEquals("say ${who}", AliasParameters.substitute("say ${who}", Map.of()));
    }

    @Test
    void shouldListThePlaceholdersAStepUses() {
        assertEquals(List.of("target", "count"),
                List.copyOf(AliasParameters.placeholders("give ${target} diamond ${count}")));
        assertTrue(AliasParameters.placeholders("give @s diamond{Count:1b}").isEmpty());
    }

    // ── shape of the declaration ────────────────────────────────────────────

    @Test
    void shouldShowUsageAsTabCompletionDoes() {
        Parameter target = param("target", AliasesConfig.TYPE_PLAYER);
        Parameter count = param("count", AliasesConfig.TYPE_INTEGER);
        count.optional = true;
        assertEquals("<target> [count]", AliasParameters.usage(List.of(target, count)));
    }

    @Test
    void shouldNormalizeAwayWhatCannotHold() {
        AliasesConfig config = new AliasesConfig();
        config.aliases.put("heal", new ArrayList<>(List.of("effect give ${target} regeneration")));
        Parameter greedy = new Parameter("message", AliasesConfig.TYPE_TEXT);
        Parameter after = new Parameter("count", AliasesConfig.TYPE_INTEGER);
        Parameter duplicate = new Parameter("message", AliasesConfig.TYPE_WORD);
        config.aliasParameters.put("heal", new ArrayList<>(List.of(greedy, after, duplicate)));
        config.aliasParameters.put("gone", new ArrayList<>(List.of(new Parameter("x", AliasesConfig.TYPE_WORD))));

        config.normalize();

        assertEquals(List.of("message"), config.parameters("heal").stream().map(p -> p.name).toList(),
                "nothing survives after a greedy argument, and a duplicate name is dropped");
        assertTrue(config.parameters("gone").isEmpty(), "arguments of an alias that does not exist are dropped");
    }

    @Test
    void shouldMakeEveryArgumentAfterAnOptionalOneOptional() {
        AliasesConfig config = new AliasesConfig();
        config.aliases.put("kit", new ArrayList<>(List.of("give ${who} bread ${count}")));
        Parameter who = new Parameter("who", AliasesConfig.TYPE_PLAYER);
        who.optional = true;
        Parameter count = new Parameter("count", AliasesConfig.TYPE_INTEGER);
        config.aliasParameters.put("kit", new ArrayList<>(List.of(who, count)));

        config.normalize();

        assertTrue(config.parameters("kit").get(1).optional,
                "a required argument cannot follow one that may be left out");
    }

    @Test
    void shouldTreatADefaultAsMakingTheArgumentOptional() {
        Parameter parameter = new Parameter("count", AliasesConfig.TYPE_INTEGER);
        parameter.defaultValue = "1";
        parameter.normalize();
        assertTrue(parameter.optional, "a default is only reachable when the argument may be left out");
        assertEquals("1", parameter.fallback());
    }

    @Test
    void shouldDropWhatTheTypeCannotCarry() {
        Parameter text = new Parameter("message", AliasesConfig.TYPE_TEXT);
        text.choices = new ArrayList<>(List.of("a", "b"));
        text.min = 1;
        text.normalize();
        assertTrue(text.choices.isEmpty(), "only a word argument carries choices");
        assertNull(text.min, "only an integer argument carries a range");

        Parameter player = new Parameter("target", AliasesConfig.TYPE_PLAYER);
        player.allowSelectors = true;
        player.normalize();
        assertFalse(player.allowSelectors, "a player argument is resolved, so no selector reaches a step");
    }

    @Test
    void shouldFallBackToWordOnAnUnknownType() {
        Parameter parameter = new Parameter("x", "entity");
        parameter.normalize();
        assertEquals(AliasesConfig.TYPE_WORD, parameter.type);
    }
}
