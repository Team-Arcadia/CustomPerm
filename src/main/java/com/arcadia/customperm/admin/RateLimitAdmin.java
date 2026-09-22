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
import com.arcadia.customperm.cluster.Cluster;
import com.arcadia.customperm.command.RateLimits;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.config.ServerScope;
import com.arcadia.customperm.perm.Expiry;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
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
        // Redefining the numbers changes the numbers only: what else the admin chose for the rule stays.
        RateLimitsConfig.Rule previous = rules().get(name);
        if (previous != null) {
            rule.persistence = previous.persistence;
            rule.scope = previous.scope;
            rule.servers = previous.servers;
            rule.perServer = previous.perServer;
        }
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

    /** Who shares the rule's budget in cluster mode: server, network, or server names joined with a comma. */
    public static AdminResult setScope(String name, String rawScope) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        String scope = RateLimitsConfig.normalizeScope(rawScope);
        if (scope == null) {
            return AdminResult.fail("Unknown scope '" + rawScope.trim() + "'. Use server (each server counts its own), network "
                    + "(one budget for the whole cluster) or server names joined with a comma, such as hub,survival.");
        }
        List<String> sharing = rule.perServer == null ? List.of()
                : RateLimitsConfig.Rule.sharingUnder(scope, rule.perServer.keySet());
        if (!sharing.isEmpty()) {
            return AdminResult.fail("A shared counter means one limit: " + String.join(", ", sharing) + " "
                    + (sharing.size() == 1 ? "has a limit of its" : "have a limit of their") + " own for /" + name
                    + ". Remove it first with /customperm ratelimit server " + name + " <server> clear.");
        }
        rule.scope = scope;
        String what = switch (scope) {
            case RateLimitsConfig.SCOPE_SERVER -> "counted by each server on its own.";
            case RateLimitsConfig.SCOPE_NETWORK -> "counted once for every server of the cluster.";
            default -> "counted once across " + scope.replace(",", ", ") + "; the other servers count on their own.";
        };
        AdminResult result = AdminResult.ok("Uses of /" + name + " are now " + what).warn(ConfigAdmin.persist());
        if (!RateLimitsConfig.SCOPE_SERVER.equals(scope) && !com.arcadia.customperm.cluster.Cluster.running()) {
            result = result.note("This server is in no cluster: it counts its own uses until cluster mode runs.");
        }
        return result;
    }

    /**
     * A limit of its own for one cluster member. Refused where that member shares its counter: one budget, one
     * limit. {@code here} names the server typed on.
     */
    public static AdminResult setOnServer(String name, String rawServer, int max, int windowSeconds) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        String server = serverName(rawServer);
        if (server == null) return badServer(rawServer);
        if (max < 1 || windowSeconds < 1) {
            return AdminResult.fail("A rate limit needs at least 1 use per window of at least 1 second.");
        }
        if (!RateLimitsConfig.Rule.sharingUnder(rule.scope, List.of(server)).isEmpty()) {
            return AdminResult.fail(server + " shares the counter of /" + name + " (scope " + rule.scope
                    + "), and a shared counter means one limit. Set the scope to server first, or change the limit of "
                    + "every member with /customperm ratelimit set.");
        }
        if (rule.perServer == null) rule.perServer = new TreeMap<>();
        RateLimitsConfig.Limit limit = new RateLimitsConfig.Limit(max, windowSeconds);
        RateLimitsConfig.Limit before = rule.perServer.put(server, limit);
        if (limit.equals(before)) return AdminResult.ok("/" + name + " is already " + limit + " on " + server + " — no change.");
        AdminResult result = AdminResult.ok("/" + name + " is now " + limit + " on " + server + ", "
                + new RateLimitsConfig.Limit(rule.maxExecutions, rule.windowSeconds) + " elsewhere.").warn(ConfigAdmin.persist());
        if (!rule.appliesHere(server)) {
            result = result.warn("The rule is not active on " + server + ": its list of servers leaves it out.");
        }
        return result;
    }

    /** Takes a member's own limit away: it follows the rule's numbers again. */
    public static AdminResult clearOnServer(String name, String rawServer) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, false);
        String server = serverName(rawServer);
        if (server == null) return badServer(rawServer);
        if (rule.perServer == null || rule.perServer.remove(server) == null) {
            return AdminResult.ok(server + " has no limit of its own for /" + name + " — no change.");
        }
        if (rule.perServer.isEmpty()) rule.perServer = null;
        return AdminResult.ok(server + " follows the limit of /" + name + " again: "
                + new RateLimitsConfig.Limit(rule.maxExecutions, rule.windowSeconds) + ".").warn(ConfigAdmin.persist());
    }

    private static String serverName(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase(ServerScope.HERE)) return Cluster.identity();
        return ServerScope.problem(text) == null ? text.toLowerCase(Locale.ROOT) : null;
    }

    private static AdminResult badServer(String raw) {
        return AdminResult.fail(raw != null && raw.trim().equalsIgnoreCase(ServerScope.HERE)
                ? "'here' names this server in a cluster, and it has no cluster name."
                : ServerScope.problem(raw));
    }

    /** Why {@code value} cannot be a grade's or a player's limit on {@code /name}, or null. */
    public static String holderValueProblem(String name, String value) {
        if (!NAME.matcher(name).matches()) return "Invalid command name '" + name + "'.";
        if (MetaAdmin.problem(RateLimitsConfig.metaKey(name), "x") != null) {
            return "/" + name + " cannot carry a limit per grade or player: its name holds a character a meta key cannot.";
        }
        RateLimitsConfig.Rule rule = rules().get(name);
        if (RateLimitsConfig.parseLimit(value, rule == null ? 1 : rule.windowSeconds) == null) {
            return "'" + value + "' is not a limit. Write uses per window, such as 10/1h, 10/30m or 10/90s, a number "
                    + "alone for the rule's own window, or unlimited.";
        }
        return null;
    }

    /** What a grade's or a player's limit is stored as: the meta value, lowercased. */
    public static String holderValue(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Everything that decides {@code /name}: the rule, its servers, and the grades and players with a limit of their
     * own. The limit one online player gets is {@link #effective}.
     */
    public static AdminResult describe(MinecraftServer server, String name) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        String counted = switch (rule.scope) {
            case RateLimitsConfig.SCOPE_SERVER -> "by each server alone";
            case RateLimitsConfig.SCOPE_NETWORK -> "once across the cluster";
            default -> "once across " + rule.scope.replace(",", ", ");
        };
        AdminResult result = AdminResult.ok("/" + name + ": " + new RateLimitsConfig.Limit(rule.maxExecutions, rule.windowSeconds)
                + (rule.enabled ? "" : " (disabled)") + ", active on "
                + (rule.servers == null ? "every member" : String.join(", ", rule.servers)) + ", counted " + counted + ".");
        if (rule.perServer != null) {
            List<String> own = new ArrayList<>();
            rule.perServer.forEach((member, limit) -> own.add(member + " " + limit));
            result = result.note("Per server: " + String.join(", ", own) + ".");
        }
        if (CustomPerm.isLuckPermsActive()) {
            return result.note("Grades and players: in LuckPerms, as the meta " + RateLimitsConfig.metaKey(name)
                    + ", e.g. lp group vip meta set " + RateLimitsConfig.metaKey(name) + " 10/1h");
        }
        List<String> grades = holders(name, true, server);
        List<String> players = holders(name, false, server);
        return result.note(grades.isEmpty() ? "No grade has a limit of its own." : "Grades: " + String.join(", ", grades) + ".")
                .note(players.isEmpty() ? "No player has a limit of their own." : "Players: " + String.join(", ", players) + ".");
    }

    /** The limit {@code player} gets on {@code /name} on this server, and where it comes from. */
    public static AdminResult effective(ServerPlayer player, String name) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        RateLimitsConfig.Limit limit = RateLimits.limitFor(player, name);
        String who = player.getGameProfile().getName();
        if (limit == null) {
            return AdminResult.ok("/" + name + " is not limited for " + who + " here: the rule is "
                    + (rule.enabled ? "not active on this server." : "disabled."));
        }
        String here = Cluster.identity();
        String meta = PermissionService.get().meta(player, RateLimitsConfig.metaKey(name));
        String from;
        if (RateLimitsConfig.parseLimit(meta, rule.limitOn(here).windowSeconds) != null) {
            from = "their grades or their own value (meta " + RateLimitsConfig.metaKey(name) + "=" + meta + ")";
        } else if (here != null && rule.perServer != null && rule.perServer.containsKey(here) && !rule.shared(here)) {
            from = "this server's own limit";
        } else {
            from = "the rule";
        }
        return AdminResult.ok(who + " gets " + limit + " on /" + name + " here, from " + from + ".");
    }

    /** Who a level of a limit belongs to. */
    public enum LevelKind { SERVER, GRADE, PLAYER }

    /**
     * One level of a limit, for the interface: a member's own limit, or a grade's or a player's value.
     *
     * @param holder      the server, the grade, or the player's name
     * @param context     where a grade's or a player's value applies, empty for everywhere
     * @param secondsLeft time left of a temporary value, 0 when it does not expire
     */
    public record Level(LevelKind kind, String holder, String value, String context, long secondsLeft) {
    }

    /**
     * Sets one level of {@code /name}, the interface's entry point: a member's own limit ({@code value} uses per
     * window), or a grade's or a player's value ({@code 10/1h}, {@code 10} or {@code unlimited}), in {@code context}.
     */
    public static AdminResult setLevel(MinecraftServer server, String name, LevelKind kind, String holder, String value,
                                       String context) {
        RateLimitsConfig.Rule rule = rules().get(name);
        if (rule == null) return noRule(name, true);
        String ctx = context == null || context.isBlank() ? null : context.trim();
        if (kind == LevelKind.SERVER) {
            if (ctx != null) return AdminResult.fail("A server's own limit has no context: the server is where it applies.");
            RateLimitsConfig.Limit limit = RateLimitsConfig.parseLimit(value, rule.windowSeconds);
            if (limit == null || limit.unlimited()) {
                return AdminResult.fail("A server's own limit is uses per window, such as 10/1h. Unlimited is for a grade or a player.");
            }
            return setOnServer(name, holder, limit.maxExecutions, limit.windowSeconds);
        }
        String problem = holderValueProblem(name, value);
        if (problem != null) return AdminResult.fail(problem);
        String key = RateLimitsConfig.metaKey(name);
        if (kind == LevelKind.GRADE) return MetaAdmin.setOnGrade(server, holder, key, holderValue(value), 0, ctx);
        GradeAdmin.Resolution who = GradeAdmin.resolvePlayer(server, holder);
        if (who.profile().isEmpty()) return AdminResult.fail(who.problem());
        return MetaAdmin.setOnPlayer(server, who.profile().get().getId(), who.profile().get().getName(), key,
                holderValue(value), 0, ctx);
    }

    /** Removes one level of {@code /name}: the holder follows the level below again. */
    public static AdminResult clearLevel(MinecraftServer server, String name, LevelKind kind, String holder, String context) {
        if (kind == LevelKind.SERVER) return clearOnServer(name, holder);
        String ctx = context == null || context.isBlank() ? null : context.trim();
        String key = RateLimitsConfig.metaKey(name);
        if (kind == LevelKind.GRADE) return MetaAdmin.unsetOnGrade(server, holder, key, ctx);
        GradeAdmin.Resolution who = GradeAdmin.resolvePlayer(server, holder);
        if (who.profile().isEmpty()) return AdminResult.fail(who.problem());
        return MetaAdmin.unsetOnPlayer(server, who.profile().get().getId(), who.profile().get().getName(), key, ctx);
    }

    /**
     * Every level of {@code /name}: the members with a limit of their own, then the grades and the players with a
     * value in the grades file. Under LuckPerms the grades' and players' values live there and are not listed.
     */
    public static List<Level> levels(MinecraftServer server, String name) {
        RateLimitsConfig.Rule rule = rules().get(name);
        List<Level> out = new ArrayList<>();
        if (rule == null) return out;
        if (rule.perServer != null) {
            rule.perServer.forEach((member, limit) -> out.add(new Level(LevelKind.SERVER, member, limit.toString(), "", 0)));
        }
        if (CustomPerm.isLuckPermsActive()) return out;
        String key = RateLimitsConfig.metaKey(name);
        GradesConfig config = CustomPerm.configManager.getGrades();
        long now = Expiry.now();
        new TreeMap<>(config.grades).forEach((grade, holder) -> {
            level(out, LevelKind.GRADE, grade, holder.meta.get(key), holder.metaExpiries.get(key), "", now);
            new TreeMap<>(holder.contexts).forEach((context, scope) ->
                    level(out, LevelKind.GRADE, grade, scope.meta.get(key), scope.metaExpiries.get(key), context, now));
        });
        Set<String> uuids = new TreeSet<>(config.userMeta.keySet());
        uuids.addAll(config.userContexts.keySet());
        for (String uuid : uuids) {
            String who;
            try {
                who = server == null ? uuid : GradeAdmin.displayName(server, UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, String> meta = config.userMeta.get(uuid);
            Map<String, Long> expiries = config.userMetaExpiries.get(uuid);
            if (meta != null) level(out, LevelKind.PLAYER, who, meta.get(key), expiries == null ? null : expiries.get(key), "", now);
            Map<String, GradesConfig.UserScoped> scopes = config.userContexts.get(uuid);
            if (scopes != null) {
                new TreeMap<>(scopes).forEach((context, scope) ->
                        level(out, LevelKind.PLAYER, who, scope.meta.get(key), scope.metaExpiries.get(key), context, now));
            }
        }
        return out;
    }

    private static void level(List<Level> out, LevelKind kind, String holder, String value, Long expiry, String context,
                              long now) {
        if (value == null || expiry != null && expiry <= now) return;
        out.add(new Level(kind, holder, value, context, expiry == null ? 0 : Math.max(1, (expiry - now) / 1000)));
    }

    /** Grade or player entries of the meta for {@code /name}: {@code vip 10/1h}, {@code vip 20/1h (server=hub)}. */
    private static List<String> holders(String name, boolean grades, MinecraftServer server) {
        String key = RateLimitsConfig.metaKey(name);
        GradesConfig config = CustomPerm.configManager.getGrades();
        List<String> out = new ArrayList<>();
        if (grades) {
            new TreeMap<>(config.grades).forEach((grade, holder) -> {
                entry(out, grade, holder.meta.get(key), holder.metaExpiries.get(key), "");
                new TreeMap<>(holder.contexts).forEach((context, scope) ->
                        entry(out, grade, scope.meta.get(key), scope.metaExpiries.get(key), context));
            });
            return out;
        }
        Set<String> uuids = new TreeSet<>(config.userMeta.keySet());
        uuids.addAll(config.userContexts.keySet());
        for (String uuid : uuids) {
            String who;
            try {
                who = server == null ? uuid : GradeAdmin.displayName(server, UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, String> meta = config.userMeta.get(uuid);
            Map<String, Long> expiries = config.userMetaExpiries.get(uuid);
            if (meta != null) entry(out, who, meta.get(key), expiries == null ? null : expiries.get(key), "");
            Map<String, GradesConfig.UserScoped> scopes = config.userContexts.get(uuid);
            if (scopes != null) {
                new TreeMap<>(scopes).forEach((context, scope) ->
                        entry(out, who, scope.meta.get(key), scope.metaExpiries.get(key), context));
            }
        }
        return out;
    }

    private static void entry(List<String> out, String holder, String value, Long expiry, String context) {
        if (value == null) return;
        long now = Expiry.now();
        if (expiry != null && expiry <= now) return;
        out.add(holder + " " + value + (context.isEmpty() ? "" : " (" + context + ")")
                + (expiry == null ? "" : ", " + Expiry.describe(expiry - now) + " left"));
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
