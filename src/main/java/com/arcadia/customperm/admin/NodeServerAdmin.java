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
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.ServerScope;
import com.arcadia.customperm.perm.Contexts;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a grade or a player says about one node on one cluster member: allowed there, denied there, or nothing,
 * following their other entries. Stored as the node limited to {@code server=<name>}, so it ranks like any
 * contextual entry: the player's own above their grades, the heaviest grade first, and above the same holder's
 * node held everywhere. On an exposed command limited to other members, such an entry is also what opens the
 * command there for them.
 */
public final class NodeServerAdmin {

    public static final String ALLOW = "allow";
    public static final String DENY = "deny";
    public static final String INHERIT = "inherit";
    /** Typed in place of a server name: the entry held everywhere, without a context. */
    public static final String EVERYWHERE = "*";

    private NodeServerAdmin() {
    }

    public static AdminResult forGrade(MinecraftServer server, String grade, String node, String serverName, String state) {
        AdminResult refusal = check(serverName, state);
        if (refusal != null) return refusal;
        GradesConfig.Grade holder = CustomPerm.configManager.getGrades().grades.get(grade);
        if (holder == null) return AdminResult.fail("No such grade: " + grade);
        String name = serverName.trim().toLowerCase(Locale.ROOT);
        String context = context(name);
        GradesConfig.Scoped scope = context == null ? null : holder.contexts.get(context);
        java.util.Set<String> allowed = context == null ? holder.permissions : scope == null ? null : scope.permissions;
        java.util.Set<String> denied = context == null ? holder.deniedPermissions : scope == null ? null : scope.deniedPermissions;
        AdminResult result = null;
        if (allowed != null && allowed.contains(node) && !state.equals(ALLOW)) {
            result = GradeAdmin.removeNode(server, grade, node, false, context);
        }
        if (denied != null && denied.contains(node) && !state.equals(DENY)) {
            result = GradeAdmin.removeNode(server, grade, node, true, context);
        }
        if (result != null && !result.success()) return result;
        if (!state.equals(INHERIT)) {
            result = GradeAdmin.addNode(server, grade, node, state.equals(DENY), 0, context);
            if (!result.success()) return result;
        }
        return outcome(node, grade, name, state, result);
    }

    public static AdminResult forPlayer(MinecraftServer server, UUID player, String displayName, String node,
                                        String serverName, String state) {
        AdminResult refusal = check(serverName, state);
        if (refusal != null) return refusal;
        String name = serverName.trim().toLowerCase(Locale.ROOT);
        String context = context(name);
        GradesConfig config = CustomPerm.configManager.getGrades();
        java.util.Set<String> allowed;
        java.util.Set<String> denied;
        if (context == null) {
            allowed = config.userPermissions.get(player.toString());
            denied = config.userDeniedPermissions.get(player.toString());
        } else {
            Map<String, GradesConfig.UserScoped> scopes = config.userContexts.get(player.toString());
            GradesConfig.UserScoped scope = scopes == null ? null : scopes.get(context);
            allowed = scope == null ? null : scope.permissions;
            denied = scope == null ? null : scope.deniedPermissions;
        }
        AdminResult result = null;
        if (allowed != null && allowed.contains(node) && !state.equals(ALLOW)) {
            result = UserAdmin.removeNode(server, player, displayName, node, false, context);
        }
        if (denied != null && denied.contains(node) && !state.equals(DENY)) {
            result = UserAdmin.removeNode(server, player, displayName, node, true, context);
        }
        if (result != null && !result.success()) return result;
        if (!state.equals(INHERIT)) {
            result = UserAdmin.addNode(server, player, displayName, node, state.equals(DENY), 0, context);
            if (!result.success()) return result;
        }
        return outcome(node, displayName, name, state, result);
    }

    /** The context of the entry for {@code name}: none for {@link #EVERYWHERE}, {@code server=<name>} otherwise. */
    private static String context(String name) {
        return name.equals(EVERYWHERE) ? null : Contexts.SERVER + "=" + name;
    }

    /**
     * One grade or player with something to say about a node: held everywhere or not, and on each server.
     *
     * @param id         the grade's name, or the player's UUID
     * @param everywhere {@code allow}, {@code deny}, or empty when their entry held everywhere says nothing
     * @param servers    server name to {@code allow} or {@code deny}, for an entry limited to exactly that server
     */
    public record Holder(boolean player, String id, String label, String everywhere, Map<String, String> servers) {
    }

    /**
     * Every grade and player whose entries name {@code node}, held everywhere or limited to one server: who decides
     * where for a command. Grades first, then players, each sorted.
     */
    public static java.util.List<Holder> holders(MinecraftServer server, String node) {
        GradesConfig config = CustomPerm.configManager.getGrades();
        java.util.List<Holder> out = new java.util.ArrayList<>();
        new java.util.TreeMap<>(config.grades).forEach((name, grade) -> {
            Map<String, String> servers = new java.util.TreeMap<>();
            grade.contexts.forEach((context, scope) -> state(servers, context, scope.permissions, scope.deniedPermissions, node));
            String everywhere = state(grade.permissions, grade.deniedPermissions, node);
            if (!everywhere.isEmpty() || !servers.isEmpty()) out.add(new Holder(false, name, name, everywhere, servers));
        });
        java.util.Set<String> uuids = new java.util.TreeSet<>(config.userPermissions.keySet());
        uuids.addAll(config.userDeniedPermissions.keySet());
        uuids.addAll(config.userContexts.keySet());
        java.util.List<Holder> players = new java.util.ArrayList<>();
        for (String uuid : uuids) {
            UUID id;
            try {
                id = UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Map<String, String> servers = new java.util.TreeMap<>();
            Map<String, GradesConfig.UserScoped> scopes = config.userContexts.get(uuid);
            if (scopes != null) {
                scopes.forEach((context, scope) -> state(servers, context, scope.permissions, scope.deniedPermissions, node));
            }
            String everywhere = state(config.userPermissions.get(uuid), config.userDeniedPermissions.get(uuid), node);
            if (everywhere.isEmpty() && servers.isEmpty()) continue;
            players.add(new Holder(true, uuid, server == null ? uuid : GradeAdmin.displayName(server, id), everywhere, servers));
        }
        players.sort(java.util.Comparator.comparing(Holder::label, String.CASE_INSENSITIVE_ORDER));
        out.addAll(players);
        return out;
    }

    private static void state(Map<String, String> servers, String context, java.util.Set<String> allowed,
                              java.util.Set<String> denied, String node) {
        if (!context.startsWith(Contexts.SERVER + "=") || context.contains(",")) return;
        String value = state(allowed, denied, node);
        if (!value.isEmpty()) servers.put(context.substring(Contexts.SERVER.length() + 1), value);
    }

    private static String state(java.util.Set<String> allowed, java.util.Set<String> denied, String node) {
        if (denied != null && denied.contains(node)) return DENY;
        if (allowed != null && allowed.contains(node)) return ALLOW;
        return "";
    }

    private static AdminResult check(String serverName, String state) {
        String problem = EVERYWHERE.equals(serverName == null ? null : serverName.trim()) ? null : ServerScope.problem(serverName);
        if (problem != null) return AdminResult.fail(problem);
        if (!state.equals(ALLOW) && !state.equals(DENY) && !state.equals(INHERIT)) {
            return AdminResult.fail("Unknown state '" + state + "': allow, deny or inherit.");
        }
        return null;
    }

    /** One line whatever it took, the warnings of the last change kept. */
    private static AdminResult outcome(String node, String holder, String server, String state, AdminResult last) {
        String where = server.equals(EVERYWHERE) ? "everywhere" : "on " + server;
        String what = switch (state) {
            case ALLOW -> "allowed to " + holder + " " + where;
            case DENY -> "denied to " + holder + " " + where;
            default -> server.equals(EVERYWHERE) ? "no longer set for " + holder + " everywhere"
                    : "follows " + holder + "'s other entries on " + server;
        };
        AdminResult result = AdminResult.ok(node + " is now " + what + ".");
        if (last != null) for (String warning : last.warnings()) result = result.warn(warning);
        return result;
    }
}
