/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.RateLimitsConfig;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rate limits: how many times one player may run an exposed command or an alias within a sliding
 * window. Shared by {@code /customperm ratelimit} and the admin interface, server thread only. Rules
 * are read when a command runs, so no command tree needs resending.
 */
public final class RateLimitAdmin {

    /** Same characters Brigadier's {@code word()} accepts. */
    private static final Pattern NAME = Pattern.compile("[0-9A-Za-z_\\-.+]{1,64}");

    private RateLimitAdmin() {
    }

    private static Map<String, RateLimitsConfig.Rule> rules() {
        return CustomPerm.configManager.getRateLimits().rules;
    }

    private static AdminResult noRule(String name, boolean hintSet) {
        return AdminResult.fail("No rate limit configured for /" + name + "."
                + (hintSet ? " Use /customperm ratelimit set first." : ""));
    }

    public static AdminResult set(String name, int max, int windowSeconds) {
        if (!NAME.matcher(name).matches()) {
            return AdminResult.fail("Invalid command name '" + name + "': use 1 to 64 letters, digits, _ - . or +.");
        }
        if (max < 1 || windowSeconds < 1) {
            return AdminResult.fail("A rate limit needs at least 1 use per window of at least 1 second.");
        }
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.enabled = true;
        rule.maxExecutions = max;
        rule.windowSeconds = windowSeconds;
        // Redefining the numbers must not silently reset a persistence mode the admin chose.
        RateLimitsConfig.Rule previous = rules().get(name);
        if (previous != null) rule.persistence = previous.persistence;
        rule.normalize();
        rules().put(name, rule);

        AdminResult result = AdminResult.ok("Rate limit for /" + name + " set to " + rule.maxExecutions + " per "
                + rule.windowSeconds + "s (enabled).").warn(ConfigAdmin.persist());
        boolean exposed = CustomPerm.configManager.getCommands().grantedCommands.contains(name);
        boolean alias = CustomPerm.configManager.getAliases().aliases.containsKey(name);
        if (!exposed && !alias) {
            result = result.warn("Note: /" + name + " is not currently exposed or an alias — the limit will take effect once it is.");
        }
        return result;
    }

    public static AdminResult setPersistence(String name, String rawMode) {
        String mode = rawMode.toLowerCase(Locale.ROOT);
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        if (!mode.equals(RateLimitsConfig.PERSISTENCE_WORLD_SAVE) && !mode.equals(RateLimitsConfig.PERSISTENCE_IMMEDIATE)) {
            return AdminResult.fail("Unknown persistence mode '" + mode + "'. Use world_save or immediate.");
        }
        rule.persistence = mode;
        return AdminResult.ok("Usage history of /" + name + " is now written "
                + (rule.persistsImmediately() ? "after every accepted use (immediate)." : "with the world save (world_save)."))
                .warn(ConfigAdmin.persist());
    }

    public static AdminResult enable(String name) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        if (rule.enabled) return AdminResult.ok("Rate limit for /" + name + " is already enabled — no change.");
        rule.enabled = true;
        return AdminResult.ok("Rate limit for /" + name + " enabled (" + rule.maxExecutions + " per "
                + rule.windowSeconds + "s).").warn(ConfigAdmin.persist());
    }

    public static AdminResult disable(String name) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, false);
        if (!rule.enabled) return AdminResult.ok("Rate limit for /" + name + " is already disabled — no change.");
        rule.enabled = false;
        return AdminResult.ok("Rate limit for /" + name
                + " disabled. Settings kept — use /customperm ratelimit enable to restore.").warn(ConfigAdmin.persist());
    }

    public static AdminResult remove(String name) {
        if (rules().remove(name) == null) return noRule(name, false);
        return AdminResult.ok("Rate limit for /" + name + " removed.").warn(ConfigAdmin.persist());
    }
}
