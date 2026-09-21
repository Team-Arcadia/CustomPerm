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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which cluster member an exposed command, an alias or a rate limit is active on. */
class ServerScopeTest {

    @Test
    void anEmptyListOrNoClusterMeansEverywhere() {
        assertTrue(ServerScope.appliesHere(null, "demo-a"));
        assertTrue(ServerScope.appliesHere(List.of(), "demo-a"));
        assertTrue(ServerScope.appliesHere(List.of("demo-b"), null), "Outside a cluster no list is read.");
        assertTrue(ServerScope.appliesHere(List.of("demo-a", "demo-b"), "Demo-A"));
        assertFalse(ServerScope.appliesHere(List.of("demo-b"), "demo-a"));
    }

    @Test
    void theStoredListIsSortedLowercasedAndNullWhenEmpty() {
        assertEquals(List.of("demo-a", "demo-b"), ServerScope.normalize(Arrays.asList(" Demo-B", "demo-a", "DEMO-A", null)));
        assertNull(ServerScope.normalize(List.of()));
        assertNull(ServerScope.normalize(List.of("not a name!")));
        assertNull(ServerScope.normalize(null));
        assertNull(ServerScope.problem("hub_1.eu-west"));
        assertNotNull(ServerScope.problem("two words"));
    }

    @Test
    void aTypedListTakesSpacesCommasHereAndAll() {
        assertEquals(List.of("demo-a", "hub"), ServerScope.parse("hub, Demo-A  hub", null).names());
        assertEquals(List.of("alpha", "hub"), ServerScope.parse("here,hub", "alpha").names());
        assertNull(ServerScope.parse("all", "alpha").names());
        assertNull(ServerScope.parse("  ", "alpha").names());
        assertNull(ServerScope.parse("all", "alpha").problem());
        assertNotNull(ServerScope.parse("hub all", "alpha").problem(), "all cannot be narrowed.");
        assertNotNull(ServerScope.parse("here", null).problem(), "here needs a cluster name.");
        assertNotNull(ServerScope.parse("hub two!", "alpha").problem());
    }

    @Test
    void aCommandIsExposedOnlyOnTheMembersItsListNames() {
        CommandsConfig config = new CommandsConfig();
        config.grantedCommands.add("tp");
        config.commandServers.put("tp", new ArrayList<>(List.of("demo-a")));
        config.commandServers.put("gone", new ArrayList<>(List.of("demo-a")));
        config.normalize();
        assertTrue(config.exposedHere("tp", "demo-a"));
        assertFalse(config.exposedHere("tp", "demo-b"));
        assertTrue(config.exposedHere("tp", null));
        assertFalse(config.commandServers.containsKey("gone"), "A list on a command that is not exposed is dropped.");
        config.removeCommand("tp");
        assertFalse(config.commandServers.containsKey("tp"), "Hiding a command drops its list.");
    }

    @Test
    void anAliasIsActiveOnlyOnTheMembersItsListNames() {
        AliasesConfig config = new AliasesConfig();
        config.aliases.put("hub", new ArrayList<>(List.of("say hub")));
        config.aliasServers.put("hub", new ArrayList<>(List.of("demo-b")));
        config.aliasServers.put("gone", new ArrayList<>(List.of("demo-b")));
        config.normalize();
        assertTrue(config.activeHere("hub", "demo-b"));
        assertFalse(config.activeHere("hub", "demo-a"));
        assertFalse(config.activeHere("gone", null));
        assertFalse(config.aliasServers.containsKey("gone"));
    }

    @Test
    void aRuleCountsUsesOnlyWhereItApplies() {
        RateLimitsConfig config = new RateLimitsConfig();
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.servers = new ArrayList<>(List.of("demo-a"));
        config.rules.put("hub", rule);
        config.normalize();
        assertNotNull(config.activeRule("hub", "demo-a"));
        assertNull(config.activeRule("hub", "demo-b"));
        rule.enabled = false;
        assertNull(config.activeRule("hub", "demo-a"), "A disabled rule counts nothing anywhere.");
        rule.servers = new ArrayList<>();
        rule.normalize();
        assertNull(rule.servers, "An emptied list is stored as none, like a rule written before lists existed.");
    }
}
