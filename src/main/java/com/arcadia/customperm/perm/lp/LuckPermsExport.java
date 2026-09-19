/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm.lp;

import com.arcadia.customperm.admin.ExportPlan;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.node.types.WeightNode;
import net.luckperms.api.track.Track;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Writes an {@link ExportPlan} into LuckPerms. Like {@code LuckPermsImport} this class mentions
 * {@code net.luckperms.api} types, so callers reach it only from inside a
 * {@code CustomPerm.isLuckPermsActive()} branch and never through a method reference.
 *
 * <p><strong>One load and one save per holder.</strong> {@code LuckPermsAdminService.apply} makes a storage
 * round trip per change because it serves an editor where a click is a change; an export reusing it would
 * make ten for a player with two grades and three nodes. Here every entry of a holder is written between
 * one load and one save.
 *
 * <p><strong>Its own thread, one holder after the other.</strong> A server with thousands of known players
 * is thousands of storage operations: never on the server thread, and not chained on LuckPerms' pool
 * either, where a failure part way would be hard to place. A plain loop on a worker thread waits for each
 * answer, so progress is a counter and the point where it stopped is the holder it was on.
 *
 * <p><strong>Groups before players</strong>, so a player never inherits a group that is not there yet, and
 * the default group after the groups, for the same reason.
 */
public final class LuckPermsExport {

    /** Longest wait for one LuckPerms answer: past it the storage is taken as gone, not as slow. */
    private static final long ANSWER_SECONDS = 60;

    private LuckPermsExport() {
    }

    /**
     * The names of the groups LuckPerms has, loaded from storage first. Completes with an empty set when
     * LuckPerms cannot be read, the report then simply not saying which groups already exist.
     */
    public static CompletableFuture<Set<String>> existingGroups() {
        try {
            LuckPerms api = LuckPermsProvider.get();
            return api.getGroupManager().loadAllGroups()
                    .thenApply(ignored -> api.getGroupManager().getLoadedGroups().stream()
                            .map(Group::getName).collect(Collectors.toUnmodifiableSet()))
                    .exceptionally(error -> Set.of());
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            return CompletableFuture.completedFuture(Set.of());
        }
    }

    /**
     * Writes the plan on a worker thread and completes with how it ended. Never completes exceptionally:
     * a failure is an {@link ExportPlan.Outcome} naming the holder it stopped at.
     *
     * @param replace  true to clear what CustomPerm decides on a holder before writing it
     * @param progress told the number of holders written after each one, on the worker thread
     */
    public static CompletableFuture<ExportPlan.Outcome> write(ExportPlan plan, boolean replace, IntConsumer progress) {
        CompletableFuture<ExportPlan.Outcome> outcome = new CompletableFuture<>();
        Thread worker = new Thread(() -> outcome.complete(run(plan, replace, progress)), "CustomPerm LuckPerms export");
        worker.setDaemon(true);
        worker.start();
        return outcome;
    }

    private static ExportPlan.Outcome run(ExportPlan plan, boolean replace, IntConsumer progress) {
        int groups = 0;
        int players = 0;
        int[] kept = {0};
        String holder = "LuckPerms";
        try {
            LuckPerms api = LuckPermsProvider.get();
            for (ExportPlan.Group source : plan.groups()) {
                holder = "group " + source.name();
                Group group = await(api.getGroupManager().loadGroup(source.name())).orElse(null);
                if (group == null) group = await(api.getGroupManager().createAndLoadGroup(source.name()));
                if (replace) clearDecided(group, true);
                if (replace || group.getWeight().isEmpty()) {
                    // Adding to a group that has a weight keeps it, as the import keeps a grade's: the number
                    // set there is a decision. A weight of 0 is what no weight means, so none is written.
                    group.data().clear(NodeType.WEIGHT::matches);
                    if (source.weight() != 0) group.data().add(WeightNode.builder(source.weight()).build());
                }
                writeDisplayName(group, source.displayName(), replace);
                for (String parent : source.parents()) {
                    add(group, timed(InheritanceNode.builder(parent), source.expiries().get("grade:" + parent)), kept);
                }
                for (String parent : source.deniedParents()) {
                    add(group, timed(InheritanceNode.builder(parent).value(false), source.expiries().get("refuse:" + parent)),
                            kept);
                }
                addNodes(group, source.allow(), source.deny(), source.expiries(), kept);
                addScoped(group, source.scoped(), kept);
                addChat(group, source.chat(), replace, kept);
                addMeta(group, source.meta(), replace, kept);
                await(api.getGroupManager().saveGroup(group));
                groups++;
                progress.accept(groups);
            }

            if (plan.carriesDefault()) {
                holder = "group " + ExportPlan.LP_DEFAULT;
                Group fallback = await(api.getGroupManager().loadGroup(ExportPlan.LP_DEFAULT)).orElse(null);
                if (fallback == null) throw new IllegalStateException("LuckPerms has no default group");
                add(fallback, InheritanceNode.builder(plan.defaultGrade()).build(), kept);
                await(api.getGroupManager().saveGroup(fallback));
                progress.accept(groups + 1);
            }
            int before = plan.groups().size() + (plan.carriesDefault() ? 1 : 0);

            for (ExportPlan.Player source : plan.players()) {
                holder = "player " + source.uuid();
                User user = await(api.getUserManager().loadUser(UUID.fromString(source.uuid())));
                if (replace) clearDecided(user, false);
                for (String grade : source.grades()) {
                    add(user, timed(InheritanceNode.builder(grade), source.expiries().get("grade:" + grade)), kept);
                }
                for (String grade : source.deniedGrades()) {
                    add(user, timed(InheritanceNode.builder(grade).value(false), source.expiries().get("refuse:" + grade)),
                            kept);
                }
                addNodes(user, source.allow(), source.deny(), source.expiries(), kept);
                addScoped(user, source.scoped(), kept);
                addChat(user, source.chat(), replace, kept);
                addMeta(user, source.meta(), replace, kept);
                await(api.getUserManager().saveUser(user));
                user.getCachedData().invalidate();
                players++;
                progress.accept(before + players);
            }
            int written = before + players;
            for (ExportPlan.Track source : plan.tracks()) {
                holder = "track " + source.name();
                Track track = await(api.getTrackManager().loadTrack(source.name())).orElse(null);
                boolean created = track == null;
                if (created) track = await(api.getTrackManager().createAndLoadTrack(source.name()));
                if (!created && !replace) {
                    // Adding never reorders a ladder LuckPerms already has: that order is a decision there.
                    if (!track.getGroups().equals(source.groups())) kept[0]++;
                } else {
                    track.clearGroups();
                    for (String name : source.groups()) {
                        Group group = api.getGroupManager().getGroup(name);
                        if (group == null) group = await(api.getGroupManager().loadGroup(name)).orElse(null);
                        if (group == null) throw new IllegalStateException("LuckPerms has no group " + name);
                        track.appendGroup(group);
                    }
                    await(api.getTrackManager().saveTrack(track));
                }
                progress.accept(++written);
            }
            return new ExportPlan.Outcome(groups, players, kept[0], null, null);
        } catch (Throwable t) {
            // Whatever it was, the outcome completes: an export left running forever would refuse every
            // other one until a restart.
            return new ExportPlan.Outcome(groups, players, kept[0], holder, describe(t));
        }
    }

    private static void addNodes(PermissionHolder holder, Set<String> allow, Set<String> deny,
                                 java.util.Map<String, Long> expiries, int[] kept) {
        for (String key : allow) add(holder, timed(Node.builder(key).value(true), expiries.get("allow:" + key)), kept);
        for (String key : deny) add(holder, timed(Node.builder(key).value(false), expiries.get("deny:" + key)), kept);
    }

    /** Entries limited to a context, written with LuckPerms' contexts (see {@link #contexts}), a temporary one with its expiry. */
    private static void addScoped(PermissionHolder holder, List<com.arcadia.customperm.admin.ScopedGrant> scoped,
                                  int[] kept) {
        for (var entry : scoped) {
            net.luckperms.api.node.NodeBuilder<?, ?> builder = switch (entry.kind()) {
                case com.arcadia.customperm.admin.ScopedGrant.GRADE, com.arcadia.customperm.admin.ScopedGrant.PARENT ->
                        InheritanceNode.builder(entry.value());
                case com.arcadia.customperm.admin.ScopedGrant.REFUSED -> InheritanceNode.builder(entry.value()).value(false);
                case com.arcadia.customperm.admin.ScopedGrant.DENY -> Node.builder(entry.value()).value(false);
                default -> Node.builder(entry.value()).value(true);
            };
            add(holder, timed(builder.withContext(contexts(entry.context())),
                    entry.expires() > 0 ? entry.expires() : null), kept);
        }
    }

    /** The node, temporary until {@code at} when it has an expiry here, as LuckPerms stores one. */
    private static Node timed(net.luckperms.api.node.NodeBuilder<?, ?> builder, Long at) {
        if (at != null) builder.expiry(java.time.Instant.ofEpochSecond(at));
        return builder.build();
    }

    /**
     * Writes prefixes and suffixes, each at its priority and in its world. Adding keeps one the holder already
     * has at that priority and in that context, and counts it when it says something else; replacing clears the
     * holder's own first, but only for the kinds the grade sets, so a group given a prefix in LuckPerms keeps it
     * when the grade has none.
     */
    private static void addChat(PermissionHolder holder, List<com.arcadia.customperm.admin.ChatGrant> chat,
                                boolean replace, int[] kept) {
        if (chat.isEmpty()) return;
        if (replace) {
            // Only the kinds, in the worlds, this holder carries: a group given only a suffix here, or only a
            // prefix limited to the Nether, keeps its other LuckPerms prefixes.
            Set<String> carried = new java.util.HashSet<>();
            for (var grant : chat) carried.add(grant.suffix() + "@" + where(grant));
            holder.getNodes().stream().filter(LuckPermsExport::decidedHere)
                    .filter(node -> NodeType.PREFIX.matches(node) || NodeType.SUFFIX.matches(node))
                    .filter(node -> carried.contains(NodeType.SUFFIX.matches(node) + "@" + node.getContexts()))
                    .toList().forEach(existing -> holder.data().remove(existing));
        }
        for (com.arcadia.customperm.admin.ChatGrant grant : chat) {
            NodeType<? extends ChatMetaNode<?, ?>> type = grant.suffix() ? NodeType.SUFFIX : NodeType.PREFIX;
            net.luckperms.api.context.ImmutableContextSet where = where(grant);
            ChatMetaNode<?, ?> same = holder.getNodes().stream().filter(node -> !node.hasExpiry())
                    .filter(type::matches).map(type::cast)
                    .filter(node -> node.getPriority() == grant.priority() && node.getContexts().equals(where))
                    .findFirst().orElse(null);
            if (same != null) {
                // Adding keeps what LuckPerms has at that priority, and counts it when it says something else.
                if (!same.getMetaValue().equals(grant.text())) kept[0]++;
                continue;
            }
            Long at = grant.expires() > 0 ? grant.expires() : null;
            net.luckperms.api.node.NodeBuilder<?, ?> builder = grant.suffix()
                    ? SuffixNode.builder(grant.text(), grant.priority())
                    : PrefixNode.builder(grant.text(), grant.priority());
            if (!grant.context().isEmpty()) builder.context(where);
            holder.data().add(timed(builder, at));
        }
    }

    /**
     * Writes meta, each key in its context. Adding keeps a value the holder already has for that key there, and
     * counts it when it differs; replacing clears the holder's own values of the keys the grade sets first, so
     * meta LuckPerms holds for other keys stays.
     */
    private static void addMeta(PermissionHolder holder, List<com.arcadia.customperm.admin.MetaGrant> meta,
                                boolean replace, int[] kept) {
        if (meta.isEmpty()) return;
        if (replace) {
            Set<String> carried = new java.util.HashSet<>();
            for (var grant : meta) carried.add(grant.key() + "@" + contexts(grant.context()));
            holder.getNodes().stream().filter(LuckPermsExport::decidedHere).filter(NodeType.META::matches)
                    .map(NodeType.META::cast)
                    .filter(node -> carried.contains(node.getMetaKey().toLowerCase(java.util.Locale.ROOT) + "@" + node.getContexts()))
                    .toList().forEach(existing -> holder.data().remove(existing));
        }
        for (var grant : meta) {
            net.luckperms.api.context.ImmutableContextSet where = contexts(grant.context());
            var same = holder.getNodes().stream().filter(node -> !node.hasExpiry()).filter(NodeType.META::matches)
                    .map(NodeType.META::cast)
                    .filter(node -> node.getMetaKey().equalsIgnoreCase(grant.key()) && node.getContexts().equals(where))
                    .findFirst().orElse(null);
            if (same != null) {
                if (!same.getMetaValue().equals(grant.value())) kept[0]++;
                continue;
            }
            holder.data().add(timed(net.luckperms.api.node.types.MetaNode.builder(grant.key(), grant.value())
                    .withContext(where), grant.expires() > 0 ? grant.expires() : null));
        }
    }

    /**
     * Adds a permanent node unless the holder already sets that key in the same context. Set the same way,
     * there is nothing to do; set the other way, LuckPerms' value is kept and counted: adding never takes
     * away, and replacing has already cleared what it would conflict with.
     */
    private static void add(PermissionHolder holder, Node node, int[] kept) {
        for (Node existing : holder.getNodes()) {
            if (!decidedHere(existing) || !existing.getKey().equalsIgnoreCase(node.getKey())
                    || !existing.getContexts().equals(node.getContexts())) continue;
            if (existing.getValue() != node.getValue()) kept[0]++;
            return;
        }
        holder.data().add(node);
    }

    /**
     * Clears what CustomPerm decides on a holder: its permanent parents and its {@code customperm.*} and
     * {@code *} nodes, global or limited to contexts it writes. Its prefix, suffix, meta (see {@link #addMeta}), weight aside, other
     * contexts, temporary entries and the nodes other mods read stay, those being what LuckPerms is kept for. A player keeps the default group,
     * which LuckPerms would otherwise have to give back on their next login.
     */
    /**
     * The group's display name, the one that applies everywhere. Adding keeps one LuckPerms already has, as the
     * weight: it is what was chosen there. Replacing writes this side's, or none. A display name LuckPerms keeps
     * for a context is left alone either way: this side has none to put in its place.
     */
    private static void writeDisplayName(Group group, String displayName, boolean replace) {
        boolean present = group.getNodes(NodeType.DISPLAY_NAME).stream().anyMatch(node -> node.getContexts().isEmpty());
        if (present && !replace) return;
        group.data().clear(node -> NodeType.DISPLAY_NAME.matches(node) && node.getContexts().isEmpty());
        if (!displayName.isEmpty()) group.data().add(DisplayNameNode.builder(displayName).build());
    }

    private static void clearDecided(PermissionHolder holder, boolean group) {
        List<Node> doomed = holder.getNodes().stream()
                .filter(LuckPermsExport::decidedHere)
                .filter(node -> {
                    if (node instanceof InheritanceNode inheritance) {
                        return group || !inheritance.getGroupName().equals(ExportPlan.LP_DEFAULT);
                    }
                    return NodeType.PERMISSION.matches(node)
                            && (node.getKey().equals("*") || node.getKey().startsWith("customperm."));
                })
                .toList();
        for (Node node : doomed) holder.data().remove(node);
    }

    /** The LuckPerms contexts a prefix limited to {@code grant}'s context is written with; empty for everywhere. */
    private static net.luckperms.api.context.ImmutableContextSet where(com.arcadia.customperm.admin.ChatGrant grant) {
        return contexts(grant.context());
    }

    /**
     * A stored context as LuckPerms' contexts: {@code world} becomes {@code dimension-type}, the key LuckPerms
     * gives the dimension on NeoForge, written the way it writes it; every other key goes as it is. Two
     * values of one key stay two values, which LuckPerms reads as either, as this side does.
     */
    static net.luckperms.api.context.ImmutableContextSet contexts(String context) {
        var builder = net.luckperms.api.context.ImmutableContextSet.builder();
        for (String[] pair : com.arcadia.customperm.perm.Contexts.split(context)) {
            if (pair[0].equals(com.arcadia.customperm.perm.Contexts.WORLD)) {
                builder.add(com.arcadia.customperm.perm.Contexts.DIMENSION_TYPE,
                        com.arcadia.customperm.perm.Contexts.luckPermsWorld(pair[1]));
            } else {
                builder.add(pair[0], pair[1]);
            }
        }
        return builder.build();
    }

    /**
     * A node CustomPerm could have written: no expiry, and no context or only contexts it writes, which are
     * never LuckPerms' {@code world} (the save's name). {@code server} is one it writes, since cluster mode.
     */
    private static boolean decidedHere(Node node) {
        if (node.hasExpiry()) return false;
        for (var context : node.getContexts()) {
            if (context.getKey().toLowerCase(java.util.Locale.ROOT).equals(com.arcadia.customperm.perm.Contexts.WORLD)) {
                return false;
            }
        }
        return true;
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try {
            return future.get(ANSWER_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } catch (TimeoutException e) {
            throw new IllegalStateException("LuckPerms did not answer within " + ANSWER_SECONDS + "s");
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }
}
