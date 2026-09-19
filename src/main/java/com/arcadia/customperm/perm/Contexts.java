/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Where a player is, as the contexts an entry can be limited to. Pure Java, like the resolver that reads it.
 *
 * <p>A context is written as {@code key=value} pairs joined by {@code ,}, sorted by key then value:
 * {@code gamemode=creative,world=minecraft:the_nether}. That string is the key an entry is stored under, so
 * the file and the resolver take a new key without changing shape. An entry applies when each key it names
 * holds for the player with one of the values it gives: two values of one key are either one, like a
 * LuckPerms context set, so {@code world=minecraft:the_end,world=minecraft:the_nether} applies in both.
 *
 * <p>Keys read:
 * <ul>
 *   <li>{@code world}, a dimension id; a value without a namespace means {@code minecraft:}. LuckPerms on
 *       NeoForge calls it {@code dimension-type}, which is read as {@code world} here.</li>
 *   <li>{@code gamemode}: {@code survival}, {@code creative}, {@code adventure} or {@code spectator}.</li>
 *   <li>Any other key, its value set for the whole server in the settings (static contexts), like
 *       LuckPerms' {@code static-contexts}.</li>
 * </ul>
 * {@code server} is reserved for cluster mode and not read yet.
 */
public final class Contexts {

    public static final String WORLD = "world";
    public static final String GAMEMODE = "gamemode";
    /** LuckPerms' name for the dimension on NeoForge, read as {@link #WORLD}. */
    public static final String DIMENSION_TYPE = "dimension-type";
    /** Reserved for cluster mode, which knows the server's name. */
    public static final String SERVER = "server";
    public static final List<String> GAMEMODES = List.of("survival", "creative", "adventure", "spectator");

    /** Where nothing is known: only entries without a context apply. */
    public static final Contexts NONE = new Contexts(new String[0]);

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]{1,32}");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9_.:/+-]{1,64}");

    /** The pairs that hold, each written {@code key=value}. */
    private final String[] pairs;

    private Contexts(String[] pairs) {
        this.pairs = pairs;
    }

    /** A player in the dimension {@code dimensionId}, such as {@code minecraft:the_nether}. */
    public static Contexts world(String dimensionId) {
        return of(dimensionId, null, Map.of());
    }

    /**
     * A player in {@code dimensionId}, in {@code gamemode} (null when unknown), on a server whose static
     * contexts are {@code statics}. Built once per combination by the caller, never per check.
     */
    public static Contexts of(String dimensionId, String gamemode, Map<String, String> statics) {
        TreeSet<String> sorted = new TreeSet<>();
        if (dimensionId != null) sorted.add(WORLD + "=" + dimensionId);
        if (gamemode != null) sorted.add(GAMEMODE + "=" + gamemode);
        if (statics != null) statics.forEach((key, value) -> sorted.add(key + "=" + value));
        return new Contexts(sorted.toArray(new String[0]));
    }

    public boolean isEmpty() {
        return pairs.length == 0;
    }

    /** The pairs that hold here, as {@code key=value}: what a listing shows. */
    public List<String> pairs() {
        return List.of(pairs);
    }

    /**
     * Whether every key of {@code context} holds here with one of its values. Compared in place, without
     * splitting: this runs on the permission path for each contextual entry a holder has. The stored form
     * keeps the values of one key next to each other, so a key is decided once its run of pairs ends.
     */
    public boolean satisfies(String context) {
        if (context == null || context.isEmpty()) return true;
        int length = context.length();
        int keyStart = -1;
        int keyLength = 0;
        boolean keyHolds = false;
        int start = 0;
        while (start <= length) {
            int end = context.indexOf(',', start);
            if (end < 0) end = length;
            int eq = context.indexOf('=', start);
            int partKey = (eq < 0 || eq > end ? end : eq) - start;
            if (keyStart < 0 || partKey != keyLength || !context.regionMatches(start, context, keyStart, partKey)) {
                if (keyStart >= 0 && !keyHolds) return false;
                keyStart = start;
                keyLength = partKey;
                keyHolds = false;
            }
            if (!keyHolds) keyHolds = holds(context, start, end - start);
            start = end + 1;
        }
        return keyHolds;
    }

    private boolean holds(String context, int offset, int length) {
        for (String pair : pairs) {
            if (pair.length() == length && context.regionMatches(offset, pair, 0, length)) return true;
        }
        return false;
    }

    /** How many keys {@code context} names: an entry naming more is the more precise one. */
    public static int size(String context) {
        if (context == null || context.isEmpty()) return 0;
        int count = 0;
        int keyStart = -1;
        int keyLength = 0;
        int start = 0;
        int length = context.length();
        while (start <= length) {
            int end = context.indexOf(',', start);
            if (end < 0) end = length;
            int eq = context.indexOf('=', start);
            int partKey = (eq < 0 || eq > end ? end : eq) - start;
            if (keyStart < 0 || partKey != keyLength || !context.regionMatches(start, context, keyStart, partKey)) {
                count++;
                keyStart = start;
                keyLength = partKey;
            }
            start = end + 1;
        }
        return count;
    }

    /**
     * The stored form of a context typed by an admin or read from a file, or {@code null} when it is not
     * one: {@code world=the_nether} becomes {@code world=minecraft:the_nether}. Keys and values are
     * lowercased, sorted, and a pair named twice counts once; {@code dimension-type} is read as {@code world}.
     * A malformed pair or a game mode that does not exist is refused. A key this method accepts but nothing
     * sets here matches nothing: see {@link #undeclared}; {@code server} is set only while a cluster runs.
     */
    public static String parse(String text) {
        if (text == null) return null;
        String clean = text.trim();
        if (clean.isEmpty()) return null;
        TreeMap<String, TreeSet<String>> sorted = new TreeMap<>();
        for (String part : clean.split(",", -1)) {
            int eq = part.indexOf('=');
            if (eq <= 0) return null;
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (key.equals(DIMENSION_TYPE)) key = WORLD;
            String value = value(key, part.substring(eq + 1).trim());
            if (value == null) return null;
            sorted.computeIfAbsent(key, k -> new TreeSet<>()).add(value);
        }
        StringBuilder out = new StringBuilder();
        sorted.forEach((key, values) -> values.forEach(value -> {
            if (!out.isEmpty()) out.append(',');
            out.append(key).append('=').append(value);
        }));
        return out.toString();
    }

    private static String value(String key, String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        return switch (key) {
            case WORLD -> dimension(value);
            case GAMEMODE -> GAMEMODES.contains(value) ? value : null;
            default -> KEY.matcher(key).matches() && VALUE.matcher(value).matches() ? value : null;
        };
    }

    private static String dimension(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) return null;
        return namespace + ":" + path;
    }

    /** Whether {@code key} is one a static context may set: not one the game decides, nor {@code server}. */
    public static boolean staticKey(String key) {
        return key != null && KEY.matcher(key).matches() && !key.equals(WORLD) && !key.equals(GAMEMODE)
                && !key.equals(DIMENSION_TYPE) && !key.equals(SERVER);
    }

    /** Whether {@code value} can be a static context's value. */
    public static boolean staticValue(String value) {
        return value != null && VALUE.matcher(value).matches();
    }

    /**
     * The first key of a stored context that neither the game nor {@code statics} sets, or {@code null}:
     * an entry limited to it would apply nowhere, most likely a typo.
     */
    public static String undeclared(String context, Map<String, String> statics) {
        for (String[] pair : split(context)) {
            if (!pair[0].equals(WORLD) && !pair[0].equals(GAMEMODE) && (statics == null || !statics.containsKey(pair[0]))) {
                return pair[0];
            }
        }
        return null;
    }

    /** The pairs of a stored context as {@code {key, value}}, in order; off the permission path. */
    public static List<String[]> split(String context) {
        List<String[]> out = new ArrayList<>();
        if (context == null || context.isEmpty()) return out;
        for (String part : context.split(",")) {
            int eq = part.indexOf('=');
            out.add(eq < 0 ? new String[]{part, ""} : new String[]{part.substring(0, eq), part.substring(eq + 1)});
        }
        return out;
    }

    /** The first dimension a context names, or {@code null} when it names none. */
    public static String worldOf(String context) {
        for (String[] pair : split(context)) {
            if (pair[0].equals(WORLD)) return pair[1];
        }
        return null;
    }

    /**
     * How LuckPerms writes a dimension in its {@code dimension-type} context: the path alone for a vanilla
     * one, the full id otherwise.
     */
    public static String luckPermsWorld(String dimensionId) {
        return dimensionId.startsWith("minecraft:") ? dimensionId.substring("minecraft:".length()) : dimensionId;
    }

    /**
     * A stored context as a world box takes it back: {@code the_nether,gamemode=creative}, a world by its
     * short name and every other pair as it is.
     */
    public static String typed(String context) {
        StringBuilder out = new StringBuilder();
        for (String[] pair : split(context)) {
            if (!out.isEmpty()) out.append(',');
            out.append(pair[0].equals(WORLD) ? luckPermsWorld(pair[1]) : pair[0] + "=" + pair[1]);
        }
        return out.toString();
    }

    /**
     * {@code the_nether} for {@code world=minecraft:the_nether}, {@code the_nether or the_end, creative} for two
     * worlds and a game mode: what the lists show beside an entry.
     */
    public static String describe(String context) {
        if (context == null || context.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        String previous = null;
        for (String[] pair : split(context)) {
            String shown = pair[0].equals(WORLD) ? luckPermsWorld(pair[1])
                    : pair[0].equals(GAMEMODE) ? pair[1] : pair[0] + "=" + pair[1];
            if (!out.isEmpty()) out.append(pair[0].equals(previous) ? " or " : ", ");
            out.append(shown);
            previous = pair[0];
        }
        return out.toString();
    }
}
