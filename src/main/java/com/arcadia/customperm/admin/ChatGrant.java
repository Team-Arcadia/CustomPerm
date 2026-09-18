/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.config.GradesConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * One prefix or suffix carried by an import or an export plan: plain Java, so a plan can be built and read
 * without LuckPerms, the same way {@link ScopedGrant} carries entries limited to a world.
 *
 * @param expires epoch seconds, 0 for good
 * @param context the stored form of the world it is limited to, empty for everywhere
 */
public record ChatGrant(boolean suffix, int priority, String text, long expires, String context) {

    /** A global one. */
    public ChatGrant(boolean suffix, int priority, String text, long expires) {
        this(suffix, priority, text, expires, "");
    }

    /**
     * A holder's prefixes then suffixes still alive at {@code now}, highest priority first, then those limited
     * to a context, by context.
     */
    public static List<ChatGrant> of(List<GradesConfig.ChatEntry> prefixes, List<GradesConfig.ChatEntry> suffixes,
                                     java.util.Map<String, ? extends GradesConfig.Scoped> scopes, long now) {
        List<ChatGrant> grants = new ArrayList<>();
        add(grants, prefixes, false, now, "");
        add(grants, suffixes, true, now, "");
        if (scopes != null) new java.util.TreeMap<>(scopes).forEach((context, scope) -> {
            add(grants, scope.prefixes, false, now, context);
            add(grants, scope.suffixes, true, now, context);
        });
        return List.copyOf(grants);
    }

    private static void add(List<ChatGrant> grants, List<GradesConfig.ChatEntry> entries, boolean suffix, long now,
                            String context) {
        if (entries == null) return;
        entries.stream().filter(entry -> entry.alive(now))
                .sorted(java.util.Comparator.comparingInt((GradesConfig.ChatEntry e) -> e.priority).reversed())
                .forEach(entry -> grants.add(new ChatGrant(suffix, entry.priority, entry.text, entry.expires, context)));
    }

    /** Adds this to a grade, in its context; true when it was added. */
    public boolean addTo(GradesConfig.Grade grade) {
        if (context.isEmpty()) return addTo(grade.prefixes, grade.suffixes);
        GradesConfig.Scoped scope = Scopes.of(grade, context);
        return addTo(scope.prefixes, scope.suffixes);
    }

    /** Adds this to one player, in its context; true when it was added. */
    public boolean addTo(GradesConfig config, java.util.UUID uuid) {
        if (!context.isEmpty()) {
            GradesConfig.Scoped scope = Scopes.of(config, uuid, context);
            return addTo(scope.prefixes, scope.suffixes);
        }
        List<GradesConfig.ChatEntry> prefixes = config.userPrefixEntries.computeIfAbsent(uuid.toString(), k -> new ArrayList<>());
        List<GradesConfig.ChatEntry> suffixes = config.userSuffixEntries.computeIfAbsent(uuid.toString(), k -> new ArrayList<>());
        boolean added = addTo(prefixes, suffixes);
        if (prefixes.isEmpty()) config.userPrefixEntries.remove(uuid.toString());
        if (suffixes.isEmpty()) config.userSuffixEntries.remove(uuid.toString());
        return added;
    }

    /**
     * Adds this to a holder's entries unless one is already at its priority: what is there was chosen here,
     * as a weight or a node is kept when an import adds. True when it was added.
     */
    public boolean addTo(List<GradesConfig.ChatEntry> prefixes, List<GradesConfig.ChatEntry> suffixes) {
        List<GradesConfig.ChatEntry> target = suffix ? suffixes : prefixes;
        if (ChatEntries.find(target, priority) != null) return false;
        target.add(new GradesConfig.ChatEntry(priority, text, expires));
        target.sort(java.util.Comparator.comparingInt((GradesConfig.ChatEntry e) -> e.priority).reversed());
        return true;
    }
}
