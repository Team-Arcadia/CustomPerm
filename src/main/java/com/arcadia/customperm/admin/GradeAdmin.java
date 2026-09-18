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
import com.arcadia.customperm.perm.AdminAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.UsernameCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Internal grades: named sets of ALLOW and DENY nodes assigned to players, online or not. Shared by
 * {@code /customperm grade} and the admin interface, server thread only. Every operation refuses while
 * LuckPerms is the active backend, where grades decide nothing.
 */
public final class GradeAdmin {

    private static final Pattern NAME = Pattern.compile("[0-9A-Za-z_\\-.+]{1,64}");
    private static final int NODE_MAX = 256;

    private GradeAdmin() {
    }

    private static GradesConfig grades() {
        return CustomPerm.configManager.getGrades();
    }

    /** The refusal while LuckPerms owns permissions, or {@code null} when grades apply. */
    public static AdminResult unavailable() {
        return CustomPerm.isLuckPermsActive()
                ? AdminResult.fail("[CustomPerm] Grade commands are disabled — use /lp instead.")
                : null;
    }

    public static AdminResult create(String name) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        if (!validName(name)) {
            return AdminResult.fail("Invalid grade name '" + name + "': use 1 to 64 letters, digits, _ - . or +.");
        }
        if (grades().grades.containsKey(name)) return AdminResult.fail("Grade already exists: " + name);
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = name;
        grades().grades.put(name, grade);
        return AdminResult.ok("Created grade " + name).warn(ConfigAdmin.persist());
    }

    public static AdminResult delete(MinecraftServer server, String name) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        if (grades().grades.remove(name) == null) return AdminResult.fail("No such grade: " + name);
        // A dangling default would silently apply to everyone again if a grade of that name is recreated.
        boolean wasDefault = name.equals(CustomPerm.configManager.getSettings().defaultGrade);
        if (wasDefault) CustomPerm.configManager.getSettings().defaultGrade = "";
        Iterator<Map.Entry<String, List<String>>> iterator = grades().userGrades.entrySet().iterator();
        while (iterator.hasNext()) {
            List<String> assigned = iterator.next().getValue();
            if (assigned == null) {
                iterator.remove();
                continue;
            }
            assigned.removeIf(name::equals);
            if (assigned.isEmpty()) iterator.remove();
        }
        grades().userGradeExpiries.values().forEach(expiries -> expiries.remove(name));
        grades().userGradeExpiries.values().removeIf(Map::isEmpty);
        grades().userContexts.values().forEach(scopes -> {
            scopes.values().forEach(scope -> scope.grades.remove(name));
            scopes.values().removeIf(GradesConfig.Scoped::isEmpty);
        });
        grades().userContexts.values().removeIf(Map::isEmpty);
        List<String> onTracks = new ArrayList<>();
        grades().tracks.forEach((track, rungs) -> {
            if (rungs.remove(name)) onTracks.add(track);
        });
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        AdminResult result = AdminResult.ok("Deleted grade " + name).warn(warning);
        if (!onTracks.isEmpty()) {
            result = result.note("Taken off track(s) " + String.join(", ", onTracks) + ": the rungs around it now follow on.");
        }
        return wasDefault ? result.note("It was the default grade: no grade applies to every player any more.") : result;
    }

    /**
     * Adds an ALLOW or a DENY node. The most specific entry wins (the exact node over {@code a.*} over
     * {@code *}); at the same level a DENY wins, whichever grade it comes from. An explicit value applies
     * to operators too.
     */
    public static AdminResult addNode(MinecraftServer server, String gradeName, String rawNode, boolean deny) {
        return addNode(server, gradeName, rawNode, deny, 0);
    }

    /**
     * {@link #addNode(MinecraftServer, String, String, boolean)} for {@code seconds}, 0 for permanent. On a
     * node already there, the duration replaces what it had: temporary from now, or permanent.
     */
    public static AdminResult addNode(MinecraftServer server, String gradeName, String rawNode, boolean deny,
                                      long seconds) {
        return addNode(server, gradeName, rawNode, deny, seconds, null);
    }

    /**
     * {@link #addNode(MinecraftServer, String, String, boolean, long)} limited to {@code rawContext}, such as
     * {@code world=the_nether}; blank for everywhere. An entry limited to a world is permanent.
     */
    public static AdminResult addNode(MinecraftServer server, String gradeName, String rawNode, boolean deny,
                                      long seconds, String rawContext) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String node = normalizeNode(rawNode);
        if (node == null) return AdminResult.fail("Invalid permission node '" + rawNode.trim() + "'.");
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null && seconds > 0) return Scopes.timedAndScoped();
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (context != null) {
            GradesConfig.Scoped scope = Scopes.of(grade, context);
            if (!(deny ? scope.deniedPermissions : scope.permissions).add(node)) {
                return AdminResult.ok(node + " is already " + (deny ? "denied to " : "granted to ") + gradeName
                        + Scopes.span(context) + " — no change.");
            }
            String warning = ConfigAdmin.persist();
            ConfigAdmin.resyncCommands(server);
            return AdminResult.ok((deny ? "Denied " : "Added ") + node + " -> " + gradeName + Scopes.span(context))
                    .warn(warning);
        }
        Set<String> nodes = deny ? grade.deniedPermissions : grade.permissions;
        boolean added = nodes.add(node);
        boolean timed = Expiries.apply(deny ? grade.deniedPermissionExpiries : grade.permissionExpiries, node, seconds);
        if (!added && !timed) {
            return AdminResult.ok(node + " is already " + (deny ? "denied to " : "granted to ") + gradeName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(added ? (deny ? "Denied " : "Added ") + node + " -> " + gradeName + Expiries.span(seconds)
                : node + " on " + gradeName + " " + Expiries.became(seconds)).warn(warning);
    }

    public static AdminResult removeNode(MinecraftServer server, String gradeName, String rawNode, boolean deny) {
        return removeNode(server, gradeName, rawNode, deny, null);
    }

    /** Removes a node limited to {@code rawContext}; blank removes the one that applies everywhere. */
    public static AdminResult removeNode(MinecraftServer server, String gradeName, String rawNode, boolean deny,
                                         String rawContext) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (context != null) {
            GradesConfig.Scoped scope = grade.contexts.get(context);
            if (scope == null || !(deny ? scope.deniedPermissions : scope.permissions).remove(node)) {
                return AdminResult.ok(node + " is not " + (deny ? "denied to " : "granted to ") + gradeName
                        + Scopes.span(context) + " — no change.");
            }
            Scopes.tidy(grade);
            String warning = ConfigAdmin.persist();
            ConfigAdmin.resyncCommands(server);
            return AdminResult.ok((deny ? "Removed the denial of " : "Removed ") + node + " from " + gradeName
                    + Scopes.span(context)).warn(warning);
        }
        Set<String> nodes = deny ? grade.deniedPermissions : grade.permissions;
        if (!nodes.remove(node)) {
            return AdminResult.ok(node + " is not " + (deny ? "denied to " : "granted to ") + gradeName + " — no change.");
        }
        (deny ? grade.deniedPermissionExpiries : grade.permissionExpiries).remove(node);
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok((deny ? "Removed the denial of " : "Removed ") + node + " from " + gradeName).warn(warning);
    }

    /**
     * Sets the tie-break weight of a grade. It only decides between grades held by the same player that
     * cover a node at the same specificity: the heaviest wins, and a DENY still wins between equal weights.
     * A more specific node in a lighter grade keeps winning, so a weight cannot be used to bypass one.
     */
    public static AdminResult setWeight(MinecraftServer server, String gradeName, int weight) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (grade.weight == weight) {
            return AdminResult.ok(gradeName + " already weighs " + weight + " — no change.");
        }
        grade.weight = weight;
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok("Weight of " + gradeName + " set to " + weight).warn(warning)
                .note("Only breaks ties at the same specificity: an exact node in a lighter grade still wins.");
    }

    /**
     * Sets or clears the chat prefix or suffix of a grade. Blank clears it. Shown only while name
     * decoration is on, which the result says, since a prefix nobody sees looks like one that failed.
     */
    public static AdminResult setChat(MinecraftServer server, String gradeName, boolean suffix, String text) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        String value = text == null || text.isBlank() ? null : text;
        String problem = com.arcadia.customperm.chat.LegacyText.problem(value);
        if (problem != null) return AdminResult.fail("Invalid " + (suffix ? "suffix" : "prefix") + ": " + problem);
        String what = suffix ? "Suffix" : "Prefix";
        if (java.util.Objects.equals(suffix ? grade.suffix : grade.prefix, value)) {
            return AdminResult.ok(what + " of " + gradeName + " unchanged.");
        }
        if (suffix) grade.suffix = value;
        else grade.prefix = value;
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return decorationNote(AdminResult.ok(value == null ? what + " of " + gradeName + " cleared."
                : what + " of " + gradeName + " set to \"" + value + "\".").warn(warning));
    }

    /** Says a prefix shows nowhere while decoration is off. Shared with {@link UserAdmin}. */
    static AdminResult decorationNote(AdminResult result) {
        return CustomPerm.configManager.getSettings().decorateNames ? result
                : result.note("Names are not decorated yet: /customperm names on to show prefixes and suffixes.");
    }

    /**
     * Makes {@code gradeName} inherit {@code parentName}: where the grade says nothing as precise about a
     * node, what its parents say applies, nearest first. A cycle is refused here rather than left to the
     * resolver, which stops one silently: an admin who asks for a cycle has made a mistake worth naming.
     */
    public static AdminResult addParent(MinecraftServer server, String gradeName, String parentName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (!grades().grades.containsKey(parentName)) return AdminResult.fail("No such grade: " + parentName);
        if (gradeName.equals(parentName)) return AdminResult.fail("A grade cannot inherit from itself.");
        if (grade.parents.contains(parentName)) {
            return AdminResult.ok(gradeName + " already inherits " + parentName + " — no change.");
        }
        if (grade.deniedParents.contains(parentName)) {
            return AdminResult.fail(gradeName + " refuses " + parentName + ": remove that refusal first, "
                    + "or the file would say both at once.");
        }
        List<String> loop = inheritancePath(parentName, gradeName);
        if (loop != null) {
            return AdminResult.fail("Refused: " + String.join(" inherits ", loop) + ", so " + gradeName
                    + " cannot inherit " + parentName + ".");
        }
        grade.parents.add(parentName);
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(gradeName + " now inherits " + parentName).warn(warning)
                .note("A node set on " + gradeName + " itself still wins over the same node inherited.");
    }

    public static AdminResult removeParent(MinecraftServer server, String gradeName, String parentName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (!grade.parents.remove(parentName)) {
            return AdminResult.ok(gradeName + " does not inherit " + parentName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(gradeName + " no longer inherits " + parentName).warn(warning);
    }

    /**
     * Makes {@code gradeName} refuse {@code parentName}, so nothing it inherits brings that grade back. The
     * refusal removes the grade from this chain; it never turns what that grade allows into a denial.
     */
    public static AdminResult denyParent(MinecraftServer server, String gradeName, String parentName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (!grades().grades.containsKey(parentName)) return AdminResult.fail("No such grade: " + parentName);
        if (gradeName.equals(parentName)) return AdminResult.fail("A grade cannot refuse itself.");
        if (grade.parents.contains(parentName)) {
            return AdminResult.fail(gradeName + " inherits " + parentName + " directly: remove that parent "
                    + "instead of refusing it.");
        }
        if (grade.deniedParents.contains(parentName)) {
            return AdminResult.ok(gradeName + " already refuses " + parentName + " — no change.");
        }
        grade.deniedParents.add(parentName);
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(gradeName + " now refuses " + parentName).warn(warning)
                .note("Nothing " + gradeName + " inherits brings it back. Other grades a player holds are unaffected.");
    }

    public static AdminResult allowParent(MinecraftServer server, String gradeName, String parentName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        if (!grade.deniedParents.remove(parentName)) {
            return AdminResult.ok(gradeName + " does not refuse " + parentName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(gradeName + " no longer refuses " + parentName).warn(warning);
    }

    /** The grades {@code gradeName} refuses, empty for an unknown grade. */
    public static List<String> deniedParents(String gradeName) {
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        return grade == null ? List.of() : List.copyOf(grade.deniedParents);
    }

    /** The grades {@code gradeName} inherits directly, nearest first; empty for an unknown grade. */
    public static List<String> parents(String gradeName) {
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        return grade == null ? List.of() : List.copyOf(grade.parents);
    }

    /**
     * The inheritance path from {@code from} up to {@code target}, or {@code null} when it does not lead
     * there. Breadth-first, so the path named in a refusal is the shortest one.
     */
    private static List<String> inheritancePath(String from, String target) {
        Map<String, String> reachedFrom = new java.util.LinkedHashMap<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        reachedFrom.put(from, null);
        queue.add(from);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (name.equals(target)) {
                List<String> path = new ArrayList<>();
                for (String step = name; step != null; step = reachedFrom.get(step)) path.add(step);
                java.util.Collections.reverse(path);
                return path;
            }
            GradesConfig.Grade grade = grades().grades.get(name);
            if (grade == null) continue;
            for (String parent : grade.parents) {
                if (parent == null || reachedFrom.containsKey(parent)) continue;
                reachedFrom.put(parent, name);
                queue.add(parent);
            }
        }
        return null;
    }

    public static AdminResult assign(MinecraftServer server, GameProfile profile, String gradeName) {
        return assign(server, profile, gradeName, 0);
    }

    /** Assigns a grade for {@code seconds}, 0 for good; on a grade already held, the duration replaces its own. */
    public static AdminResult assign(MinecraftServer server, GameProfile profile, String gradeName, long seconds) {
        return assign(server, profile, gradeName, seconds, null);
    }

    /** Assigns a grade that applies only in {@code rawContext}, such as {@code world=the_nether}; blank for everywhere. */
    public static AdminResult assign(MinecraftServer server, GameProfile profile, String gradeName, long seconds,
                                     String rawContext) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null && seconds > 0) return Scopes.timedAndScoped();
        if (!grades().grades.containsKey(gradeName)) return AdminResult.fail("No such grade: " + gradeName);
        if (grades().userDeniedGrades.getOrDefault(profile.getId().toString(), List.of()).contains(gradeName)) {
            return AdminResult.fail(profile.getName() + " refuses " + gradeName + ": remove that refusal first, "
                    + "or the file would say both at once.");
        }
        if (context != null) {
            List<String> scoped = Scopes.of(grades(), profile.getId(), context).grades;
            if (scoped.contains(gradeName)) {
                Scopes.tidy(grades(), profile.getId());
                return AdminResult.ok(profile.getName() + " is already assigned to " + gradeName + Scopes.span(context)
                        + " — no change.");
            }
            scoped.add(gradeName);
            String warning = ConfigAdmin.persist();
            resyncPlayer(server, profile.getId());
            AdminResult result = AdminResult.ok("Assigned " + gradeName + " -> " + profile.getName() + Scopes.span(context))
                    .warn(warning);
            return grades().userGrades.getOrDefault(profile.getId().toString(), List.of()).contains(gradeName)
                    ? result.note("They also hold it everywhere, which already covers that world.")
                    : result;
        }
        List<String> list = grades().userGrades.computeIfAbsent(profile.getId().toString(), k -> new ArrayList<>());
        boolean added = !list.contains(gradeName);
        if (added) list.add(gradeName);
        boolean timed = Expiries.apply(Expiries.of(grades().userGradeExpiries, profile.getId()), gradeName, seconds);
        Expiries.tidy(grades().userGradeExpiries, profile.getId());
        if (!added && !timed) {
            return AdminResult.ok(profile.getName() + " is already assigned to " + gradeName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        resyncPlayer(server, profile.getId());
        return AdminResult.ok(added ? "Assigned " + gradeName + " -> " + profile.getName() + Expiries.span(seconds)
                : gradeName + " for " + profile.getName() + " " + Expiries.became(seconds)).warn(warning);
    }

    public static AdminResult unassign(MinecraftServer server, UUID uuid, String displayName, String gradeName) {
        return unassign(server, uuid, displayName, gradeName, null);
    }

    /** Unassigns a grade held in {@code rawContext} only; blank unassigns the one held everywhere. */
    public static AdminResult unassign(MinecraftServer server, UUID uuid, String displayName, String gradeName,
                                       String rawContext) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String context = Scopes.parse(rawContext);
        if (context == Scopes.INVALID) return Scopes.invalid(rawContext);
        if (context != null) {
            GradesConfig.UserScoped scope = Scopes.find(grades(), uuid, context);
            if (scope == null || !scope.grades.remove(gradeName)) {
                return AdminResult.ok(displayName + " is not assigned to " + gradeName + Scopes.span(context)
                        + " — no change.");
            }
            Scopes.tidy(grades(), uuid);
            String warning = ConfigAdmin.persist();
            resyncPlayer(server, uuid);
            return AdminResult.ok("Unassigned " + gradeName + " from " + displayName + Scopes.span(context)).warn(warning);
        }
        List<String> list = grades().userGrades.get(uuid.toString());
        if (list == null || !list.remove(gradeName)) {
            return AdminResult.ok(displayName + " is not assigned to " + gradeName + " — no change.");
        }
        if (list.isEmpty()) grades().userGrades.remove(uuid.toString());
        Expiries.forget(grades().userGradeExpiries, uuid, gradeName);
        String warning = ConfigAdmin.persist();
        resyncPlayer(server, uuid);
        return AdminResult.ok("Unassigned " + gradeName + " from " + displayName).warn(warning);
    }

    /**
     * Makes {@code gradeName} apply to every player, below their own grades; an empty name clears it.
     * This is how a player made operator by mistake is restricted: they have no grade of their own.
     */
    public static AdminResult setDefault(MinecraftServer server, String gradeName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        var settings = CustomPerm.configManager.getSettings();
        String name = gradeName.trim();
        if (name.isEmpty()) {
            if (settings.defaultGrade.isEmpty()) return AdminResult.ok("No default grade is set. No change.");
            String previous = settings.defaultGrade;
            settings.defaultGrade = "";
            String warning = ConfigAdmin.persist();
            ConfigAdmin.resyncCommands(server);
            return AdminResult.ok(previous + " no longer applies to every player.").warn(warning);
        }
        if (!grades().grades.containsKey(name)) return AdminResult.fail("No such grade: " + name);
        if (name.equals(settings.defaultGrade)) return AdminResult.ok(name + " is already the default grade. No change.");
        settings.defaultGrade = name;
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        AdminResult result = AdminResult.ok(name + " now applies to every player, below their own grades.").warn(warning)
                .note("Operators included: a node it denies is refused to them unless one of their own grades allows it.");
        return CustomPerm.gatesAllCommands() ? result
                : result.note("Commands that are not exposed ignore grades until /customperm command gateall true.");
    }

    /**
     * Runs a grade or per-player node change for {@code actor} and undoes it if it takes away the actor's own
     * access to the grade commands ({@code customperm.admin} and {@code customperm.manage.grades}): a denied {@code *} in their grade,
     * or a removed allow, would otherwise lock the admin out of the very command that can repair it. The console is never checked, it cannot be
     * locked out.
     */
    public static AdminResult guarded(CommandSourceStack actor, MinecraftServer server, Supplier<AdminResult> change) {
        if (!(actor.getEntity() instanceof ServerPlayer player) || !canManageGrades(player)) {
            return change.get();
        }
        GradesConfig saved = copy(grades());
        String savedDefault = CustomPerm.configManager.getSettings().defaultGrade;
        AdminResult result = change.get();
        if (!result.success() || canManageGrades(player)) return result;

        restore(grades(), saved);
        CustomPerm.configManager.getSettings().defaultGrade = savedDefault;
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        CustomPerm.LOGGER.warn("[CustomPerm] Refused a grade change by {}: it would have taken away their own access to grades.",
                player.getGameProfile().getName());
        return AdminResult.fail("Refused: you would lose customperm.admin or customperm.manage.grades yourself. Keep them "
                + "allowed in one of your grades, or make this change from the console.").warn(warning);
    }

    /** What the guard protects: the admin can still run the grade commands that would undo the change. */
    private static boolean canManageGrades(ServerPlayer player) {
        return AdminAccess.canManage(player, com.arcadia.customperm.perm.PermissionNodes.MANAGE_GRADES);
    }

    private static GradesConfig copy(GradesConfig source) {
        GradesConfig copy = new GradesConfig();
        source.grades.forEach((name, grade) -> {
            GradesConfig.Grade g = new GradesConfig.Grade();
            g.name = grade.name;
            g.permissions = new HashSet<>(grade.permissions);
            g.deniedPermissions = new HashSet<>(grade.deniedPermissions);
            g.weight = grade.weight;
            g.parents = new ArrayList<>(grade.parents);
            g.deniedParents = new ArrayList<>(grade.deniedParents);
            g.prefix = grade.prefix;
            g.suffix = grade.suffix;
            g.permissionExpiries = new java.util.HashMap<>(grade.permissionExpiries);
            g.deniedPermissionExpiries = new java.util.HashMap<>(grade.deniedPermissionExpiries);
            g.contexts = Scopes.copy(grade.contexts);
            copy.grades.put(name, g);
        });
        source.userGrades.forEach((uuid, list) -> copy.userGrades.put(uuid, new ArrayList<>(list)));
        source.userDeniedGrades.forEach((uuid, list) -> copy.userDeniedGrades.put(uuid, new ArrayList<>(list)));
        source.userPermissions.forEach((uuid, nodes) -> copy.userPermissions.put(uuid, new HashSet<>(nodes)));
        source.userDeniedPermissions.forEach((uuid, nodes) -> copy.userDeniedPermissions.put(uuid, new HashSet<>(nodes)));
        copy.userPrefixes.putAll(source.userPrefixes);
        copy.userSuffixes.putAll(source.userSuffixes);
        copyExpiries(source.userPermissionExpiries, copy.userPermissionExpiries);
        copyExpiries(source.userDeniedPermissionExpiries, copy.userDeniedPermissionExpiries);
        copyExpiries(source.userGradeExpiries, copy.userGradeExpiries);
        copyExpiries(source.userDeniedGradeExpiries, copy.userDeniedGradeExpiries);
        copy.userContexts = Scopes.copyUsers(source.userContexts);
        return copy;
    }

    private static void restore(GradesConfig target, GradesConfig saved) {
        target.grades.clear();
        target.grades.putAll(saved.grades);
        target.userGrades.clear();
        target.userGrades.putAll(saved.userGrades);
        target.userDeniedGrades.clear();
        target.userDeniedGrades.putAll(saved.userDeniedGrades);
        target.userPermissions.clear();
        target.userPermissions.putAll(saved.userPermissions);
        target.userDeniedPermissions.clear();
        target.userDeniedPermissions.putAll(saved.userDeniedPermissions);
        target.userPrefixes.clear();
        target.userPrefixes.putAll(saved.userPrefixes);
        target.userSuffixes.clear();
        target.userSuffixes.putAll(saved.userSuffixes);
        target.userPermissionExpiries.clear();
        target.userPermissionExpiries.putAll(saved.userPermissionExpiries);
        target.userDeniedPermissionExpiries.clear();
        target.userDeniedPermissionExpiries.putAll(saved.userDeniedPermissionExpiries);
        target.userGradeExpiries.clear();
        target.userGradeExpiries.putAll(saved.userGradeExpiries);
        target.userDeniedGradeExpiries.clear();
        target.userDeniedGradeExpiries.putAll(saved.userDeniedGradeExpiries);
        target.userContexts.clear();
        target.userContexts.putAll(saved.userContexts);
    }

    private static void copyExpiries(Map<String, Map<String, Long>> from, Map<String, Map<String, Long>> to) {
        from.forEach((uuid, byKey) -> to.put(uuid, new java.util.HashMap<>(byKey)));
    }

    /** Outcome of resolving a player name: the profile, or why there is none. */
    public record Resolution(Optional<GameProfile> profile, String problem) {
    }

    /**
     * Resolves a player known to this server by name: online players, then any player who has joined
     * before (NeoForge's username cache). Never asks the session service: on an offline-mode server
     * that lookup invents a profile for any name, so a typo would silently grant a grade to nobody.
     * An offline name carried by several known accounts (a name taken over after a rename) is refused
     * rather than guessed.
     */
    public static Resolution resolvePlayer(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new Resolution(Optional.of(online.getGameProfile()), null);
        List<Map.Entry<UUID, String>> matches = UsernameCache.getMap().entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(name))
                .toList();
        if (matches.isEmpty()) {
            return new Resolution(Optional.empty(), "Unknown player '" + name
                    + "': grades can be assigned to players online or who joined this server before.");
        }
        if (matches.size() > 1) {
            return new Resolution(Optional.empty(), "Several accounts have used the name '" + name
                    + "': assign the grade while the player is online.");
        }
        Map.Entry<UUID, String> match = matches.get(0);
        return new Resolution(Optional.of(new GameProfile(match.getKey(), match.getValue())), null);
    }

    /** Names of every player known to this server, for suggestions. */
    public static List<String> knownPlayerNames(MinecraftServer server) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        server.getPlayerList().getPlayers().forEach(p -> names.add(p.getGameProfile().getName()));
        names.addAll(UsernameCache.getMap().values());
        return new ArrayList<>(names);
    }

    /** Best display name for an assigned UUID, without any network lookup. */
    public static String displayName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        String known = UsernameCache.getLastKnownUsername(uuid);
        return known != null ? known : uuid.toString();
    }

    /** Whether a grade could be called this. Shared with {@link ImportAdmin}, which reads names from elsewhere. */
    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** The node as it is stored, or {@code null} when it cannot be one. Shared with {@link UserAdmin}. */
    static String normalizeNode(String rawNode) {
        String node = rawNode.trim();
        if (node.isEmpty() || node.length() > NODE_MAX || node.chars().anyMatch(Character::isWhitespace)) return null;
        return node;
    }

    static void resyncPlayer(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;
        server.getCommands().sendCommands(player);
        com.arcadia.customperm.chat.NameDecoration.refresh(player);
    }
}
