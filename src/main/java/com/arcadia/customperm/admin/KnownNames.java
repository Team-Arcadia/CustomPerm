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
import com.arcadia.customperm.cluster.Cluster;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.Contexts;
import com.arcadia.customperm.perm.ModPermissions;
import com.arcadia.customperm.perm.PermissionNodes;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The names an admin may mean when typing in a field: permission nodes, contexts, commands, meta keys.
 * One source for the command completion and for the interface, so both propose the same thing.
 *
 * <p>Read from the live configuration on every call. Server thread only, like the maps it reads.
 */
public final class KnownNames {

    /** A few durations, to show the form; any combination of w, d, h, m and s is accepted. */
    public static final List<String> DURATIONS = List.of("1h", "12h", "1d", "7d", "30d");

    private KnownNames() {
    }

    /**
     * Known permission nodes: customperm.command.* and customperm.alias.* for what is configured, the
     * admin nodes, nodes other mods declared, and every node already allowed or denied by a grade.
     */
    public static Set<String> nodes(MinecraftServer server) {
        var config = CustomPerm.configManager;
        Set<String> nodes = new TreeSet<>(PermissionNodes.all());
        for (String c : config.getCommands().grantedCommands) nodes.add("customperm.command." + c);
        if (CustomPerm.gatesAllCommands() && server != null) {
            for (String root : commandRoots(server)) nodes.add("customperm.command." + root);
        }
        // Nodes other mods declared through NeoForge: grantable here once CustomPerm answers them.
        nodes.addAll(ModPermissions.declaredNodes());
        for (String a : config.getAliases().aliases.keySet()) nodes.add("customperm.alias." + a);
        for (GradesConfig.Grade g : config.getGrades().grades.values()) {
            nodes.addAll(g.permissions);
            nodes.addAll(g.deniedPermissions);
            for (GradesConfig.GradeScoped scoped : g.contexts.values()) {
                nodes.addAll(scoped.permissions);
                nodes.addAll(scoped.deniedPermissions);
            }
        }
        return nodes;
    }

    /**
     * {@code world=the_nether} and the like, one per loaded dimension, then the game modes, the static
     * contexts, and {@code server=} for each cluster member heard, written as the commands take them.
     */
    public static List<String> contexts(MinecraftServer server) {
        if (server == null) return List.of();
        Set<String> contexts = new LinkedHashSet<>();
        for (var level : server.getAllLevels()) {
            contexts.add(Contexts.WORLD + "=" + Contexts.luckPermsWorld(level.dimension().location().toString()));
        }
        Contexts.GAMEMODES.forEach(mode -> contexts.add(Contexts.GAMEMODE + "=" + mode));
        contexts.addAll(ContextAdmin.describe());
        for (String member : Cluster.memberNames()) contexts.add(Contexts.SERVER + "=" + member);
        return new ArrayList<>(contexts);
    }

    /** The loaded worlds by id alone, as {@code prefix <holder> in <world>} takes them. */
    public static List<String> worldIds(MinecraftServer server) {
        if (server == null) return List.of();
        return server.levelKeys().stream().map(key -> key.location().toString()).toList();
    }

    /** Every dispatcher root outside {@code /customperm}. */
    public static List<String> commandRoots(MinecraftServer server) {
        if (server == null) return List.of();
        List<String> roots = new ArrayList<>();
        for (var node : server.getCommands().getDispatcher().getRoot().getChildren()) {
            if (!node.getName().equals("customperm")) roots.add(node.getName());
        }
        roots.sort(String.CASE_INSENSITIVE_ORDER);
        return roots;
    }

    /** Meta keys set anywhere, on a grade or a player, everywhere or in a context. */
    public static Set<String> metaKeys() {
        Set<String> keys = new TreeSet<>();
        metaValues().forEach((key, values) -> keys.add(key));
        return keys;
    }

    /** Meta key to the values it is set to anywhere, both sorted. */
    public static Map<String, Set<String>> metaValues() {
        var config = CustomPerm.configManager.getGrades();
        Map<String, Set<String>> values = new java.util.TreeMap<>();
        for (GradesConfig.Grade g : config.grades.values()) {
            collect(values, g.meta);
            g.contexts.values().forEach(scoped -> collect(values, scoped.meta));
        }
        config.userMeta.values().forEach(meta -> collect(values, meta));
        config.userContexts.values().forEach(scopes -> scopes.values().forEach(scoped -> collect(values, scoped.meta)));
        return values;
    }

    private static void collect(Map<String, Set<String>> into, Map<String, String> meta) {
        if (meta == null) return;
        meta.forEach((key, value) -> into.computeIfAbsent(key, k -> new TreeSet<>()).add(value));
    }

    /** Prefix and suffix texts in use, on grades and players, codes included. */
    public static Set<String> chatTexts() {
        var config = CustomPerm.configManager.getGrades();
        Set<String> texts = new TreeSet<>();
        for (GradesConfig.Grade g : config.grades.values()) {
            addTexts(texts, g.prefixes);
            addTexts(texts, g.suffixes);
            g.contexts.values().forEach(scoped -> {
                addTexts(texts, scoped.prefixes);
                addTexts(texts, scoped.suffixes);
            });
        }
        config.userPrefixEntries.values().forEach(list -> addTexts(texts, list));
        config.userSuffixEntries.values().forEach(list -> addTexts(texts, list));
        config.userContexts.values().forEach(scopes -> scopes.values().forEach(scoped -> {
            addTexts(texts, scoped.prefixes);
            addTexts(texts, scoped.suffixes);
        }));
        return texts;
    }

    private static void addTexts(Set<String> into, Collection<GradesConfig.ChatEntry> entries) {
        if (entries == null) return;
        for (GradesConfig.ChatEntry entry : entries) {
            if (entry.text != null && !entry.text.isEmpty()) into.add(entry.text);
        }
    }
}
