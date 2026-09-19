/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.config.CommandsConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Commands, aliases and rate-limit rules: one row each, and back to the same configuration. */
class SmallCodecsTest {

    private static <T> T roundTrip(PartCodec<T> codec, T config) {
        T copy = codec.empty();
        codec.split(config).forEach((holder, body) -> codec.patch(copy, holder, body));
        codec.afterPatch(copy);
        assertEquals(codec.split(config), codec.split(copy));
        return copy;
    }

    @Test
    void commands() {
        CommandsCodec codec = new CommandsCodec();
        CommandsConfig config = codec.empty();
        config.grantedCommands.add("gamemode");
        config.grantedCommands.add("home");
        config.preserveOriginalRequires.put("gamemode", true);
        config.preserveOriginalRequires.put("tp", false);
        assertEquals(Set.of("command:gamemode", "command:home", "command:tp"), codec.split(config).keySet());

        CommandsConfig copy = roundTrip(codec, config);
        assertEquals(Set.of("gamemode", "home"), copy.grantedCommands);
        assertEquals(Map.of("gamemode", true, "tp", false), copy.preserveOriginalRequires);

        codec.patch(copy, "command:gamemode", null);
        assertFalse(copy.grantedCommands.contains("gamemode"));
        assertFalse(copy.preserveOriginalRequires.containsKey("gamemode"));
    }

    @Test
    void aliasesKeepTheirStepsInOrder() {
        AliasesCodec codec = new AliasesCodec();
        AliasesConfig config = codec.empty();
        config.aliases.put("heal", new java.util.ArrayList<>(List.of("effect give @s regeneration", "say healed")));
        AliasesConfig copy = roundTrip(codec, config);
        assertEquals(List.of("effect give @s regeneration", "say healed"), copy.aliases.get("heal"));
    }

    @Test
    void rateLimitRules() {
        RateLimitsCodec codec = new RateLimitsCodec();
        RateLimitsConfig config = codec.empty();
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.maxExecutions = 3;
        rule.windowSeconds = 60;
        rule.enabled = false;
        config.rules.put("home", rule);
        RateLimitsConfig copy = roundTrip(codec, config);
        assertEquals(3, copy.rules.get("home").maxExecutions);
        assertFalse(copy.rules.get("home").enabled);
    }
}
