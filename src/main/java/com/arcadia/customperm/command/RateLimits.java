/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.cluster.Cluster;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The limit one player gets on one exposed command or alias, and the counting of their uses. Rule, then server,
 * then grade, then player, each with the last word over the one before it:
 * <ol>
 *   <li>the rule's numbers;</li>
 *   <li>the rule's value for this server, on a member that counts its uses alone;</li>
 *   <li>the meta {@code customperm.ratelimit.<name>} of the player's grades, ranked like any meta, a value limited
 *       to {@code server=} winning over one held everywhere;</li>
 *   <li>the player's own such meta.</li>
 * </ol>
 * Steps 3 and 4 are one meta lookup, so LuckPerms answers them itself when it runs.
 */
public final class RateLimits {

    private RateLimits() {
    }

    /** The rule counting uses of {@code name} on this server, or null when none is active here. */
    public static RateLimitsConfig.Rule active(String name) {
        return CustomPerm.configManager.getRateLimits().activeRule(name, Cluster.identity());
    }

    /** The limit {@code player} gets on {@code /name} here, or null when no rule is active here. */
    public static RateLimitsConfig.Limit limitFor(ServerPlayer player, String name) {
        RateLimitsConfig.Rule rule = active(name);
        if (rule == null) return null;
        return rule.limitFor(Cluster.identity(), PermissionService.get().meta(player, RateLimitsConfig.metaKey(name)));
    }

    /**
     * Counts one use of {@code name} by the source and tells the player when the limit refuses it. The console,
     * command blocks and functions are never limited. An unlimited holder's uses are not counted, so they take
     * nothing from a budget shared with other servers.
     */
    public static boolean acquire(CommandSourceStack source, String name) {
        // Amortised memory reclaim, self-throttled to once per interval and piggybacked on command execution
        // (single-threaded server tick), so idle players' history is evicted without a scheduled task.
        RateLimiter.maybeSweep(System.currentTimeMillis(), RateLimits::retentionMillis);
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        RateLimitsConfig.Rule rule = active(name);
        if (rule == null) return true;
        RateLimitsConfig.Limit limit = rule.limitFor(Cluster.identity(),
                PermissionService.get().meta(player, RateLimitsConfig.metaKey(name)));
        if (limit.unlimited()) return true;
        RateLimiter.Result result = RateLimiter.tryAcquire(name, player.getUUID(), limit.maxExecutions, limit.windowSeconds);
        if (!result.allowed()) {
            source.sendFailure(Component.literal("[CustomPerm] Rate limit reached for /" + name + " — try again in "
                    + result.retryAfterSeconds() + "s (max " + limit.maxExecutions + " per "
                    + limit.windowSeconds + "s)."));
            return false;
        }
        RateLimitPersistence.afterAcceptedUse(rule);
        return true;
    }

    /**
     * How long uses of {@code name} are kept, in ms: the longest window the rule, its servers or a grade's or a
     * player's meta sets, since any of them may count a use. {@code <= 0} when no rule is active here, which lets
     * the history go.
     */
    public static long retentionMillis(String name) {
        RateLimitsConfig.Rule rule = active(name);
        if (rule == null) return -1L;
        return longestWindowSeconds(name, rule) * 1000L;
    }

    /** The longest window {@code rule} can count a use of {@code name} with, in seconds. */
    public static long longestWindowSeconds(String name, RateLimitsConfig.Rule rule) {
        return Math.max(rule.longestWindowSeconds(),
                RateLimitsConfig.longestMetaWindow(CustomPerm.configManager.getGrades(), RateLimitsConfig.metaKey(name),
                        rule.windowSeconds));
    }
}
