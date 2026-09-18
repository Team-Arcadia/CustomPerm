/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm.lp;

import com.arcadia.customperm.admin.ImportPlan;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads a LuckPerms installation into an {@link ImportPlan}. Like {@code LuckPermsAdminService} this
 * class mentions {@code net.luckperms.api} types, so callers must reach it only from inside a
 * {@code CustomPerm.isLuckPermsActive()} branch and never through a method reference, which would
 * resolve its target eagerly and drag LuckPerms onto a server that does not have it.
 *
 * <p><strong>Nothing is written here.</strong> Reading and applying are separate on purpose: the admin
 * reads the whole report, including what is left behind, before a single grade exists.
 *
 * <p><strong>Every user, not only the loaded ones.</strong> Users come from a handful of {@code searchAll}
 * calls rather than one {@code loadUser} per account: LuckPerms answers each from storage in one query,
 * where loading every user of a large server one by one would not be something to run from a button.
 *
 * <p><strong>Threading.</strong> Everything LuckPerms answers is asynchronous and stays that way: the
 * method returns a future and the caller hops back onto the server thread before touching any config.
 */
public final class LuckPermsImport {

    private LuckPermsImport() {
    }

    /**
     * Builds the plan. Never completes exceptionally: a failure to reach LuckPerms yields an empty plan
     * carrying the reason, which the admin reads instead of a stack trace.
     *
     * @param exposeCommands whether a translated {@code minecraft.command.<x>} node also exposes {@code <x>}
     */
    public static CompletableFuture<ImportPlan> read(boolean exposeCommands) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            ImportPlan.Builder plan = new ImportPlan.Builder();
            return api.getGroupManager().loadAllGroups()
                    .thenApply(ignored -> readGroups(api, plan, exposeCommands))
                    .thenCompose(ignored -> readUsers(api, plan, exposeCommands))
                    .thenApply(ignored -> plan.build())
                    .exceptionally(error -> failed(error.getMessage()));
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            return CompletableFuture.completedFuture(failed(t.getMessage()));
        }
    }

    private static ImportPlan failed(String reason) {
        ImportPlan.Builder plan = new ImportPlan.Builder();
        plan.note("LuckPerms could not be read: " + reason + ". Nothing was changed.");
        return plan.build();
    }

    // ------------------------------------------------------------------ groups

    private static Void readGroups(LuckPerms api, ImportPlan.Builder plan, boolean exposeCommands) {
        for (Group group : api.getGroupManager().getLoadedGroups()) {
            List<String> parents = new ArrayList<>();
            List<String> deniedParents = new ArrayList<>();
            Set<String> allow = new LinkedHashSet<>();
            Set<String> deny = new LinkedHashSet<>();
            readNodes(group.getNodes(), plan, exposeCommands, parents, deniedParents, allow, deny,
                    "group " + group.getName());
            plan.grade(new ImportPlan.Grade(group.getName(), group.getWeight().orElse(0),
                    List.copyOf(parents), List.copyOf(deniedParents), Set.copyOf(allow), Set.copyOf(deny)));
        }
        return null;
    }

    // ------------------------------------------------------------------ users

    private static CompletableFuture<Void> readUsers(LuckPerms api, ImportPlan.Builder plan,
                                                     boolean exposeCommands) {
        // One search per family of keys rather than one per user. LuckPerms answers each from storage in a
        // single query, where loading every account one by one is not something to run from a button. It has
        // no matcher for permission nodes as a type, a permission key being any string, so the families that
        // can be imported are named instead: what is left is what CustomPerm would not have read anyway.
        CompletableFuture<Map<UUID, Collection<InheritanceNode>>> inherited =
                api.getUserManager().searchAll(NodeMatcher.type(NodeType.INHERITANCE));
        List<CompletableFuture<Map<UUID, Collection<Node>>>> permissions = List.of(
                api.getUserManager().searchAll(NodeMatcher.keyStartsWith("customperm.")),
                api.getUserManager().searchAll(NodeMatcher.keyStartsWith("minecraft.command.")),
                api.getUserManager().searchAll(NodeMatcher.key("*")));

        CompletableFuture<?>[] all = new CompletableFuture<?>[permissions.size() + 1];
        all[0] = inherited;
        for (int i = 0; i < permissions.size(); i++) all[i + 1] = permissions.get(i);

        return CompletableFuture.allOf(all).thenApply(ignored -> {
            Map<UUID, List<Node>> byUser = new LinkedHashMap<>();
            inherited.join().forEach((uuid, nodes) -> byUser.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(nodes));
            for (CompletableFuture<Map<UUID, Collection<Node>>> search : permissions) {
                search.join().forEach((uuid, nodes) ->
                        byUser.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(nodes));
            }
            plan.note("On players, only what CustomPerm can read is looked at: their groups and their "
                    + "customperm, minecraft.command and * nodes.");

            for (Map.Entry<UUID, List<Node>> user : byUser.entrySet()) {
                UUID uuid = user.getKey();
                List<String> grades = new ArrayList<>();
                List<String> deniedGrades = new ArrayList<>();
                Set<String> allow = new LinkedHashSet<>();
                Set<String> deny = new LinkedHashSet<>();
                readNodes(user.getValue(), plan, exposeCommands, grades, deniedGrades, allow, deny,
                        "player " + uuid);
                if (grades.isEmpty() && deniedGrades.isEmpty() && allow.isEmpty() && deny.isEmpty()) continue;
                plan.player(new ImportPlan.Player(uuid.toString(), name(api, uuid), List.copyOf(grades),
                        List.copyOf(deniedGrades), Set.copyOf(allow), Set.copyOf(deny)));
            }
            return null;
        });
    }

    /** The username LuckPerms knows, or the UUID: this is a label for the report, never a key. */
    private static String name(LuckPerms api, UUID uuid) {
        var user = api.getUserManager().getUser(uuid);
        String username = user == null ? null : user.getUsername();
        return username == null ? uuid.toString() : username;
    }

    // ------------------------------------------------------------------ nodes

    /**
     * Sorts one holder's nodes into what CustomPerm keeps and what it leaves behind. A node is left
     * behind when it would not mean here what it means there: a temporary node imported as permanent
     * would over-grant, a contextual one imported as global would grant everywhere, and a node another
     * mod reads would sit in the file granting nothing.
     */
    private static void readNodes(Collection<? extends Node> nodes, ImportPlan.Builder plan,
                                  boolean exposeCommands, List<String> parents, List<String> deniedParents,
                                  Set<String> allow, Set<String> deny, String holder) {
        for (Node node : nodes) {
            if (node.getExpiry() != null) {
                plan.temporary();
                plan.note("Temporary entries are not imported, nothing here expires: " + holder + ".");
                continue;
            }
            if (!node.getContexts().isEmpty()) {
                plan.contextual();
                plan.note("Contextual entries are not imported, a node here applies everywhere: " + holder + ".");
                continue;
            }
            if (node instanceof InheritanceNode inheritance) {
                (node.getValue() ? parents : deniedParents).add(inheritance.getGroupName());
                plan.imported(false);
                continue;
            }
            if (!NodeType.PERMISSION.matches(node)) {
                plan.other();
                plan.note("Prefixes, suffixes, meta and display names have no equivalent and are not imported.");
                continue;
            }
            String translated = ImportPlan.translate(node.getKey());
            if (translated == null) {
                plan.foreign();
                plan.note("Nodes other mods read are not imported, CustomPerm would not read them back.");
                continue;
            }
            (node.getValue() ? allow : deny).add(translated);
            plan.imported(!translated.equals(node.getKey()));
            String command = ImportPlan.exposedCommand(node.getKey());
            if (exposeCommands && command != null && node.getValue()) plan.expose(command);
        }
    }
}
