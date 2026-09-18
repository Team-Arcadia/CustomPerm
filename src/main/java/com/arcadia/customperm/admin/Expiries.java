/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.perm.Expiry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The expiry side of adding and removing an entry, shared by {@link GradeAdmin} and {@link UserAdmin}.
 * Adding an entry that is already there with a duration makes it temporary from now, and without one makes
 * it permanent: the last thing said is what holds, as with LuckPerms' {@code settemp} and {@code set}.
 */
final class Expiries {

    private Expiries() {
    }

    /**
     * Sets or clears the expiry of {@code key}; true when that changed anything.
     *
     * @param seconds how long from now, 0 for permanent
     */
    static boolean apply(Map<String, Long> expiries, String key, long seconds) {
        if (seconds > 0) {
            expiries.put(key, Expiry.now() + seconds);
            return true;
        }
        return expiries.remove(key) != null;
    }

    /** The expiries of one player in {@code byUser}, created when absent. */
    static Map<String, Long> of(Map<String, Map<String, Long>> byUser, UUID uuid) {
        return byUser.computeIfAbsent(uuid.toString(), k -> new HashMap<>());
    }

    /** Forgets the expiry of one player's entry, and the player's map once it is empty. */
    static void forget(Map<String, Map<String, Long>> byUser, UUID uuid, String key) {
        Map<String, Long> expiries = byUser.get(uuid.toString());
        if (expiries == null) return;
        expiries.remove(key);
        if (expiries.isEmpty()) byUser.remove(uuid.toString());
    }

    /** Forgets the expiry of one player's entry when the entry itself is not added: nothing to time. */
    static void tidy(Map<String, Map<String, Long>> byUser, UUID uuid) {
        Map<String, Long> expiries = byUser.get(uuid.toString());
        if (expiries != null && expiries.isEmpty()) byUser.remove(uuid.toString());
    }

    /** {@code " for 30d"}, or nothing for a permanent entry: the tail of a confirmation. */
    static String span(long seconds) {
        return seconds > 0 ? " for " + Expiry.describe(seconds) : "";
    }

    /** What an entry already there became: temporary from now, or permanent. */
    static String became(long seconds) {
        return seconds > 0 ? "now expires in " + Expiry.describe(seconds) : "is now permanent";
    }
}
