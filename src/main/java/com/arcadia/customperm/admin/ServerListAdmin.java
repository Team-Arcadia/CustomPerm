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
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.config.ServerScope;
import com.arcadia.customperm.config.SettingsConfig;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * Which cluster members an exposed command, an alias or a rate limit is active on: the {@code servers} list of
 * {@link ServerScope}, set by the text commands and the interface alike.
 *
 * <p>The answer says what the list does on this server and warns about the two ways it can be misread: a part
 * this member does not share, where the list never reaches the others, and a name no member answers to now,
 * which is kept on purpose.
 */
public final class ServerListAdmin {

    /** What carries a list. */
    public enum Kind {
        COMMAND("exposed commands", "commands"),
        ALIAS("aliases", "aliases"),
        RATE_LIMIT("rate limits", "rateLimits");

        private final String plural;
        private final String shareKey;

        Kind(String plural, String shareKey) {
            this.plural = plural;
            this.shareKey = shareKey;
        }

        /** Whether this member follows the cluster for this part. */
        boolean shared(SettingsConfig.Share share) {
            return switch (this) {
                case COMMAND -> share.commands;
                case ALIAS -> share.aliases;
                case RATE_LIMIT -> share.rateLimits;
            };
        }
    }

    private ServerListAdmin() {
    }

    /** The members {@code name} is active on; null when no such element exists. */
    public static List<String> servers(Kind kind, String name) {
        var config = CustomPerm.configManager;
        return switch (kind) {
            case COMMAND -> config.getCommands().grantedCommands.contains(name) ? config.getCommands().servers(name) : null;
            case ALIAS -> config.getAliases().aliases.containsKey(name) ? config.getAliases().servers(name) : null;
            case RATE_LIMIT -> {
                RateLimitsConfig.Rule rule = config.getRateLimits().rules.get(name);
                yield rule == null ? null : rule.servers == null ? List.of() : rule.servers;
            }
        };
    }

    /** Whether {@code name} is active on this server, by its list alone. */
    public static boolean activeHere(Kind kind, String name) {
        List<String> servers = servers(kind, name);
        return servers != null && ServerScope.appliesHere(servers, Cluster.identity());
    }

    /** {@code  [hub, survival]} for a list, empty for every member, and a mark when that leaves this server out. */
    public static String label(Kind kind, String name) {
        List<String> servers = servers(kind, name);
        if (servers == null || servers.isEmpty()) return "";
        return "  [" + String.join(", ", servers) + "]" + (activeHere(kind, name) ? "" : " (not on this server)");
    }

    public static AdminResult show(Kind kind, String name) {
        List<String> servers = servers(kind, name);
        if (servers == null) return missing(kind, name);
        String here = Cluster.identity();
        String where = servers.isEmpty() ? "every member" : String.join(", ", servers);
        AdminResult result = AdminResult.ok(subject(kind, name) + " is active on " + where + ".");
        if (here == null) return result.note("This server has no cluster name: every list is ignored here.");
        return result.note("This server is " + here + (ServerScope.appliesHere(servers, here) ? ", and it is active here." : ", and it is not active here."));
    }

    /** Replaces the list of {@code name} with {@code raw}: names separated by spaces or commas, {@code here}, or {@code all}. */
    public static AdminResult set(MinecraftServer server, Kind kind, String name, String raw) {
        List<String> before = servers(kind, name);
        if (before == null) return missing(kind, name);
        String here = Cluster.identity();
        ServerScope.Parsed parsed = ServerScope.parse(raw, here);
        if (parsed.problem() != null) return AdminResult.fail(parsed.problem());
        List<String> after = parsed.names() == null ? List.of() : parsed.names();
        if (after.equals(before)) {
            return AdminResult.ok(subject(kind, name) + " is already active on "
                    + (after.isEmpty() ? "every member" : String.join(", ", after)) + ". No change.");
        }
        store(kind, name, parsed.names());
        String warning = ConfigAdmin.persist();
        apply(server, kind, name);

        AdminResult result = AdminResult.ok(subject(kind, name) + " is now active on "
                + (after.isEmpty() ? "every member." : String.join(", ", after) + " only.")).warn(warning);
        var cluster = CustomPerm.configManager.getSettings().cluster;
        if (here == null) {
            return result.note("This server has no cluster name: the list is stored, and read once it runs in a cluster.");
        }
        if (!kind.shared(cluster.share)) {
            result = result.warn("This server does not share its " + kind.plural + " (share." + kind.shareKey
                    + " is false in settings.json): the list stays in its own file and reaches no other member.");
        }
        if (Cluster.running()) {
            List<String> members = Cluster.memberNames();
            List<String> unknown = after.stream().filter(n -> !members.contains(n)).toList();
            if (!unknown.isEmpty()) {
                result = result.warn(String.join(", ", unknown) + (unknown.size() == 1 ? " is" : " are")
                        + " not a member heard right now. Kept: it applies once that server runs.");
            }
        }
        return result.note(ServerScope.appliesHere(after, here)
                ? "Active on this server (" + here + ")."
                : "Not active on this server (" + here + ").");
    }

    private static void store(Kind kind, String name, List<String> servers) {
        var config = CustomPerm.configManager;
        switch (kind) {
            case COMMAND -> {
                if (servers == null) config.getCommands().commandServers.remove(name);
                else config.getCommands().commandServers.put(name, servers);
            }
            case ALIAS -> {
                if (servers == null) config.getAliases().aliasServers.remove(name);
                else config.getAliases().aliasServers.put(name, servers);
            }
            case RATE_LIMIT -> config.getRateLimits().rules.get(name).servers = servers;
        }
    }

    /** Brings this server in step: gates are read live, aliases are registered, rules are read at each use. */
    private static void apply(MinecraftServer server, Kind kind, String name) {
        switch (kind) {
            case COMMAND -> {
                CommandTreeRewriter.reassertExposedCommands(server);
                ConfigAdmin.resyncCommands(server);
            }
            case ALIAS -> AliasAdmin.refresh(server, name);
            case RATE_LIMIT -> {
            }
        }
    }

    private static String subject(Kind kind, String name) {
        return switch (kind) {
            case COMMAND -> "Exposed command /" + name;
            case ALIAS -> "Alias /" + name;
            case RATE_LIMIT -> "The rate limit on /" + name;
        };
    }

    private static AdminResult missing(Kind kind, String name) {
        return AdminResult.fail(switch (kind) {
            case COMMAND -> "Command /" + name + " is not currently exposed.";
            case ALIAS -> "Alias /" + name + " does not exist.";
            case RATE_LIMIT -> "No rate limit on /" + name + ".";
        });
    }
}
