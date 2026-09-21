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

    /**
     * A list travels in the row body; a row without one keeps the exact body it had before lists existed, so an
     * upgraded member does not republish every row, and an older member reading a row with a list ignores it.
     */
    @Test
    void serverListsTravelAndStayOutOfRowsWithoutThem() {
        CommandsCodec commands = new CommandsCodec();
        CommandsConfig config = commands.empty();
        config.grantedCommands.add("tp");
        config.grantedCommands.add("seed");
        config.commandServers.put("tp", List.of("Demo-B", "demo-a"));
        config.normalize();
        assertEquals("{\"exposed\":true}", commands.split(config).get("command:seed"));
        assertEquals("{\"exposed\":true,\"servers\":[\"demo-a\",\"demo-b\"]}", commands.split(config).get("command:tp"));
        CommandsConfig commandsCopy = roundTrip(commands, config);
        assertEquals(List.of("demo-a", "demo-b"), commandsCopy.servers("tp"));
        assertEquals(List.of(), commandsCopy.servers("seed"));
        commands.patch(commandsCopy, "command:tp", "{\"exposed\":true}");
        assertEquals(List.of(), commandsCopy.servers("tp"), "A row that loses its list must lose it here too.");

        AliasesCodec aliases = new AliasesCodec();
        AliasesConfig aliasConfig = aliases.empty();
        aliasConfig.aliases.put("hub", new java.util.ArrayList<>(List.of("say hub")));
        aliasConfig.aliases.put("spawn", new java.util.ArrayList<>(List.of("say spawn")));
        aliasConfig.aliasServers.put("hub", List.of("demo-a"));
        assertEquals("{\"steps\":[\"say spawn\"]}", aliases.split(aliasConfig).get("alias:spawn"));
        AliasesConfig aliasCopy = roundTrip(aliases, aliasConfig);
        assertEquals(List.of("demo-a"), aliasCopy.servers("hub"));

        RateLimitsCodec limits = new RateLimitsCodec();
        RateLimitsConfig limitConfig = limits.empty();
        RateLimitsConfig.Rule plain = new RateLimitsConfig.Rule();
        RateLimitsConfig.Rule scoped = new RateLimitsConfig.Rule();
        scoped.servers = new java.util.ArrayList<>(List.of("demo-b"));
        limitConfig.rules.put("home", plain);
        limitConfig.rules.put("hub", scoped);
        limitConfig.normalize();
        assertFalse(limits.split(limitConfig).get("rule:home").contains("servers"));
        RateLimitsConfig limitCopy = roundTrip(limits, limitConfig);
        assertEquals(List.of("demo-b"), limitCopy.rules.get("hub").servers);
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
