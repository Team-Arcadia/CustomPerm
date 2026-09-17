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
        if (!NAME.matcher(name).matches()) {
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
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        AdminResult result = AdminResult.ok("Deleted grade " + name).warn(warning);
        return wasDefault ? result.note("It was the default grade: no grade applies to every player any more.") : result;
    }

    /**
     * Adds an ALLOW or a DENY node. The most specific entry wins (the exact node over {@code a.*} over
     * {@code *}); at the same level a DENY wins, whichever grade it comes from. An explicit value applies
     * to operators too.
     */
    public static AdminResult addNode(MinecraftServer server, String gradeName, String rawNode, boolean deny) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
        if (node.isEmpty() || node.length() > NODE_MAX || node.chars().anyMatch(Character::isWhitespace)) {
            return AdminResult.fail("Invalid permission node '" + node + "'.");
        }
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        Set<String> nodes = deny ? grade.deniedPermissions : grade.permissions;
        if (!nodes.add(node)) {
            return AdminResult.ok(node + " is already " + (deny ? "denied to " : "granted to ") + gradeName + " — no change.");
        }
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok((deny ? "Denied " : "Added ") + node + " -> " + gradeName).warn(warning);
    }

    public static AdminResult removeNode(MinecraftServer server, String gradeName, String rawNode, boolean deny) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        String node = rawNode.trim();
        GradesConfig.Grade grade = grades().grades.get(gradeName);
        if (grade == null) return AdminResult.fail("No such grade: " + gradeName);
        Set<String> nodes = deny ? grade.deniedPermissions : grade.permissions;
        if (!nodes.remove(node)) {
            return AdminResult.ok(node + " is not " + (deny ? "denied to " : "granted to ") + gradeName + " — no change.");
        }
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

    public static AdminResult assign(MinecraftServer server, GameProfile profile, String gradeName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        if (!grades().grades.containsKey(gradeName)) return AdminResult.fail("No such grade: " + gradeName);
        List<String> list = grades().userGrades.computeIfAbsent(profile.getId().toString(), k -> new ArrayList<>());
        if (list.contains(gradeName)) {
            return AdminResult.ok(profile.getName() + " is already assigned to " + gradeName + " — no change.");
        }
        list.add(gradeName);
        String warning = ConfigAdmin.persist();
        resyncPlayer(server, profile.getId());
        return AdminResult.ok("Assigned " + gradeName + " -> " + profile.getName()).warn(warning);
    }

    public static AdminResult unassign(MinecraftServer server, UUID uuid, String displayName, String gradeName) {
        AdminResult refusal = unavailable();
        if (refusal != null) return refusal;
        List<String> list = grades().userGrades.get(uuid.toString());
        if (list == null || !list.remove(gradeName)) {
            return AdminResult.ok(displayName + " is not assigned to " + gradeName + " — no change.");
        }
        if (list.isEmpty()) grades().userGrades.remove(uuid.toString());
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
     * Runs a grade change for {@code actor} and undoes it if it takes away the actor's own access to the grade
     * commands ({@code customperm.admin} and {@code customperm.manage.grades}): a denied {@code *} in their grade,
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
            copy.grades.put(name, g);
        });
        source.userGrades.forEach((uuid, list) -> copy.userGrades.put(uuid, new ArrayList<>(list)));
        return copy;
    }

    private static void restore(GradesConfig target, GradesConfig saved) {
        target.grades.clear();
        target.grades.putAll(saved.grades);
        target.userGrades.clear();
        target.userGrades.putAll(saved.userGrades);
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

    private static void resyncPlayer(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) server.getCommands().sendCommands(player);
    }
}
