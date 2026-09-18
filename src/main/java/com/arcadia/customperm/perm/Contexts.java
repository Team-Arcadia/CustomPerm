/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Where a player is, as the contexts an entry can be limited to. Pure Java, like the resolver that reads it.
 *
 * <p>A context is written as {@code key=value} pairs joined by {@code ,}, sorted by key:
 * {@code world=minecraft:the_nether}. That string is the key an entry is stored under, so the file and the
 * resolver take a second key ({@code server=<name>}, for cluster mode) without changing shape. An entry
 * applies when every pair it names holds for the player; an entry with no context applies everywhere.
 *
 * <p>{@code world} is the only key read today, its value a dimension id. A value without a namespace means
 * {@code minecraft:}, which is also how LuckPerms names the vanilla worlds ({@code the_nether}).
 */
public final class Contexts {

    public static final String WORLD = "world";

    /** Where nothing is known: only entries without a context apply. */
    public static final Contexts NONE = new Contexts(new String[0]);

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    /** The pairs that hold, each written {@code key=value}. */
    private final String[] pairs;

    private Contexts(String[] pairs) {
        this.pairs = pairs;
    }

    /** A player in the dimension {@code dimensionId}, such as {@code minecraft:the_nether}. */
    public static Contexts world(String dimensionId) {
        return new Contexts(new String[]{WORLD + "=" + dimensionId});
    }

    public boolean isEmpty() {
        return pairs.length == 0;
    }

    /**
     * Whether every pair of {@code context} holds here. Compared in place, without splitting: this runs on
     * the permission path for each contextual entry a holder has.
     */
    public boolean satisfies(String context) {
        if (context == null || context.isEmpty()) return true;
        int start = 0;
        while (start <= context.length()) {
            int end = context.indexOf(',', start);
            if (end < 0) end = context.length();
            if (!holds(context, start, end - start)) return false;
            start = end + 1;
        }
        return true;
    }

    private boolean holds(String context, int offset, int length) {
        for (String pair : pairs) {
            if (pair.length() == length && context.regionMatches(offset, pair, 0, length)) return true;
        }
        return false;
    }

    /** How many pairs {@code context} names: an entry naming more is the more precise one. */
    public static int size(String context) {
        if (context == null || context.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < context.length(); i++) {
            if (context.charAt(i) == ',') count++;
        }
        return count;
    }

    /**
     * The stored form of a context typed by an admin or read from a file, or {@code null} when it is not
     * one: {@code world=the_nether} becomes {@code world=minecraft:the_nether}. Keys are lowercased and
     * sorted; a key named twice, an unknown key or a value that is not a dimension id is refused.
     */
    public static String parse(String text) {
        if (text == null) return null;
        String clean = text.trim();
        if (clean.isEmpty()) return null;
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String part : clean.split(",", -1)) {
            int eq = part.indexOf('=');
            if (eq <= 0) return null;
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = value(key, part.substring(eq + 1).trim());
            if (value == null || sorted.put(key, value) != null) return null;
        }
        StringBuilder out = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (!out.isEmpty()) out.append(',');
            out.append(key).append('=').append(value);
        });
        return out.toString();
    }

    private static String value(String key, String raw) {
        if (!WORLD.equals(key)) return null;
        String id = raw.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) return null;
        return namespace + ":" + path;
    }

    /** The dimension a context names, or {@code null} when it names none. */
    public static String worldOf(String context) {
        if (context == null) return null;
        for (String part : context.split(",")) {
            if (part.startsWith(WORLD + "=")) return part.substring(WORLD.length() + 1);
        }
        return null;
    }

    /**
     * How LuckPerms writes a dimension in its {@code world} context: the path alone for a vanilla one, the
     * full id otherwise.
     */
    public static String luckPermsWorld(String dimensionId) {
        return dimensionId.startsWith("minecraft:") ? dimensionId.substring("minecraft:".length()) : dimensionId;
    }

    /** {@code the_nether} for {@code world=minecraft:the_nether}: what the lists show beside an entry. */
    public static String describe(String context) {
        if (context == null || context.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : context.split(",")) {
            if (!out.isEmpty()) out.append(", ");
            out.append(part.startsWith(WORLD + "=") ? luckPermsWorld(part.substring(WORLD.length() + 1)) : part);
        }
        return out.toString();
    }
}
