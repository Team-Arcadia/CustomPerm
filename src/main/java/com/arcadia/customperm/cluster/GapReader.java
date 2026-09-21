/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads an append-only table numbered by the database (the shared log, the shared uses) without reading a row twice
 * and without missing one.
 *
 * <p>Numbers are handed out when a row is inserted, not when it commits: another server's row numbered 41 can commit
 * after this server has read 42. Reading "after the last number" alone would skip 41 for good; reading the last
 * minute again on every poll would send the same rows thirty times over. Here, a number skipped between two rows
 * read is remembered and asked for by number on the next reads, for a limited time: long enough for a late commit,
 * after which the number is taken to belong to a rolled back insert, which leaves a hole for ever.</p>
 */
final class GapReader {

    /** How long a skipped number is waited for. */
    static final long WAIT_MILLIS = 60_000;
    /** Skipped numbers remembered at most; a larger jump is taken as rows another way (a purge, a new table). */
    static final int MISSING_MAX = 500;

    private long last;
    /** Number skipped to when it was first seen missing. */
    private final Map<Long, Long> missing = new HashMap<>();

    /** Highest number read. */
    long last() {
        return last;
    }

    /** Numbers still waited for, to ask again by number. */
    Set<Long> missing() {
        return missing.keySet();
    }

    /** Starts after {@code number}, with nothing waited for: rows already there are not read again. */
    void startAfter(long number) {
        last = Math.max(last, number);
        missing.clear();
    }

    /**
     * Takes the numbers of the rows just read, in increasing order of number among those above {@link #last()}.
     * True when the row is new here.
     */
    boolean accept(long number, long now) {
        if (number <= last) return missing.remove(number) != null;
        long gap = number - last - 1;
        // No floor on last: a cluster joining an empty table starts at 0, and a number skipped on the very first
        // row read is exactly the one a concurrent insert commits late. The MISSING_MAX bound below is what keeps
        // a first read of an already long table from turning into thousands of numbers to wait for.
        if (gap > 0 && gap <= MISSING_MAX) {
            for (long skipped = last + 1; skipped < number && missing.size() < MISSING_MAX; skipped++) {
                missing.put(skipped, now);
            }
        }
        last = number;
        return true;
    }

    /** Stops waiting for numbers skipped longer than {@link #WAIT_MILLIS} ago. */
    void prune(long now) {
        missing.values().removeIf(since -> now - since > WAIT_MILLIS);
    }
}
