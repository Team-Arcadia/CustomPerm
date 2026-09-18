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

    private static final String FOREIGN = "Nodes other mods read without declaring them to NeoForge's permission "
            + "API are not imported, CustomPerm would not read them back; declared ones are.";

    /** The boolean nodes other mods declared, which the import carries since CustomPerm answers them. */
    private static List<String> declared = List.of();

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
            // Read once, on the calling thread: what the rest reads on LuckPerms' threads never changes.
            declared = List.copyOf(com.arcadia.customperm.perm.ModPermissions.declaredNodes());
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
                    chat.grants(), Map.copyOf(expiries), List.copyOf(scoped)));
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
        List<CompletableFuture<Map<UUID, Collection<Node>>>> permissions = new ArrayList<>(List.of(
                api.getUserManager().searchAll(NodeMatcher.keyStartsWith("customperm.")),
                api.getUserManager().searchAll(NodeMatcher.keyStartsWith("minecraft.command.")),
                api.getUserManager().searchAll(NodeMatcher.key("*"))));
        // One search per mod that declared nodes, by the namespace they start with, not one per node.
        for (String namespace : namespaces()) {
            permissions.add(api.getUserManager().searchAll(NodeMatcher.keyStartsWith(namespace + ".")));
        }

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
            plan.note("On players, only what CustomPerm can read is looked at: their groups, their prefixes and "
                    + "suffixes, their customperm, minecraft.command and * nodes, and the nodes other mods declared "
                    + "to NeoForge.");

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
                        && chat.grants().isEmpty() && scoped.isEmpty()) continue;
                plan.player(new ImportPlan.Player(uuid.toString(), name(api, uuid), List.copyOf(grades),
                        List.copyOf(deniedGrades), Set.copyOf(allow), Set.copyOf(deny), chat.grants(),
                        Map.copyOf(expiries), List.copyOf(scoped)));
            }
            return null;
        });
    }

    /** The first segment of every declared node, other than the ones already searched. */
    private static java.util.Set<String> namespaces() {
        java.util.Set<String> namespaces = new java.util.TreeSet<>();
        for (String name : declared) {
            int dot = name.indexOf('.');
            String namespace = dot < 0 ? name : name.substring(0, dot);
            if (!namespace.equals("customperm") && !namespace.equals("minecraft")) namespaces.add(namespace);
        }
        return namespaces;
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
                                   List<ScopedGrant> scoped, Chat chat, boolean group, String holder) {
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
            // On a group, a parent or a refusal of its chain; on a player, a grade held or refused.
            String kind = !node.getValue() ? ScopedGrant.REFUSED : group ? ScopedGrant.PARENT : ScopedGrant.GRADE;
            scoped.add(new ScopedGrant(context, kind, inheritance.getGroupName()));
            plan.imported(false);
            plan.world();
            return;
        }
        if (node instanceof ChatMetaNode<?, ?> meta) {
            if (chat.offer(meta, null, context)) {
                plan.imported(false);
                plan.world();
            } else {
                plan.other();
                plan.note("One prefix and one suffix per priority on a holder: of two at the same priority, "
                        + "the text that sorts first is imported.");
            }
            return;
        }
        if (!NodeType.PERMISSION.matches(node)) {
            plan.contextual();
            plan.note("Meta limited to a world has no equivalent and is not imported: " + holder + ".");
            return;
        }
        String translated = ImportPlan.translate(node.getKey(), declared);
        if (translated == null) {
            plan.foreign();
            plan.note(FOREIGN);
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
     * The prefixes and suffixes one holder keeps, each with its priority and expiry. A holder carries one per
     * priority here, so of two at the same priority the text that sorts first is imported, and two reads of
     * the same data import the same thing. The same text twice is one entry: permanent if either is, else
     * the later expiry, as LuckPerms would keep showing it.
     */
    private static final class Chat {
        private final Map<String, com.arcadia.customperm.admin.ChatGrant> kept = new java.util.TreeMap<>();

        boolean offer(ChatMetaNode<?, ?> node, Long at) {
            return offer(node, at, "");
        }

        /** False when the node is left behind: another at its priority sorts first, or it displaced one. */
        boolean offer(ChatMetaNode<?, ?> node, Long at, String context) {
            boolean suffix = !(node instanceof PrefixNode);
            String key = (suffix ? "suffix:" : "prefix:") + node.getPriority() + "@" + context;
            long expires = at == null ? 0 : at;
            var offered = new com.arcadia.customperm.admin.ChatGrant(suffix, node.getPriority(), node.getMetaValue(),
                    expires, context);
            var existing = kept.get(key);
            if (existing != null && existing.text().equals(offered.text())) {
                long longer = existing.expires() == 0 || expires == 0 ? 0 : Math.max(existing.expires(), expires);
                kept.put(key, new com.arcadia.customperm.admin.ChatGrant(suffix, node.getPriority(), offered.text(),
                        longer, context));
                return true;
            }
            if (existing != null && offered.text().compareTo(existing.text()) >= 0) return false;
            kept.put(key, offered);
            return existing == null;
        }

        List<com.arcadia.customperm.admin.ChatGrant> grants() {
            return List.copyOf(kept.values());
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
                readScoped(node, at, plan, exposeCommands, scoped, chat, group, holder);
                continue;
            }
            if (node instanceof InheritanceNode inheritance) {
                (node.getValue() ? parents : deniedParents).add(inheritance.getGroupName());
                stamp(expiries, permanent, (node.getValue() ? "grade:" : "refuse:") + inheritance.getGroupName(), at, plan);
                plan.imported(false);
                continue;
            }
            if (node instanceof ChatMetaNode<?, ?> meta) {
                if (chat.offer(meta, at)) {
                    plan.imported(false);
                    if (at != null) plan.timed();
                } else {
                    plan.other();
                    plan.note("One prefix and one suffix per priority on a holder: of two at the same priority, "
                            + "the text that sorts first is imported.");
                }
                continue;
            }
            if (!NodeType.PERMISSION.matches(node)) {
                plan.other();
                plan.note("Meta and display names have no equivalent and are not imported.");
                continue;
            }
            String translated = ImportPlan.translate(node.getKey(), declared);
            if (translated == null) {
                plan.foreign();
                plan.note(FOREIGN);
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
