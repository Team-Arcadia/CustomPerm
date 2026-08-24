/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.lp;

import com.arcadia.customperm.network.lp.LpDto;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Display helpers for the LuckPerms editor screens. Minecraft GUI rows are narrow, so these
 * favour short forms ({@code 3d}, {@code global}) over exact ones — the editor is for deciding
 * what to change, not for auditing timestamps to the second.
 */
final class LpFormat {

    private LpFormat() {
    }

    /** Human label for a node's context set. */
    static String contexts(String flattened) {
        return flattened.isEmpty() ? "global" : flattened;
    }

    /** Human label for a node expiry: {@code permanent}, {@code expired}, or a rough countdown. */
    static String expiry(long epochSeconds) {
        if (epochSeconds == LpDto.NO_EXPIRY) return "permanent";
        long remaining = epochSeconds - Instant.now().getEpochSecond();
        if (remaining <= 0) return "expired";
        if (remaining < 60) return remaining + "s";
        if (remaining < 3600) return (remaining / 60) + "m";
        if (remaining < 86_400) return (remaining / 3600) + "h";
        return (remaining / 86_400) + "d";
    }

    /** Allow/deny label for a node value. */
    static String value(boolean allow) {
        return allow ? "allow" : "deny";
    }

    /** Comma-joined list, or a dash when empty, so a row never collapses to nothing. */
    static String join(List<String> values) {
        return values.isEmpty() ? "-" : String.join(", ", values);
    }

    /** Weight label, hiding the {@link LpDto#NO_WEIGHT} sentinel behind a dash. */
    static String weight(int weight) {
        return weight == LpDto.NO_WEIGHT ? "-" : String.valueOf(weight);
    }

    /** Falls back to a placeholder when a group declares no display name, prefix or suffix. */
    static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    /**
     * Parses a duration typed by the admin: a bare number of seconds, or a number with a
     * {@code s}/{@code m}/{@code h}/{@code d} suffix. Returns {@code "0"} (permanent) for
     * anything it cannot read, which is the safe reading of an empty or malformed field —
     * a temporary grant misread as a shorter one would expire silently.
     */
    static String durationSeconds(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return "0";
        char unit = text.charAt(text.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> 1L;
            case 'm' -> 60L;
            case 'h' -> 3600L;
            case 'd' -> 86_400L;
            default -> 0L;
        };
        try {
            if (multiplier == 0L) return String.valueOf(Math.max(0L, Long.parseLong(text)));
            long amount = Long.parseLong(text.substring(0, text.length() - 1));
            return String.valueOf(Math.max(0L, amount * multiplier));
        } catch (NumberFormatException e) {
            return "0";
        }
    }
}
