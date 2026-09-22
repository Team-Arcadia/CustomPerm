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
    private static final java.util.regex.Pattern SERVER_NAME = ServerScope.NAME;

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
        /**
         * The cluster members this rule is enforced on; null for every member. Not {@link #scope}: the scope says
         * which members count uses together, this list says where the rule applies at all. See {@link ServerScope}.
         */
        public java.util.List<String> servers;
        /**
         * A limit of its own for some cluster members, by server name; null when none has one. Only read on a
         * member that counts its uses alone: where the counter is shared, one budget means one limit.
         */
        public Map<String, Limit> perServer;

        public void normalize() {
            if (maxExecutions < 1) maxExecutions = 1;
            if (windowSeconds < 1) windowSeconds = 1;
            if (perServer != null) {
                Map<String, Limit> clean = new java.util.TreeMap<>();
                perServer.forEach((name, limit) -> {
                    String lower = lower(name);
                    if (limit == null || !SERVER_NAME.matcher(lower).matches()) return;
                    limit.normalize();
                    clean.put(lower, limit);
                });
                perServer = clean.isEmpty() ? null : clean;
            }
            persistence = persistence == null ? PERSISTENCE_WORLD_SAVE : persistence.trim().toLowerCase(java.util.Locale.ROOT);
            if (!PERSISTENCE_IMMEDIATE.equals(persistence)) persistence = PERSISTENCE_WORLD_SAVE;
            String clean = normalizeScope(scope);
            scope = clean == null ? SCOPE_SERVER : clean;
            // Null rather than empty, so a rule without a list keeps the cluster row it had before lists existed.
            servers = ServerScope.normalize(servers);
        }

        /** Whether this rule applies on the server named {@code here} (null outside a cluster). */
        public boolean appliesHere(String here) {
            return ServerScope.appliesHere(servers, here);
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

        /**
         * The rule's own limit on the server named {@code here}: its value for that member when it has one and
         * counts alone, the rule's numbers otherwise. A grade's or a player's value, read from their meta, still
         * comes on top of it.
         */
        public Limit limitOn(String here) {
            if (here != null && perServer != null && !shared(here)) {
                Limit own = perServer.get(lower(here));
                if (own != null) return own;
            }
            return new Limit(maxExecutions, windowSeconds);
        }

        /**
         * The limit on {@code here} for a holder whose meta {@code customperm.ratelimit.<name>} reads
         * {@code metaValue}: that value when it reads as a limit, the rule's own limit on {@code here} otherwise. A
         * value that does not read as a limit is ignored rather than locking anyone out.
         */
        public Limit limitFor(String here, String metaValue) {
            Limit own = limitOn(here);
            Limit meta = parseLimit(metaValue, own.windowSeconds);
            return meta != null ? meta : own;
        }

        /** The longest window this rule itself sets anywhere, in seconds. */
        public int longestWindowSeconds() {
            int longest = windowSeconds;
            if (perServer != null) {
                for (Limit limit : perServer.values()) longest = Math.max(longest, limit.windowSeconds);
            }
            return longest;
        }

        /**
         * The servers of {@code candidates} that would share their counter under {@code scope}: those cannot keep a
         * limit of their own. Empty for {@link #SCOPE_SERVER}.
         */
        public static java.util.List<String> sharingUnder(String scope, java.util.Collection<String> candidates) {
            if (candidates == null || SCOPE_SERVER.equals(scope)) return java.util.List.of();
            if (SCOPE_NETWORK.equals(scope)) return new java.util.ArrayList<>(candidates);
            java.util.List<String> named = java.util.List.of(scope.split(","));
            return candidates.stream().filter(named::contains).toList();
        }
    }

    /**
     * A number of uses within a window. {@link #UNLIMITED} lifts the limit; it is only written in a grade's or a
     * player's meta, never in the rule itself.
     */
    public static class Limit {
        public int maxExecutions;
        public int windowSeconds;

        public static final Limit UNLIMITED = new Limit(Integer.MAX_VALUE, 1);

        public Limit() {
        }

        public Limit(int maxExecutions, int windowSeconds) {
            this.maxExecutions = maxExecutions;
            this.windowSeconds = windowSeconds;
        }

        public boolean unlimited() {
            return maxExecutions == Integer.MAX_VALUE;
        }

        void normalize() {
            if (maxExecutions < 1) maxExecutions = 1;
            if (windowSeconds < 1) windowSeconds = 1;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Limit limit && limit.maxExecutions == maxExecutions
                    && limit.windowSeconds == windowSeconds;
        }

        @Override
        public int hashCode() {
            return 31 * maxExecutions + windowSeconds;
        }

        /** As an admin types it: {@code 10/1h}, or {@code unlimited}. */
        @Override
        public String toString() {
            return unlimited() ? UNLIMITED_VALUE : maxExecutions + "/" + window(windowSeconds);
        }
    }

    /** The meta a grade or a player carries to get a limit of their own on {@code /<name>}. */
    public static final String META_PREFIX = "customperm.ratelimit.";
    /** The meta value lifting the limit for that holder. */
    public static final String UNLIMITED_VALUE = "unlimited";

    public static String metaKey(String name) {
        return META_PREFIX + name.toLowerCase(java.util.Locale.ROOT);
    }

    private static final java.util.regex.Pattern VALUE =
            java.util.regex.Pattern.compile("(\\d{1,9})(?:/(\\d{1,9})([smhd]?))?");

    /**
     * A limit as written in meta: {@code 10/1h}, {@code 10/3600}, {@code 10} (the rule's own window), or
     * {@code unlimited}. Null when the value is none of these, so a mistyped value leaves the rule deciding
     * rather than locking anyone out.
     */
    public static Limit parseLimit(String raw, int ruleWindowSeconds) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.equals(UNLIMITED_VALUE)) return Limit.UNLIMITED;
        java.util.regex.Matcher m = VALUE.matcher(value);
        if (!m.matches()) return null;
        int max = Integer.parseInt(m.group(1));
        if (max < 1) return null;
        if (m.group(2) == null) return new Limit(max, ruleWindowSeconds);
        long window = Long.parseLong(m.group(2)) * switch (m.group(3)) {
            case "m" -> 60L;
            case "h" -> 3600L;
            case "d" -> 86400L;
            default -> 1L;
        };
        if (window < 1 || window > Integer.MAX_VALUE) return null;
        return new Limit(max, (int) window);
    }

    /**
     * The longest window any grade or player sets in the meta {@code key}, in seconds, 0 when none does. Reads the
     * grades file only: under LuckPerms, the windows its meta sets are learnt as they are used (see RateLimiter).
     */
    public static long longestMetaWindow(GradesConfig grades, String key, int ruleWindowSeconds) {
        long longest = 0L;
        for (GradesConfig.Grade grade : grades.grades.values()) {
            longest = Math.max(longest, metaWindow(grade.meta, key, ruleWindowSeconds));
            for (GradesConfig.GradeScoped scope : grade.contexts.values()) {
                longest = Math.max(longest, metaWindow(scope.meta, key, ruleWindowSeconds));
            }
        }
        for (Map<String, String> meta : grades.userMeta.values()) {
            longest = Math.max(longest, metaWindow(meta, key, ruleWindowSeconds));
        }
        for (Map<String, GradesConfig.UserScoped> scopes : grades.userContexts.values()) {
            for (GradesConfig.UserScoped scope : scopes.values()) {
                longest = Math.max(longest, metaWindow(scope.meta, key, ruleWindowSeconds));
            }
        }
        return longest;
    }

    private static long metaWindow(Map<String, String> meta, String key, int ruleWindowSeconds) {
        if (meta == null) return 0L;
        Limit limit = parseLimit(meta.get(key), ruleWindowSeconds);
        return limit == null || limit.unlimited() ? 0L : limit.windowSeconds;
    }

    /** {@code 3600} as {@code 1h}, {@code 90} as {@code 90s}: the largest unit that divides it. */
    public static String window(int seconds) {
        if (seconds % 86400 == 0) return seconds / 86400 + "d";
        if (seconds % 3600 == 0) return seconds / 3600 + "h";
        if (seconds % 60 == 0) return seconds / 60 + "m";
        return seconds + "s";
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

    /** The rule counting uses of {@code commandName} on the server named {@code here}: enabled and active there. */
    public Rule activeRule(String commandName, String here) {
        Rule rule = rules.get(commandName);
        return rule != null && rule.enabled && rule.appliesHere(here) ? rule : null;
    }

    public boolean isEnforced(String commandName) {
        Rule rule = rules.get(commandName);
        return rule != null && rule.enabled;
    }
}
