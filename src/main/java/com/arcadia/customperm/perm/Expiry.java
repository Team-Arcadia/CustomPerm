/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Temporary entries: when they end, how a duration is written, and whether one is still alive. Pure Java,
 * like the resolver that reads it.
 *
 * <p>An expiry is an epoch second stored beside the entry, in a map keyed like the collection it belongs
 * to. An entry with no expiry is permanent, which is what every entry written before the field reads as.
 */
public final class Expiry {

    /** Longest duration accepted: ten years, past which "temporary" means a typo. */
    public static final long MAX_SECONDS = 10L * 365 * 24 * 3600;

    private static final Pattern PART = Pattern.compile("(\\d+)([wdhms])");
    private static final Pattern WHOLE = Pattern.compile("(\\d+[wdhms])+");

    private Expiry() {
    }

    public static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Whether {@code key} is alive in a holder whose expiries are {@code expiries}. The map is checked only
     * for emptiness in the usual case, where nothing on that holder is temporary, and the clock is read only
     * for an entry that has an expiry.
     */
    public static boolean alive(Map<String, Long> expiries, String key) {
        if (expiries == null || expiries.isEmpty()) return true;
        Long at = expiries.get(key);
        return at == null || at > now();
    }

    /**
     * {@code names} without the ones whose expiry has passed. The same list when nothing in it is temporary,
     * so the usual case allocates nothing.
     */
    public static List<String> alive(List<String> names, Map<String, Long> expiries) {
        if (names == null || expiries == null || expiries.isEmpty()) return names;
        long now = now();
        boolean anyDead = false;
        for (String name : names) {
            Long at = expiries.get(name);
            if (at != null && at <= now) {
                anyDead = true;
                break;
            }
        }
        if (!anyDead) return names;
        return names.stream().filter(name -> {
            Long at = expiries.get(name);
            return at == null || at > now;
        }).toList();
    }

    /**
     * Seconds in a duration written as {@code 30d}, {@code 2h}, {@code 1d12h}, {@code 90m}: weeks, days,
     * hours, minutes and seconds, in any combination. {@code -1} for anything else, zero or over ten years.
     */
    public static long parse(String text) {
        if (text == null) return -1;
        String clean = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (!WHOLE.matcher(clean).matches()) return -1;
        long total = 0;
        Matcher part = PART.matcher(clean);
        while (part.find()) {
            long amount;
            try {
                amount = Long.parseLong(part.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
            long unit = switch (part.group(2)) {
                case "w" -> 7L * 24 * 3600;
                case "d" -> 24L * 3600;
                case "h" -> 3600L;
                case "m" -> 60L;
                default -> 1L;
            };
            if (amount > MAX_SECONDS / unit) return -1;
            total += amount * unit;
            if (total > MAX_SECONDS) return -1;
        }
        return total <= 0 ? -1 : total;
    }

    /** A remaining time as the two largest units that matter: {@code 29d 23h}, {@code 2h 5m}, {@code 40s}. */
    public static String describe(long seconds) {
        if (seconds <= 0) return "expired";
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        if (days > 0) return hours > 0 ? days + "d " + hours + "h" : days + "d";
        if (hours > 0) return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
