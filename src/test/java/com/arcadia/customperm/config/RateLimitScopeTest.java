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

import static org.junit.jupiter.api.Assertions.*;

/** Who shares a rate limit's budget in cluster mode. */
class RateLimitScopeTest {

    private static RateLimitsConfig.Rule rule(String scope) {
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.scope = scope;
        rule.normalize();
        return rule;
    }

    @Test
    void scopesAreReadAsTyped() {
        assertEquals("server", RateLimitsConfig.normalizeScope(" Server "));
        assertEquals("network", RateLimitsConfig.normalizeScope("NETWORK"));
        assertEquals("hub,survival", RateLimitsConfig.normalizeScope("Survival, hub,hub"));
        assertNull(RateLimitsConfig.normalizeScope("hub,,survival"));
        assertNull(RateLimitsConfig.normalizeScope("bad name"));
        assertEquals("server", rule(null).scope, "a rule without a scope counts per server");
        assertEquals("server", rule("??").scope, "an unreadable scope falls back to per server");
    }

    @Test
    void serverSharesNothing() {
        RateLimitsConfig.Rule rule = rule("server");
        assertFalse(rule.shared("hub"));
        assertFalse(rule.sharedWith("hub", "survival"));
    }

    @Test
    void networkSharesWithEveryServer() {
        RateLimitsConfig.Rule rule = rule("network");
        assertTrue(rule.shared("hub"));
        assertTrue(rule.sharedWith("hub", "anything"));
    }

    @Test
    void aGroupSharesAmongItsMembersOnly() {
        RateLimitsConfig.Rule rule = rule("hub,survival");
        assertTrue(rule.shared("Hub"));
        assertFalse(rule.shared("creative"), "a server outside the group keeps its uses to itself");
        assertTrue(rule.sharedWith("hub", "Survival"));
        assertFalse(rule.sharedWith("hub", "creative"));
        assertFalse(rule.sharedWith("creative", "hub"), "an outsider does not count the group's uses either");
    }

    @Test
    void anySharedTellsWhenUsesAreWorthSending() {
        RateLimitsConfig config = new RateLimitsConfig();
        config.rules.put("home", rule("server"));
        assertFalse(config.anyShared("hub"));
        config.rules.put("kit", rule("hub,survival"));
        assertTrue(config.anyShared("hub"));
        assertFalse(config.anyShared("creative"));
    }
}
