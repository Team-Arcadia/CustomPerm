/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The cluster members an exposed command, an alias or a rate limit is active on. The list travels with the
 * element through the cluster store; each member reads it and keeps the element only when its own name is on it.
 *
 * <p>An empty list means every member, so a configuration written before the list existed keeps working as it
 * did. Outside a cluster the list is not read at all: a server that has no cluster name keeps every element. A
 * name that is not a member today is kept, since a member stopped for maintenance must not lose its setup.
 *
 * <p>Pure Java, no Minecraft class, so it is unit tested directly.
 */
public final class ServerScope {

    /** A server name as the cluster and the rate-limit scopes write it. */
    public static final Pattern NAME = Pattern.compile("[0-9a-z_.\\-]{1,64}");

    private ServerScope() {
    }

    /** Why {@code name} cannot be a server name, or null when it can; compare it lowercased. */
    public static String problem(String name) {
        if (name == null || !NAME.matcher(name.trim().toLowerCase(Locale.ROOT)).matches()) {
            return "A server name is 1 to 64 of a-z, 0-9, dot, dash or underscore.";
        }
        return null;
    }

    /**
     * The list as it is stored: lowercased, sorted, each name once, invalid names dropped; null when nothing is
     * left, so an element without a list keeps the file and the cluster row it had before lists existed.
     */
    public static List<String> normalize(Collection<String> names) {
        if (names == null) return null;
        TreeSet<String> clean = new TreeSet<>();
        for (String name : names) {
            if (name == null) continue;
            String lower = name.trim().toLowerCase(Locale.ROOT);
            if (NAME.matcher(lower).matches()) clean.add(lower);
        }
        return clean.isEmpty() ? null : new ArrayList<>(clean);
    }

    /** Whether an element with this list is active on the server named {@code here}; null {@code here}: no cluster. */
    public static boolean appliesHere(List<String> servers, String here) {
        if (servers == null || servers.isEmpty() || here == null) return true;
        return servers.contains(here.toLowerCase(Locale.ROOT));
    }
}
