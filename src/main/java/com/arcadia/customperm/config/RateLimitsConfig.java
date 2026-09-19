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

    /** Usage history written with the world save (autosave, save-all, stop). */
    public static final String PERSISTENCE_WORLD_SAVE = "world_save";
    /** Usage history written right after each accepted use: nothing lost on a crash, one disk write per use. */
    public static final String PERSISTENCE_IMMEDIATE = "immediate";

    /** Each server counts its own uses: the default, and what every rule does outside a cluster. */
    public static final String SCOPE_SERVER = "server";
    /** Every server of the cluster counts against one budget. */
    public static final String SCOPE_NETWORK = "network";
    private static final java.util.regex.Pattern SERVER_NAME = java.util.regex.Pattern.compile("[0-9a-z_.\\-]{1,64}");

    public static class Rule {
        public boolean enabled = true;
        public int maxExecutions = 10;
        public int windowSeconds = 3600;
        public String persistence = PERSISTENCE_WORLD_SAVE;
        /**
         * Who shares the budget, in cluster mode: {@link #SCOPE_SERVER}, {@link #SCOPE_NETWORK}, or server names joined
         * with a comma ({@code hub,survival}), which share one budget among themselves while the others count alone.
         */
        public String scope = SCOPE_SERVER;

        public void normalize() {
            if (maxExecutions < 1) maxExecutions = 1;
            if (windowSeconds < 1) windowSeconds = 1;
            persistence = persistence == null ? PERSISTENCE_WORLD_SAVE : persistence.trim().toLowerCase(java.util.Locale.ROOT);
            if (!PERSISTENCE_IMMEDIATE.equals(persistence)) persistence = PERSISTENCE_WORLD_SAVE;
            String clean = normalizeScope(scope);
            scope = clean == null ? SCOPE_SERVER : clean;
        }

        /** Whether this server's uses of the rule are counted by other servers too. */
        public boolean shared(String here) {
            return SCOPE_NETWORK.equals(scope) || !SCOPE_SERVER.equals(scope) && servers().contains(lower(here));
        }

        /** Whether a use counted on {@code other} counts here. */
        public boolean sharedWith(String here, String other) {
            if (SCOPE_NETWORK.equals(scope)) return true;
            if (SCOPE_SERVER.equals(scope)) return false;
            java.util.List<String> servers = servers();
            return servers.contains(lower(here)) && servers.contains(lower(other));
        }

        private java.util.List<String> servers() {
            return java.util.List.of(scope.split(","));
        }

        public boolean persistsImmediately() {
            return PERSISTENCE_IMMEDIATE.equals(persistence);
        }
    }

    /**
     * The stored form of a scope as typed: {@code server}, {@code network}, or server names lowercased, sorted and
     * joined with a comma; null when it is none of these.
     */
    public static String normalizeScope(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (clean.equals(SCOPE_SERVER) || clean.equals(SCOPE_NETWORK)) return clean;
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String part : clean.split(",")) {
            String name = part.trim();
            if (!SERVER_NAME.matcher(name).matches()) return null;
            names.add(name);
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private static String lower(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Whether any rule is counted with other servers from {@code here}, which is when uses are worth sending. */
    public boolean anyShared(String here) {
        for (Rule rule : rules.values()) {
            if (rule.shared(here)) return true;
        }
        return false;
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
