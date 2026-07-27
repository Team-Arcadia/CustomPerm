/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-command execution rate limits, keyed by the exposed command name or alias name
 * (same namespace CommandsConfig#grantedCommands / AliasesConfig#aliases use).
 *
 * Each rule caps how many times a single player may run the command within a sliding
 * time window. The window is only enforced while {@code enabled} is true — disabling a
 * rule keeps its configured values so an admin can re-enable it without re-entering them.
 */
public class RateLimitsConfig {
    public Map<String, Rule> rules = new LinkedHashMap<>();

    public static class Rule {
        public boolean enabled = true;
        public int maxExecutions = 10;
        public int windowSeconds = 3600;

        public void normalize() {
            if (maxExecutions < 1) maxExecutions = 1;
            if (windowSeconds < 1) windowSeconds = 1;
        }
    }

    public void normalize() {
        if (rules == null) rules = new LinkedHashMap<>();
        rules.values().removeIf(java.util.Objects::isNull);
        for (Rule rule : rules.values()) {
            rule.normalize();
        }
    }

    public Rule get(String commandName) {
        return rules.get(commandName);
    }

    public boolean isEnforced(String commandName) {
        Rule rule = rules.get(commandName);
        return rule != null && rule.enabled;
    }
}
