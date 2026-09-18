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
import com.arcadia.customperm.admin.ScopedGrant;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;

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
                    .thenCompose(ignored -> api.getTrackManager().loadAllTracks())
                    .thenApply(ignored -> readTracks(api, plan))
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
            Chat chat = new Chat();
            Map<String, Long> expiries = new java.util.HashMap<>();
            List<ScopedGrant> scoped = new ArrayList<>();
            readNodes(group.getNodes(), plan, exposeCommands, parents, deniedParents, allow, deny, chat, expiries,
                    scoped, true, "group " + group.getName());
            plan.grade(new ImportPlan.Grade(group.getName(), group.getWeight().orElse(0),
                    List.copyOf(parents), List.copyOf(deniedParents), Set.copyOf(allow), Set.copyOf(deny),
                    chat.prefix, chat.suffix, Map.copyOf(expiries), List.copyOf(scoped)));
        }
        return null;
    }

    /** Every track with its groups, lowest first, which become the same ladder of grades. */
    private static Void readTracks(LuckPerms api, ImportPlan.Builder plan) {
        for (net.luckperms.api.track.Track track : api.getTrackManager().getLoadedTracks()) {
            plan.track(track.getName(), track.getGroups());
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
        CompletableFuture<Map<UUID, Collection<ChatMetaNode<?, ?>>>> prefixes =
                api.getUserManager().searchAll(NodeMatcher.type(NodeType.PREFIX));
        CompletableFuture<Map<UUID, Collection<ChatMetaNode<?, ?>>>> suffixes =
                api.getUserManager().searchAll(NodeMatcher.type(NodeType.SUFFIX));
        List<CompletableFuture<Map<UUID, Collection<Node>>>> permissions = List.of(
                api.getUserManager().searchAll(NodeMatcher.keyStartsWith("customperm.")),
                api.getUserManager().searchAll(NodeMatcher.keyStartsWith("minecraft.command.")),
                api.getUserManager().searchAll(NodeMatcher.key("*")));

        CompletableFuture<?>[] all = new CompletableFuture<?>[permissions.size() + 3];
        all[0] = inherited;
        all[1] = prefixes;
        all[2] = suffixes;
        for (int i = 0; i < permissions.size(); i++) all[i + 3] = permissions.get(i);

        return CompletableFuture.allOf(all).thenApply(ignored -> {
            Map<UUID, List<Node>> byUser = new LinkedHashMap<>();
            inherited.join().forEach((uuid, nodes) -> byUser.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(nodes));
            prefixes.join().forEach((uuid, nodes) -> byUser.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(nodes));
            suffixes.join().forEach((uuid, nodes) -> byUser.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(nodes));
            for (CompletableFuture<Map<UUID, Collection<Node>>> search : permissions) {
                search.join().forEach((uuid, nodes) ->
                        byUser.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(nodes));
            }
            plan.note("On players, only what CustomPerm can read is looked at: their groups, their prefix and "
                    + "suffix, and their customperm, minecraft.command and * nodes.");

            for (Map.Entry<UUID, List<Node>> user : byUser.entrySet()) {
                UUID uuid = user.getKey();
                List<String> grades = new ArrayList<>();
                List<String> deniedGrades = new ArrayList<>();
                Set<String> allow = new LinkedHashSet<>();
                Set<String> deny = new LinkedHashSet<>();
                Chat chat = new Chat();
                Map<String, Long> expiries = new java.util.HashMap<>();
                List<ScopedGrant> scoped = new ArrayList<>();
                readNodes(user.getValue(), plan, exposeCommands, grades, deniedGrades, allow, deny, chat, expiries,
                        scoped, false, "player " + uuid);
                if (grades.isEmpty() && deniedGrades.isEmpty() && allow.isEmpty() && deny.isEmpty()
                        && chat.prefix == null && chat.suffix == null && scoped.isEmpty()) continue;
                plan.player(new ImportPlan.Player(uuid.toString(), name(api, uuid), List.copyOf(grades),
                        List.copyOf(deniedGrades), Set.copyOf(allow), Set.copyOf(deny), chat.prefix, chat.suffix,
                        Map.copyOf(expiries), List.copyOf(scoped)));
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
     * The context CustomPerm stores for a LuckPerms context set, or {@code null} when it has none: a single
     * {@code world} with a single value is the only one read here.
     */
    private static String context(net.luckperms.api.context.ContextSet contexts) {
        if (contexts.size() != 1) return null;
        var context = contexts.iterator().next();
        if (!context.getKey().equalsIgnoreCase(com.arcadia.customperm.perm.Contexts.WORLD)) return null;
        return com.arcadia.customperm.perm.Contexts.parse(
                com.arcadia.customperm.perm.Contexts.WORLD + "=" + context.getValue());
    }

    /** A node that carries a context: kept when it is limited to one world and nothing else, left behind otherwise. */
    private static void readScoped(Node node, Long at, ImportPlan.Builder plan, boolean exposeCommands,
                                   List<ScopedGrant> scoped, boolean group, String holder) {
        String context = context(node.getContexts());
        if (context == null) {
            plan.contextual();
            plan.note("Only entries limited to a single world are imported: a server context, several worlds or "
                    + "another key is not, a node here would apply everywhere: " + holder + ".");
            return;
        }
        if (at != null) {
            plan.contextual();
            plan.note("An entry limited to a world is permanent here: temporary ones limited to a world are not "
                    + "imported: " + holder + ".");
            return;
        }
        if (node instanceof InheritanceNode inheritance) {
            if (group || !node.getValue()) {
                plan.contextual();
                plan.note("Only a player's grades can be limited to a world here, not a group's parents or a "
                        + "refusal: " + holder + ".");
                return;
            }
            scoped.add(new ScopedGrant(context, ScopedGrant.GRADE, inheritance.getGroupName()));
            plan.imported(false);
            plan.world();
            return;
        }
        if (!NodeType.PERMISSION.matches(node)) {
            plan.contextual();
            plan.note("Prefixes, suffixes and meta limited to a world have no equivalent and are not imported: "
                    + holder + ".");
            return;
        }
        String translated = ImportPlan.translate(node.getKey());
        if (translated == null) {
            plan.foreign();
            plan.note("Nodes other mods read are not imported, CustomPerm would not read them back.");
            return;
        }
        scoped.add(new ScopedGrant(context, node.getValue() ? ScopedGrant.ALLOW : ScopedGrant.DENY, translated));
        plan.imported(!translated.equals(node.getKey()));
        plan.world();
        String command = ImportPlan.exposedCommand(node.getKey());
        if (exposeCommands && command != null && node.getValue()) plan.expose(command);
    }

    /**
     * Records the expiry of an imported entry. LuckPerms can hold the same entry both for good and for a
     * while: the permanent one wins, as it does there, and of two temporary ones the one that lasts longer.
     */
    private static void stamp(Map<String, Long> expiries, Set<String> permanent, String key, Long at,
                              ImportPlan.Builder plan) {
        if (at == null) {
            permanent.add(key);
            expiries.remove(key);
            return;
        }
        plan.timed();
        if (!permanent.contains(key)) expiries.merge(key, at, Math::max);
    }

    /**
     * The prefix and suffix one holder keeps: a grade or a player carries one of each, so of several the
     * one LuckPerms would show first, the highest priority, is the one imported. Ties go to the text that
     * sorts first, so two reads of the same data import the same thing.
     */
    private static final class Chat {
        private String prefix;
        private int prefixPriority = Integer.MIN_VALUE;
        private String suffix;
        private int suffixPriority = Integer.MIN_VALUE;

        /** Keeps the node when it beats the one kept so far; false when it does not, and is left behind. */
        boolean offer(ChatMetaNode<?, ?> node) {
            boolean isPrefix = node instanceof PrefixNode;
            String kept = isPrefix ? prefix : suffix;
            int keptPriority = isPrefix ? prefixPriority : suffixPriority;
            if (kept != null && (node.getPriority() < keptPriority
                    || (node.getPriority() == keptPriority && node.getMetaValue().compareTo(kept) >= 0))) {
                return false;
            }
            if (isPrefix) {
                prefix = node.getMetaValue();
                prefixPriority = node.getPriority();
            } else {
                suffix = node.getMetaValue();
                suffixPriority = node.getPriority();
            }
            return kept == null;
        }
    }

    /**
     * Sorts one holder's nodes into what CustomPerm keeps and what it leaves behind. A node is left
     * behind when it would not mean here what it means there: a temporary node imported as permanent
     * would over-grant, a contextual one imported as global would grant everywhere, and a node another
     * mod reads would sit in the file granting nothing. A node limited to one world is carried with it.
     */
    private static void readNodes(Collection<? extends Node> nodes, ImportPlan.Builder plan,
                                  boolean exposeCommands, List<String> parents, List<String> deniedParents,
                                  Set<String> allow, Set<String> deny, Chat chat, Map<String, Long> expiries,
                                  List<ScopedGrant> scoped, boolean group, String holder) {
        Set<String> permanent = new java.util.HashSet<>();
        long now = com.arcadia.customperm.perm.Expiry.now();
        for (Node node : nodes) {
            Long at = node.getExpiry() == null ? null : node.getExpiry().getEpochSecond();
            // Already over: LuckPerms drops it on its next pass, and so would the sweep here.
            if (at != null && at <= now) continue;
            if (!node.getContexts().isEmpty()) {
                readScoped(node, at, plan, exposeCommands, scoped, group, holder);
                continue;
            }
            if (node instanceof InheritanceNode inheritance) {
                if (at != null && group) {
                    plan.temporary();
                    plan.note("A grade inherits for good here: temporary parents of a group are not imported: "
                            + holder + ".");
                    continue;
                }
                (node.getValue() ? parents : deniedParents).add(inheritance.getGroupName());
                stamp(expiries, permanent, (node.getValue() ? "grade:" : "refuse:") + inheritance.getGroupName(), at, plan);
                plan.imported(false);
                continue;
            }
            if (node instanceof ChatMetaNode<?, ?> meta) {
                if (at != null) {
                    plan.temporary();
                    plan.note("A prefix or a suffix is set for good here: temporary ones are not imported: "
                            + holder + ".");
                    continue;
                }
                if (chat.offer(meta)) {
                    plan.imported(false);
                } else {
                    plan.other();
                    plan.note("One prefix and one suffix per holder: the one with the highest priority is "
                            + "imported, the others are not.");
                }
                continue;
            }
            if (!NodeType.PERMISSION.matches(node)) {
                plan.other();
                plan.note("Meta and display names have no equivalent and are not imported.");
                continue;
            }
            String translated = ImportPlan.translate(node.getKey());
            if (translated == null) {
                plan.foreign();
                plan.note("Nodes other mods read are not imported, CustomPerm would not read them back.");
                continue;
            }
            (node.getValue() ? allow : deny).add(translated);
            stamp(expiries, permanent, (node.getValue() ? "allow:" : "deny:") + translated, at, plan);
            plan.imported(!translated.equals(node.getKey()));
            String command = ImportPlan.exposedCommand(node.getKey());
            if (exposeCommands && command != null && node.getValue()) plan.expose(command);
        }
    }
}
