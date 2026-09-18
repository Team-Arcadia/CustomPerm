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
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Nodes carried by one player rather than by a grade: the exception a single player gets without
 * inventing a grade for them, like a node set on a LuckPerms user rather than on one of their groups.
 * Shared by {@code /customperm user} and the Players page of the interface, server thread only.
 *
 * <p>A node here ranks above every grade the player holds at the same specificity, whatever the grade
 * weighs, but it does not beat a more specific grade node: the most specific entry still wins. Like
 * grades, these operations refuse while LuckPerms is the active backend, where they decide nothing.
 *
 * <p>Changes made by a player go through {@link GradeAdmin#guarded} like grade changes: denying yourself
 * a node here locks you out of the commands that would repair it just as surely.
 */
public final class UserAdmin {

    private UserAdmin() {
    }

    private static GradesConfig grades() {
        return CustomPerm.configManager.getGrades();
    }

    private static Map<String, Set<String>> holder(boolean deny) {
        return deny ? grades().userDeniedPermissions : grades().userPermissions;
    }

    /** Adds an ALLOW or a DENY node to one player. */
    public static AdminResult addNode(MinecraftServer server, UUID uuid, String displayName, String rawNode,
                                      boolean deny) {
        return addNode(server, uuid, displayName, rawNode, deny, 0);
    }

    /** The same for {@code seconds}, 0 for permanent; on a node already there, the duration replaces its own. */
    public static AdminResult addNode(MinecraftServer server, UUID uuid, String displayName, String rawNode,
                                      boolean deny, long seconds) {
        return addNode(server, uuid, displayName, rawNode, deny, seconds, null);
    }

    /** The same limited to {@code rawContext}, such as {@code world=the_nether}; blank for everywhere. */
    public static AdminResult addNode(MinecraftServer server, UUID uuid, String displayName, String rawNode,
                                      boolean deny, long seconds, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String node = GradeAdmin.normalizeNode(rawNode);
        if (node == null) return AdminResult.fail("Invalid permission node '" + rawNode.trim() + "'.");
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null && seconds > 0) return Scopes.timedAndScoped();
        if (context != null) {
            GradesConfig.UserScoped scope = Scopes.of(grades(), uuid, context);
            if (!(deny ? scope.deniedPermissions : scope.permissions).add(node)) {
                return AdminResult.ok(node + " is already " + (deny ? "denied to " : "granted to ") + displayName
                        + Scopes.span(context) + " — no change.");
            }
            String warning = ConfigAdmin.persist();
            GradeAdmin.resyncPlayer(server, uuid);
            return AdminResult.ok((deny ? "Denied " : "Added ") + node + " -> " + displayName + Scopes.span(context))
                    .warn(warning);
        }
        Set<String> nodes = holder(deny).computeIfAbsent(uuid.toString(), key -> new LinkedHashSet<>());
        boolean added = nodes.add(node);
        Map<String, Map<String, Long>> expiries = expiries(deny);
        boolean timed = Expiries.apply(Expiries.of(expiries, uuid), node, seconds);
        Expiries.tidy(expiries, uuid);
        if (!added && !timed) {
            return AdminResult.ok(node + " is already " + (deny ? "denied to " : "granted to ") + displayName
                    + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return AdminResult.ok(added ? (deny ? "Denied " : "Added ") + node + " -> " + displayName + Expiries.span(seconds)
                : node + " for " + displayName + " " + Expiries.became(seconds)).warn(warning);
    }

    private static Map<String, Map<String, Long>> expiries(boolean deny) {
        return deny ? grades().userDeniedPermissionExpiries : grades().userPermissionExpiries;
    }

    /**
     * Gives one player a prefix or suffix of their own at {@code priority}, for {@code seconds} or for good.
     * At equal priority it shows before any grade's; a grade's at a higher priority still shows first.
     */
    public static AdminResult addChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix,
                                      int priority, String text, long seconds) {
        return addChat(server, uuid, displayName, suffix, priority, text, seconds, null);
    }

    /** {@link #addChat(MinecraftServer, UUID, String, boolean, int, String, long)} in {@code rawContext} only. */
    public static AdminResult addChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix,
                                      int priority, String text, long seconds, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null && seconds > 0) return Scopes.timedAndScoped();
        String problem = ChatEntries.problem(suffix, priority, text);
        if (problem != null) return AdminResult.fail(problem);
        Map<String, List<GradesConfig.ChatEntry>> byUser = suffix ? grades().userSuffixEntries : grades().userPrefixEntries;
        List<GradesConfig.ChatEntry> entries;
        if (context == null) {
            entries = byUser.computeIfAbsent(uuid.toString(), k -> new ArrayList<>());
        } else {
            GradesConfig.UserScoped scope = Scopes.of(grades(), uuid, context);
            entries = suffix ? scope.suffixes : scope.prefixes;
        }
        String what = suffix ? "Suffix" : "Prefix";
        ChatEntries.Change change = ChatEntries.put(entries, priority, text, seconds);
        if (change == ChatEntries.Change.UNCHANGED) {
            return AdminResult.ok(what + " \"" + text + "\" at " + priority + " on " + displayName + Scopes.span(context)
                    + " unchanged.");
        }
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return GradeAdmin.decorationNote(AdminResult.ok(what + " \"" + text + "\" at " + priority + " -> " + displayName
                + Scopes.span(context) + Expiries.span(seconds)
                + (change == ChatEntries.Change.REPLACED ? ", replacing the one at that priority." : "")).warn(warning));
    }

    /** Removes the prefix or suffix one player carries at {@code priority}. */
    public static AdminResult removeChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix,
                                         int priority) {
        return removeChat(server, uuid, displayName, suffix, priority, null);
    }

    /** Removes the one at {@code priority} in {@code rawContext}; blank for the one that applies everywhere. */
    public static AdminResult removeChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix,
                                         int priority, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        Map<String, List<GradesConfig.ChatEntry>> byUser = suffix ? grades().userSuffixEntries : grades().userPrefixEntries;
        String what = suffix ? "suffix" : "prefix";
        GradesConfig.UserScoped scope = context == null ? null : Scopes.find(grades(), uuid, context);
        List<GradesConfig.ChatEntry> entries = context == null ? byUser.get(uuid.toString())
                : scope == null ? null : (suffix ? scope.suffixes : scope.prefixes);
        GradesConfig.ChatEntry removed = ChatEntries.remove(entries, priority);
        if (removed == null) {
            return AdminResult.ok(displayName + " has no " + what + " at " + priority + Scopes.span(context) + " — no change.");
        }
        if (context == null && entries.isEmpty()) byUser.remove(uuid.toString());
        Scopes.tidy(grades(), uuid);
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return AdminResult.ok("Removed the " + what + " " + ChatEntries.describe(removed) + " from " + displayName
                + Scopes.span(context)).warn(warning);
    }

    /** Removes every prefix, or every suffix, one player carries themselves. */
    public static AdminResult clearChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix) {
        return clearChat(server, uuid, displayName, suffix, null);
    }

    public static AdminResult clearChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix,
                                        String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        String what = suffix ? "suffixes" : "prefixes";
        int count;
        if (context == null) {
            List<GradesConfig.ChatEntry> removed = (suffix ? grades().userSuffixEntries : grades().userPrefixEntries)
                    .remove(uuid.toString());
            count = removed == null ? 0 : removed.size();
        } else {
            GradesConfig.UserScoped scope = Scopes.find(grades(), uuid, context);
            List<GradesConfig.ChatEntry> entries = scope == null ? new ArrayList<>() : (suffix ? scope.suffixes : scope.prefixes);
            count = entries.size();
            entries.clear();
            Scopes.tidy(grades(), uuid);
        }
        if (count == 0) return AdminResult.ok(displayName + " has no " + what + Scopes.span(context) + " — no change.");
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return AdminResult.ok("Cleared " + count + " " + what + " from " + displayName + Scopes.span(context)).warn(warning);
    }

    /**
     * Seconds left on a temporary entry of one player, or 0 for a permanent one. {@code kind} is
     * {@code "allow"}, {@code "deny"}, {@code "grade"} or {@code "refuse"}.
     */
    public static long remaining(UUID uuid, String kind, String key) {
        Map<String, Map<String, Long>> byUser = switch (kind) {
            case "deny" -> grades().userDeniedPermissionExpiries;
            case "grade" -> grades().userGradeExpiries;
            case "refuse" -> grades().userDeniedGradeExpiries;
            default -> grades().userPermissionExpiries;
        };
        Map<String, Long> expiries = byUser.get(uuid.toString());
        Long at = expiries == null ? null : expiries.get(key);
        return at == null ? 0 : Math.max(1, at - com.arcadia.customperm.perm.Expiry.now());
    }

    /**
     * The prefixes or suffixes a player carries themselves, for a listing, highest priority first, then those
     * limited to a world with it.
     */
    public static List<String> chat(UUID uuid, boolean suffix) {
        List<String> out = new ArrayList<>(ChatEntries.listing(
                (suffix ? grades().userSuffixEntries : grades().userPrefixEntries).get(uuid.toString())));
        Map<String, GradesConfig.UserScoped> scopes = grades().userContexts.get(uuid.toString());
        if (scopes != null) new java.util.TreeMap<>(scopes).forEach((context, scope) ->
                ChatEntries.listing(suffix ? scope.suffixes : scope.prefixes)
                        .forEach(line -> out.add(line + " " + Scopes.span(context).trim())));
        return out;
    }

    /** Removes an ALLOW or a DENY node from one player; the entry goes with its last node. */
    public static AdminResult removeNode(MinecraftServer server, UUID uuid, String displayName, String rawNode,
                                         boolean deny) {
        return removeNode(server, uuid, displayName, rawNode, deny, null);
    }

    /** Removes a node limited to {@code rawContext}; blank removes the one that applies everywhere. */
    public static AdminResult removeNode(MinecraftServer server, UUID uuid, String displayName, String rawNode,
                                         boolean deny, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null) {
            GradesConfig.UserScoped scope = Scopes.find(grades(), uuid, context);
            if (scope == null || !(deny ? scope.deniedPermissions : scope.permissions).remove(node)) {
                return AdminResult.ok(node + " is not " + (deny ? "denied to " : "granted to ") + displayName
                        + Scopes.span(context) + " — no change.");
            }
            Scopes.tidy(grades(), uuid);
            String warning = ConfigAdmin.persist();
            GradeAdmin.resyncPlayer(server, uuid);
            return AdminResult.ok((deny ? "Removed the denial of " : "Removed ") + node + " from " + displayName
                    + Scopes.span(context)).warn(warning);
        }
        Map<String, Set<String>> target = holder(deny);
        Set<String> nodes = target.get(uuid.toString());
        if (nodes == null || !nodes.remove(node)) {
            return AdminResult.ok(node + " is not " + (deny ? "denied to " : "granted to ") + displayName
                    + " — no change.");
        }
        if (nodes.isEmpty()) target.remove(uuid.toString());
        Expiries.forget(expiries(deny), uuid, node);
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return AdminResult.ok((deny ? "Removed the denial of " : "Removed ") + node + " from " + displayName)
                .warn(warning);
    }

    /**
     * Makes one player refuse {@code gradeName}: it is not read for them, whichever grade of theirs would
     * have inherited it, the default grade included. The refusal removes the grade from their resolution;
     * it never turns what that grade allows into a denial.
     */
    public static AdminResult refuseGrade(MinecraftServer server, UUID uuid, String displayName, String gradeName) {
        return refuseGrade(server, uuid, displayName, gradeName, 0);
    }

    /** A refusal for {@code seconds}, 0 for good; on a refusal already there, the duration replaces its own. */
    public static AdminResult refuseGrade(MinecraftServer server, UUID uuid, String displayName, String gradeName,
                                          long seconds) {
        return refuseGrade(server, uuid, displayName, gradeName, seconds, null);
    }

    /** A refusal in {@code rawContext} only, such as {@code world=the_nether}; blank for everywhere. */
    public static AdminResult refuseGrade(MinecraftServer server, UUID uuid, String displayName, String gradeName,
                                          long seconds, String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null && seconds > 0) return Scopes.timedAndScoped();
        if (!grades().grades.containsKey(gradeName)) return AdminResult.fail("No such grade: " + gradeName);
        if (context != null) {
            GradesConfig.UserScoped scope = Scopes.find(grades(), uuid, context);
            if (scope != null && scope.grades.contains(gradeName)) {
                return AdminResult.fail(displayName + " is assigned " + gradeName + Scopes.span(context)
                        + ": unassign it there instead of refusing it.");
            }
            if (scope != null && scope.refused.contains(gradeName)) {
                return AdminResult.ok(displayName + " already refuses " + gradeName + Scopes.span(context) + " — no change.");
            }
            Scopes.of(grades(), uuid, context).refused.add(gradeName);
            String warning = ConfigAdmin.persist();
            GradeAdmin.resyncPlayer(server, uuid);
            return AdminResult.ok(displayName + " now refuses " + gradeName + Scopes.span(context)).warn(warning)
                    .note("Nothing they hold brings it back there, the default grade included.");
        }
        if (grades().userGrades.getOrDefault(uuid.toString(), List.of()).contains(gradeName)) {
            return AdminResult.fail(displayName + " is assigned " + gradeName + " directly: unassign it instead "
                    + "of refusing it.");
        }
        List<String> refused = grades().userDeniedGrades.computeIfAbsent(uuid.toString(), key -> new ArrayList<>());
        boolean added = !refused.contains(gradeName);
        if (added) refused.add(gradeName);
        boolean timed = Expiries.apply(Expiries.of(grades().userDeniedGradeExpiries, uuid), gradeName, seconds);
        Expiries.tidy(grades().userDeniedGradeExpiries, uuid);
        if (!added && !timed) {
            return AdminResult.ok(displayName + " already refuses " + gradeName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        if (!added) {
            return AdminResult.ok("The refusal of " + gradeName + " by " + displayName + " " + Expiries.became(seconds))
                    .warn(warning);
        }
        return AdminResult.ok(displayName + " now refuses " + gradeName + Expiries.span(seconds)).warn(warning)
                .note("Nothing they hold brings it back, the default grade included.");
    }

    public static AdminResult acceptGrade(MinecraftServer server, UUID uuid, String displayName, String gradeName) {
        return acceptGrade(server, uuid, displayName, gradeName, null);
    }

    /** Stops refusing {@code gradeName} in {@code rawContext}; blank for the refusal held everywhere. */
    public static AdminResult acceptGrade(MinecraftServer server, UUID uuid, String displayName, String gradeName,
                                          String rawContext) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null) {
            GradesConfig.UserScoped scope = Scopes.find(grades(), uuid, context);
            if (scope == null || !scope.refused.remove(gradeName)) {
                return AdminResult.ok(displayName + " does not refuse " + gradeName + Scopes.span(context) + " — no change.");
            }
            Scopes.tidy(grades(), uuid);
            String warning = ConfigAdmin.persist();
            GradeAdmin.resyncPlayer(server, uuid);
            return AdminResult.ok(displayName + " no longer refuses " + gradeName + Scopes.span(context)).warn(warning);
        }
        List<String> refused = grades().userDeniedGrades.get(uuid.toString());
        if (refused == null || !refused.remove(gradeName)) {
            return AdminResult.ok(displayName + " does not refuse " + gradeName + " — no change.");
        }
        if (refused.isEmpty()) grades().userDeniedGrades.remove(uuid.toString());
        Expiries.forget(grades().userDeniedGradeExpiries, uuid, gradeName);
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return AdminResult.ok(displayName + " no longer refuses " + gradeName).warn(warning);
    }

    /** The grades this player refuses, empty when they refuse none. */
    public static List<String> refusedGrades(UUID uuid) {
        return List.copyOf(grades().userDeniedGrades.getOrDefault(uuid.toString(), List.of()));
    }

    /** The player's own nodes of one kind, sorted, empty when they carry none. */
    public static List<String> nodes(UUID uuid, boolean deny) {
        Set<String> nodes = holder(deny).get(uuid.toString());
        return nodes == null ? List.of() : List.copyOf(new TreeSet<>(nodes));
    }

    /**
     * What one player holds limited to a context, by context, sorted: {@code "grade"}, {@code "refused"},
     * {@code "allow"} and {@code "deny"} entries, each as {@code kind:value}. Empty when they hold nothing limited to one.
     */
    public static Map<String, List<String>> scoped(UUID uuid) {
        Map<String, List<String>> out = new java.util.TreeMap<>();
        Map<String, GradesConfig.UserScoped> scopes = grades().userContexts.get(uuid.toString());
        if (scopes == null) return out;
        scopes.forEach((context, scope) -> {
            List<String> entries = new ArrayList<>();
            scope.grades.stream().sorted().forEach(grade -> entries.add("grade:" + grade));
            scope.refused.stream().sorted().forEach(grade -> entries.add("refused:" + grade));
            new TreeSet<>(scope.permissions).forEach(node -> entries.add("allow:" + node));
            new TreeSet<>(scope.deniedPermissions).forEach(node -> entries.add("deny:" + node));
            if (!entries.isEmpty()) out.put(context, entries);
        });
        return out;
    }

    /** Every player who carries a node of their own or a grade, as UUID strings. */
    public static Set<String> knownHolders() {
        Set<String> holders = new LinkedHashSet<>(grades().userGrades.keySet());
        holders.addAll(grades().userDeniedGrades.keySet());
        holders.addAll(grades().userPermissions.keySet());
        holders.addAll(grades().userDeniedPermissions.keySet());
        holders.addAll(grades().userPrefixEntries.keySet());
        holders.addAll(grades().userSuffixEntries.keySet());
        holders.addAll(grades().userContexts.keySet());
        return holders;
    }
}
