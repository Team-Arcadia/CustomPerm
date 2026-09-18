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
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.WeightNode;

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
                for (String parent : source.parents()) add(group, InheritanceNode.builder(parent).build(), kept);
                for (String parent : source.deniedParents()) {
                    add(group, InheritanceNode.builder(parent).value(false).build(), kept);
                }
                addNodes(group, source.allow(), source.deny(), kept);
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
            int before = plan.holders() - plan.players().size();

            for (ExportPlan.Player source : plan.players()) {
                holder = "player " + source.uuid();
                User user = await(api.getUserManager().loadUser(UUID.fromString(source.uuid())));
                if (replace) clearDecided(user, false);
                for (String grade : source.grades()) add(user, InheritanceNode.builder(grade).build(), kept);
                for (String grade : source.deniedGrades()) {
                    add(user, InheritanceNode.builder(grade).value(false).build(), kept);
                }
                addNodes(user, source.allow(), source.deny(), kept);
                await(api.getUserManager().saveUser(user));
                user.getCachedData().invalidate();
                players++;
                progress.accept(before + players);
            }
            return new ExportPlan.Outcome(groups, players, kept[0], null, null);
        } catch (Throwable t) {
            // Whatever it was, the outcome completes: an export left running forever would refuse every
            // other one until a restart.
            return new ExportPlan.Outcome(groups, players, kept[0], holder, describe(t));
        }
    }

    private static void addNodes(PermissionHolder holder, Set<String> allow, Set<String> deny, int[] kept) {
        for (String key : allow) add(holder, Node.builder(key).value(true).build(), kept);
        for (String key : deny) add(holder, Node.builder(key).value(false).build(), kept);
    }

    /**
     * Adds a global, permanent node unless the holder already sets that key there. Set the same way, there
     * is nothing to do; set the other way, LuckPerms' value is kept and counted: adding never takes away,
     * and replacing has already cleared what it would conflict with.
     */
    private static void add(PermissionHolder holder, Node node, int[] kept) {
        for (Node existing : holder.getNodes()) {
            if (!decidedHere(existing) || !existing.getKey().equalsIgnoreCase(node.getKey())) continue;
            if (existing.getValue() != node.getValue()) kept[0]++;
            return;
        }
        holder.data().add(node);
    }

    /**
     * Clears what CustomPerm decides on a holder: its global, permanent parents and its {@code customperm.*}
     * and {@code *} nodes. Its prefix, suffix, meta, weight aside, contextual and temporary entries and the
     * nodes other mods read stay, those being what LuckPerms is kept for. A player keeps the default group,
     * which LuckPerms would otherwise have to give back on their next login.
     */
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

    /** A node CustomPerm could have written: no context, no expiry. */
    private static boolean decidedHere(Node node) {
        return node.getContexts().isEmpty() && !node.hasExpiry();
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
