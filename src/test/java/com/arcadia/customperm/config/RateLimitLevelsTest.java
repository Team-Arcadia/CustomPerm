/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A rate limit decided rule, then server, then grade or player: the per-server values, the meta value format, and
 * the longest window a holder can set. Pure Java.
 */
class RateLimitLevelsTest {

    private static RateLimitsConfig.Rule rule(int max, int window) {
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.maxExecutions = max;
        rule.windowSeconds = window;
        return rule;
    }

    private static RateLimitsConfig.Limit limit(int max, int window) {
        return new RateLimitsConfig.Limit(max, window);
    }

    @Test
    void metaValuesReadAsLimits() {
        assertEquals(limit(10, 3600), RateLimitsConfig.parseLimit("10/1h", 60));
        assertEquals(limit(10, 1800), RateLimitsConfig.parseLimit("10/30m", 60));
        assertEquals(limit(10, 90), RateLimitsConfig.parseLimit("10/90s", 60));
        assertEquals(limit(10, 90), RateLimitsConfig.parseLimit("10/90", 60));
        assertEquals(limit(3, 172800), RateLimitsConfig.parseLimit(" 3/2D ", 60), "case and spaces do not matter");
        assertEquals(limit(10, 60), RateLimitsConfig.parseLimit("10", 60), "a number alone keeps the rule's window");
        assertTrue(RateLimitsConfig.parseLimit("unlimited", 60).unlimited());
        assertTrue(RateLimitsConfig.parseLimit("UNLIMITED", 60).unlimited());
    }

    @Test
    void valuesThatAreNotLimitsAreIgnored() {
        for (String bad : new String[] {null, "", "0", "0/1h", "10/0", "-1", "ten", "10/1w", "10/1h/2", "10 per hour",
                "99999999999/1h"}) {
            assertNull(RateLimitsConfig.parseLimit(bad, 60), "'" + bad + "' must not read as a limit");
        }
    }

    @Test
    void aLimitPrintsTheWayItIsTyped() {
        assertEquals("10/1h", limit(10, 3600).toString());
        assertEquals("5/90s", limit(5, 90).toString());
        assertEquals("2/1d", limit(2, 86400).toString());
        assertEquals("unlimited", RateLimitsConfig.Limit.UNLIMITED.toString());
    }

    @Test
    void aServerCountingAloneUsesItsOwnLimit() {
        RateLimitsConfig.Rule rule = rule(3, 60);
        rule.perServer = new TreeMap<>(Map.of("survival", limit(5, 120)));
        rule.normalize();

        assertEquals(limit(5, 120), rule.limitOn("survival"));
        assertEquals(limit(5, 120), rule.limitOn("SURVIVAL"), "server names are compared lowercased");
        assertEquals(limit(3, 60), rule.limitOn("hub"), "a member without a value follows the rule");
        assertEquals(limit(3, 60), rule.limitOn(null), "outside a cluster the rule decides");
    }

    @Test
    void aSharedCounterMeansOneLimit() {
        RateLimitsConfig.Rule rule = rule(3, 60);
        rule.perServer = new TreeMap<>(Map.of("survival", limit(5, 120), "hub", limit(9, 60)));
        rule.scope = "network";
        rule.normalize();
        assertEquals(limit(3, 60), rule.limitOn("survival"), "network: every member shares, so the rule decides");

        rule.scope = "creative,survival";
        rule.normalize();
        assertEquals(limit(3, 60), rule.limitOn("survival"), "survival shares with creative");
        assertEquals(limit(9, 60), rule.limitOn("hub"), "hub still counts alone");
    }

    @Test
    void membersThatWouldShareAreNamed() {
        List<String> candidates = List.of("hub", "survival");
        assertEquals(List.of(), RateLimitsConfig.Rule.sharingUnder("server", candidates));
        assertEquals(candidates, RateLimitsConfig.Rule.sharingUnder("network", candidates));
        assertEquals(List.of("survival"), RateLimitsConfig.Rule.sharingUnder("creative,survival", candidates));
        assertEquals(List.of(), RateLimitsConfig.Rule.sharingUnder("network", null));
    }

    @Test
    void aHolderValueHasTheLastWord() {
        RateLimitsConfig.Rule rule = rule(3, 60);
        rule.perServer = new TreeMap<>(Map.of("survival", limit(5, 120)));
        rule.normalize();

        assertEquals(limit(3, 60), rule.limitFor("hub", null), "no meta: the rule");
        assertEquals(limit(5, 120), rule.limitFor("survival", null), "no meta: the server's own value");
        assertEquals(limit(10, 3600), rule.limitFor("survival", "10/1h"), "the holder's value wins over the server's");
        assertEquals(limit(10, 120), rule.limitFor("survival", "10"), "a number alone keeps this server's window");
        assertTrue(rule.limitFor("hub", "unlimited").unlimited());
        assertEquals(limit(5, 120), rule.limitFor("survival", "lots"), "a mistyped value leaves the server's value");
    }

    @Test
    void perServerValuesAreCleanedOnLoad() {
        RateLimitsConfig.Rule rule = rule(3, 60);
        Map<String, RateLimitsConfig.Limit> raw = new TreeMap<>();
        raw.put("Survival", limit(0, 0));
        raw.put("bad name!", limit(5, 60));
        raw.put("hub", null);
        rule.perServer = raw;
        rule.normalize();

        assertEquals(Map.of("survival", limit(1, 1)), rule.perServer, "lowercased, clamped, invalid ones dropped");

        rule.perServer = new TreeMap<>(Map.of("bad name!", limit(5, 60)));
        rule.normalize();
        assertNull(rule.perServer, "nothing left: no map, so the stored rule stays as it was");
    }

    @Test
    void theLongestWindowCountsEveryServer() {
        RateLimitsConfig.Rule rule = rule(3, 60);
        assertEquals(60, rule.longestWindowSeconds());
        rule.perServer = new TreeMap<>(Map.of("survival", limit(5, 7200), "hub", limit(1, 30)));
        assertEquals(7200, rule.longestWindowSeconds());
    }

    @Test
    void aRuleWithoutServerValuesReadsAndWritesAsBefore() {
        Gson gson = new Gson();
        RateLimitsConfig.Rule old = gson.fromJson("{\"maxExecutions\":3,\"windowSeconds\":60}", RateLimitsConfig.Rule.class);
        old.normalize();
        assertNull(old.perServer);
        assertFalse(gson.toJson(old).contains("perServer"), "no new field is written for a rule that does not use it");

        old.perServer = new TreeMap<>(Map.of("hub", limit(9, 60)));
        RateLimitsConfig.Rule back = gson.fromJson(gson.toJson(old), RateLimitsConfig.Rule.class);
        back.normalize();
        assertEquals(Map.of("hub", limit(9, 60)), back.perServer);
    }

    @Test
    void theLongestMetaWindowIsFoundWhereverItIsSet() {
        String key = RateLimitsConfig.metaKey("Hub");
        assertEquals("customperm.ratelimit.hub", key);

        GradesConfig grades = new GradesConfig();
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.meta.put(key, "10/1h");
        GradesConfig.GradeScoped onHub = new GradesConfig.GradeScoped();
        onHub.meta.put(key, "20/2h");
        vip.contexts.put("server=hub", onHub);
        grades.grades.put("vip", vip);
        assertEquals(7200, RateLimitsConfig.longestMetaWindow(grades, key, 60), "a grade's value limited to one server");

        grades.userMeta.put("00000000-0000-0000-0000-000000000001", new TreeMap<>(Map.of(key, "1/1d")));
        assertEquals(86400, RateLimitsConfig.longestMetaWindow(grades, key, 60), "a player's own value");

        grades.userMeta.put("00000000-0000-0000-0000-000000000002", new TreeMap<>(Map.of(key, "unlimited")));
        grades.userMeta.put("00000000-0000-0000-0000-000000000003", new TreeMap<>(Map.of(key, "junk")));
        assertEquals(86400, RateLimitsConfig.longestMetaWindow(grades, key, 60), "unlimited and junk set no window");

        assertEquals(0, RateLimitsConfig.longestMetaWindow(new GradesConfig(), key, 60));
    }
}
