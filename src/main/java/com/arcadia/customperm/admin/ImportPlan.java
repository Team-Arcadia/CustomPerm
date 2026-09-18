/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an import from LuckPerms would write, and what it would leave behind. Plain Java with no
 * {@code net.luckperms.api} type: {@code perm/lp/LuckPermsImport} builds one of these, this package
 * applies it, and the interface shows its report. Reading and writing are separated on purpose, so the
 * admin sees the whole thing before anything is written.
 *
 * <p>A plan is a description, never a promise: applying it can still refuse, for instance when the
 * change would take away the admin's own access to the grades.
 */
public record ImportPlan(List<Grade> grades, List<Player> players, Map<String, List<String>> tracks,
                         Set<String> exposeCommands,
                         List<String> skipped, Counts counts) {

    public static final ImportPlan EMPTY =
            new ImportPlan(List.of(), List.of(), Map.of(), Set.of(), List.of(), Counts.NONE);

    /**
     * One LuckPerms group as the grade it would become; {@code chat} holds its prefixes and suffixes.
     * {@code expiries} holds the temporary entries in epoch seconds, keyed {@code allow:<node>},
     * {@code deny:<node>}, {@code grade:<parent>} or {@code refuse:<parent>}, and {@code scoped} the nodes
     * limited to a world.
     */
    public record Grade(String name, int weight, List<String> parents, List<String> deniedParents,
                        Set<String> allow, Set<String> deny, List<ChatGrant> chat,
                        Map<String, Long> expiries, List<ScopedGrant> scoped, List<MetaGrant> meta) {

        /** One without meta. */
        public Grade(String name, int weight, List<String> parents, List<String> deniedParents, Set<String> allow,
                     Set<String> deny, List<ChatGrant> chat, Map<String, Long> expiries, List<ScopedGrant> scoped) {
            this(name, weight, parents, deniedParents, allow, deny, chat, expiries, scoped, List.of());
        }
    }

    /**
     * One LuckPerms user as what they would hold; {@code name} is display only, the UUID is the key.
     * {@code expiries} is keyed {@code allow:}, {@code deny:}, {@code grade:} or {@code refuse:}, and
     * {@code scoped} holds the nodes and grades limited to a world.
     */
    public record Player(String uuid, String name, List<String> grades, List<String> deniedGrades,
                         Set<String> allow, Set<String> deny, List<ChatGrant> chat,
                         Map<String, Long> expiries, List<ScopedGrant> scoped, List<MetaGrant> meta) {

        /** One without meta. */
        public Player(String uuid, String name, List<String> grades, List<String> deniedGrades, Set<String> allow,
                      Set<String> deny, List<ChatGrant> chat, Map<String, Long> expiries, List<ScopedGrant> scoped) {
            this(uuid, name, grades, deniedGrades, allow, deny, chat, expiries, scoped, List.of());
        }
    }

    /**
     * What the report counts. The skipped ones matter as much as the imported ones: a permission that
     * silently disappears in a migration is how a server ends up open or locked without anyone knowing.
     */
    public record Counts(int groups, int players, int nodes, int translated, int timed, int worlds, int commands,
                         int contextual, int foreign, int other) {

        public static final Counts NONE = new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public int skipped() {
            return contextual + foreign + other;
        }
    }

    public boolean isEmpty() {
        return grades.isEmpty() && players.isEmpty() && tracks.isEmpty();
    }

    /**
     * The report, as the lines the admin reads before confirming. Written as sentences rather than a
     * table: the interface shows them as they are and so does the console.
     */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add(counts.groups + " group(s) become grades, " + counts.players + " player(s) keep what they hold.");
        lines.add(counts.nodes + " node(s) imported, " + counts.translated + " of them translated from "
                + "minecraft.command to customperm.command"
                + (counts.timed == 0 ? "" : ", " + counts.timed + " temporary, carried with their expiry")
                + (counts.worlds == 0 ? "." : ", " + counts.worlds + " limited to a world, carried with it."));
        if (!tracks.isEmpty()) {
            lines.add(tracks.size() + " track(s) carried with their rungs: " + String.join(", ", new java.util.TreeSet<>(tracks.keySet()))
                    + ".");
        }
        if (!exposeCommands.isEmpty()) {
            lines.add(exposeCommands.size() + " command(s) also exposed, without which those nodes would grant "
                    + "nothing: " + String.join(", ", exposeCommands) + ".");
        }
        if (counts.skipped() == 0) {
            lines.add("Nothing is left behind.");
        } else {
            lines.add(counts.skipped() + " entrie(s) are left behind: "
                    + counts.contextual + " in a context not read here, " + counts.foreign + " belonging to other mods, "
                    + counts.other + " of a kind CustomPerm has no equivalent for (display name).");
        }
        lines.addAll(skipped);
        return lines;
    }

    /**
     * The node CustomPerm would store for a LuckPerms permission key, or {@code null} when it governs
     * nothing here. {@code customperm.*} keys carry over as they are, {@code minecraft.command.<x>}
     * becomes {@code customperm.command.<x>}, the global wildcard stays itself, and a node another mod
     * reads is not stored: once LuckPerms is gone nothing would read it.
     */
    public static String translate(String key) {
        if (key == null) return null;
        String node = key.trim();
        if (node.isEmpty()) return null;
        if (node.equals("*") || node.startsWith("customperm.")) return node;
        if (node.startsWith("minecraft.command.")) {
            return "customperm.command." + node.substring("minecraft.command.".length());
        }
        return null;
    }

    /**
     * {@link #translate(String)}, also keeping a node another mod declared through NeoForge's permission API,
     * which CustomPerm answers, and a wildcard over such nodes ({@code somemod.*}). A node no mod declared stays
     * out: once LuckPerms is gone nothing would read it.
     *
     * @param declared the names of the boolean nodes mods declared
     */
    public static String translate(String key, java.util.Collection<String> declared) {
        String node = translate(key);
        if (node != null || key == null) return node;
        String clean = key.trim();
        if (declared.contains(clean)) return clean;
        if (clean.endsWith(".*") && clean.length() > 2) {
            String prefix = clean.substring(0, clean.length() - 1);
            for (String name : declared) {
                if (name.startsWith(prefix)) return clean;
            }
        }
        return null;
    }

    /**
     * The command a key opens, or {@code null} when it names none. A node on a command that is not
     * exposed grants nothing, so an import that does not expose it imports a permission that does nothing.
     */
    public static String exposedCommand(String key) {
        String node = translate(key);
        if (node == null || !node.startsWith("customperm.command.")) return null;
        String command = node.substring("customperm.command.".length());
        return command.isEmpty() || command.contains("*") ? null : command;
    }

    /** A mutable builder, since the reader fills a plan group by group and user by user. */
    public static final class Builder {
        private final List<Grade> grades = new ArrayList<>();
        private final List<Player> players = new ArrayList<>();
        private final Map<String, List<String>> tracks = new java.util.LinkedHashMap<>();
        private final Set<String> commands = new LinkedHashSet<>();
        private final List<String> skipped = new ArrayList<>();
        private int nodes;
        private int translated;
        private int timed;
        private int worlds;
        private int contextual;
        private int foreign;
        private int other;

        public void grade(Grade grade) {
            grades.add(grade);
        }

        public void player(Player player) {
            players.add(player);
        }

        /** A LuckPerms track, its groups lowest first. */
        public void track(String name, List<String> groups) {
            tracks.put(name, List.copyOf(groups));
        }

        public void expose(String command) {
            commands.add(command);
        }

        public void imported(boolean wasTranslated) {
            nodes++;
            if (wasTranslated) translated++;
        }

        /** A temporary entry that is imported with its expiry. */
        public void timed() {
            timed++;
        }

        /** An entry limited to a world that is imported with it. */
        public void world() {
            worlds++;
        }

        public void contextual() {
            contextual++;
        }

        public void foreign() {
            foreign++;
        }

        public void other() {
            other++;
        }

        /** A line for the report; the same one is not repeated, a migration being read, not scrolled. */
        public void note(String line) {
            if (!skipped.contains(line)) skipped.add(line);
        }

        public ImportPlan build() {
            return new ImportPlan(List.copyOf(grades), List.copyOf(players), Map.copyOf(tracks), Set.copyOf(commands),
                    List.copyOf(skipped),
                    new Counts(grades.size(), players.size(), nodes, translated, timed, worlds, commands.size(),
                            contextual, foreign, other));
        }
    }
}
