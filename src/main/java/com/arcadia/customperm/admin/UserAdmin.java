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
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String node = GradeAdmin.normalizeNode(rawNode);
        if (node == null) return AdminResult.fail("Invalid permission node '" + rawNode.trim() + "'.");
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

    /** Sets or clears the prefix or suffix one player carries above their grades. Blank clears it. */
    public static AdminResult setChat(MinecraftServer server, UUID uuid, String displayName, boolean suffix,
                                      String text) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String value = text == null || text.isBlank() ? null : text;
        String problem = com.arcadia.customperm.chat.LegacyText.problem(value);
        if (problem != null) return AdminResult.fail("Invalid " + (suffix ? "suffix" : "prefix") + ": " + problem);
        Map<String, String> texts = suffix ? grades().userSuffixes : grades().userPrefixes;
        String what = suffix ? "Suffix" : "Prefix";
        String before = value == null ? texts.remove(uuid.toString()) : texts.put(uuid.toString(), value);
        if (java.util.Objects.equals(before, value)) return AdminResult.ok(what + " of " + displayName + " unchanged.");
        String warning = ConfigAdmin.persist();
        GradeAdmin.resyncPlayer(server, uuid);
        return GradeAdmin.decorationNote(AdminResult.ok(value == null ? what + " of " + displayName + " cleared."
                : what + " of " + displayName + " set to \"" + value + "\".").warn(warning));
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

    /** The prefix or suffix a player carries themselves, {@code null} for none. */
    public static String chat(UUID uuid, boolean suffix) {
        return (suffix ? grades().userSuffixes : grades().userPrefixes).get(uuid.toString());
    }

    /** Removes an ALLOW or a DENY node from one player; the entry goes with its last node. */
    public static AdminResult removeNode(MinecraftServer server, UUID uuid, String displayName, String rawNode,
                                         boolean deny) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
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
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        if (!grades().grades.containsKey(gradeName)) return AdminResult.fail("No such grade: " + gradeName);
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
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
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

    /** Every player who carries a node of their own or a grade, as UUID strings. */
    public static Set<String> knownHolders() {
        Set<String> holders = new LinkedHashSet<>(grades().userGrades.keySet());
        holders.addAll(grades().userDeniedGrades.keySet());
        holders.addAll(grades().userPermissions.keySet());
        holders.addAll(grades().userDeniedPermissions.keySet());
        holders.addAll(grades().userPrefixes.keySet());
        holders.addAll(grades().userSuffixes.keySet());
        return holders;
    }
}
