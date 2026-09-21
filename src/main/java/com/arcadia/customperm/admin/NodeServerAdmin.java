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

    private NodeServerAdmin() {
    }

    public static AdminResult forGrade(MinecraftServer server, String grade, String node, String serverName, String state) {
        AdminResult refusal = check(serverName, state);
        if (refusal != null) return refusal;
        GradesConfig.Grade holder = CustomPerm.configManager.getGrades().grades.get(grade);
        if (holder == null) return AdminResult.fail("No such grade: " + grade);
        String name = serverName.trim().toLowerCase(Locale.ROOT);
        String context = Contexts.SERVER + "=" + name;
        GradesConfig.GradeScoped scope = holder.contexts.get(context);
        AdminResult result = null;
        if (scope != null && scope.permissions.contains(node) && !state.equals(ALLOW)) {
            result = GradeAdmin.removeNode(server, grade, node, false, context);
        }
        if (scope != null && scope.deniedPermissions.contains(node) && !state.equals(DENY)) {
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
        String context = Contexts.SERVER + "=" + name;
        Map<String, GradesConfig.UserScoped> scopes = CustomPerm.configManager.getGrades().userContexts.get(player.toString());
        GradesConfig.UserScoped scope = scopes == null ? null : scopes.get(context);
        AdminResult result = null;
        if (scope != null && scope.permissions.contains(node) && !state.equals(ALLOW)) {
            result = UserAdmin.removeNode(server, player, displayName, node, false, context);
        }
        if (scope != null && scope.deniedPermissions.contains(node) && !state.equals(DENY)) {
            result = UserAdmin.removeNode(server, player, displayName, node, true, context);
        }
        if (result != null && !result.success()) return result;
        if (!state.equals(INHERIT)) {
            result = UserAdmin.addNode(server, player, displayName, node, state.equals(DENY), 0, context);
            if (!result.success()) return result;
        }
        return outcome(node, displayName, name, state, result);
    }

    private static AdminResult check(String serverName, String state) {
        String problem = ServerScope.problem(serverName);
        if (problem != null) return AdminResult.fail(problem);
        if (!state.equals(ALLOW) && !state.equals(DENY) && !state.equals(INHERIT)) {
            return AdminResult.fail("Unknown state '" + state + "': allow, deny or inherit.");
        }
        return null;
    }

    /** One line whatever it took, the warnings of the last change kept. */
    private static AdminResult outcome(String node, String holder, String server, String state, AdminResult last) {
        String what = switch (state) {
            case ALLOW -> "allowed to " + holder + " on " + server;
            case DENY -> "denied to " + holder + " on " + server;
            default -> "follows " + holder + "'s other entries on " + server;
        };
        AdminResult result = AdminResult.ok(node + " is now " + what + ".");
        if (last != null) for (String warning : last.warnings()) result = result.warn(warning);
        return result;
    }
}
