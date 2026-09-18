/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.chat.LegacyText;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.Expiry;

import java.util.Comparator;
import java.util.List;

/**
 * Adding and removing one holder's prefixes or suffixes, shared by {@link GradeAdmin} and {@link UserAdmin}.
 * A holder carries one entry per priority, so adding at a priority already used replaces that entry, and a
 * priority is enough to name the one to remove.
 */
public final class ChatEntries {

    /** Widest priority accepted, either way: LuckPerms takes any int, and nothing sensible needs more. */
    public static final int PRIORITY_MAX = 1_000_000_000;

    /** What adding did. */
    enum Change { ADDED, REPLACED, UNCHANGED }

    private ChatEntries() {
    }

    /** Why {@code text} at {@code priority} cannot be stored, or {@code null} when it can. */
    static String problem(boolean suffix, int priority, String text) {
        String what = suffix ? "suffix" : "prefix";
        if (priority < -PRIORITY_MAX || priority > PRIORITY_MAX) {
            return "A priority is between " + -PRIORITY_MAX + " and " + PRIORITY_MAX + ".";
        }
        if (text == null || text.isEmpty()) return "Expected a " + what + " text.";
        String problem = LegacyText.problem(text);
        return problem == null ? null : "Invalid " + what + ": " + problem;
    }

    /** Puts {@code text} at {@code priority}, for {@code seconds} or for good (0). */
    static Change put(List<GradesConfig.ChatEntry> entries, int priority, String text, long seconds) {
        long expires = seconds > 0 ? Expiry.now() + seconds : 0;
        GradesConfig.ChatEntry existing = find(entries, priority);
        if (existing != null) {
            if (existing.text.equals(text) && existing.expires == expires) return Change.UNCHANGED;
            existing.text = text;
            existing.expires = expires;
            return Change.REPLACED;
        }
        entries.add(new GradesConfig.ChatEntry(priority, text, expires));
        entries.sort(Comparator.comparingInt((GradesConfig.ChatEntry e) -> e.priority).reversed());
        return Change.ADDED;
    }

    /** Removes the entry at {@code priority}; the one removed, or {@code null} when there was none. */
    static GradesConfig.ChatEntry remove(List<GradesConfig.ChatEntry> entries, int priority) {
        GradesConfig.ChatEntry existing = find(entries, priority);
        if (existing != null) entries.remove(existing);
        return existing;
    }

    static GradesConfig.ChatEntry find(List<GradesConfig.ChatEntry> entries, int priority) {
        if (entries == null) return null;
        for (GradesConfig.ChatEntry entry : entries) {
            if (entry.priority == priority) return entry;
        }
        return null;
    }

    /** {@code "[VIP] " at 10}, codes included, as the admin would type it back. */
    static String describe(GradesConfig.ChatEntry entry) {
        return "\"" + entry.text + "\" at " + entry.priority;
    }

    /**
     * One holder's entries for a listing: {@code "[VIP] " at 10 (29d 23h left)}, highest priority first; an
     * entry that has run out but was not swept yet is left out, the resolver already ignoring it.
     */
    public static List<String> listing(List<GradesConfig.ChatEntry> entries) {
        if (entries == null) return List.of();
        long now = Expiry.now();
        return entries.stream().filter(entry -> entry.alive(now))
                .map(entry -> describe(entry) + (entry.expires > 0
                        ? " (" + Expiry.describe(entry.expires - now) + " left)" : ""))
                .toList();
    }
}
