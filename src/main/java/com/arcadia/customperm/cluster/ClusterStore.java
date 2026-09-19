/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import java.util.List;
import java.util.Map;

/**
 * Where the servers of a cluster keep the shared rows. Every write takes the next number of one sequence shared
 * by all parts, and writers are serialised by it, so a server asking for the rows written after the last number it
 * saw never misses one.
 *
 * <p>A row is never deleted: removing a holder leaves it with a null body (a tombstone) and a new number, which is
 * how the removal reaches the other servers.</p>
 */
public interface ClusterStore {

    /** One holder as stored. {@code body} is null for a removed holder. */
    record Row(String holder, long version, String body, String updatedBy, long seq) {}

    /**
     * One holder to write. {@code expectedVersion} is the version this server last saw, 0 for a holder it has
     * never seen; {@code body} null removes the holder.
     */
    record Change(String holder, long expectedVersion, String body) {}

    /**
     * Either every change was written ({@code conflicts} empty, {@code written} the rows as stored now), or none
     * was ({@code conflicts} the rows as stored now for the holders someone else changed).
     */
    record WriteResult(List<Row> written, List<Row> conflicts) {
        public boolean ok() {
            return conflicts.isEmpty();
        }
    }

    /** Rows of {@code part} numbered after {@code afterSeq}, tombstones included, in number order. */
    List<Row> changesSince(String part, long afterSeq) throws StoreException;

    /** Writes all of {@code changes} or none of them. */
    WriteResult write(String part, List<Change> changes, String server) throws StoreException;

    /**
     * Records that {@code server} runs as {@code instance}, and answers the other instances seen running under the
     * same name within {@code liveSeconds}: a non-empty answer means two servers were given the same name.
     */
    List<String> heartbeat(String server, String instance, int liveSeconds) throws StoreException;

    /** Forgets this instance, at a clean stop. */
    void leave(String server, String instance) throws StoreException;

    /** For the dashboard: server name to seconds since its last heartbeat. */
    Map<String, Long> servers() throws StoreException;

    /** The store could not be reached or refused the operation. */
    class StoreException extends Exception {
        public StoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
