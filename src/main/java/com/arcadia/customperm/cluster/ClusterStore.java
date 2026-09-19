/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.log.LogEntry;

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

    /**
     * The same for several parts in one read, the rows grouped by part, each group in number order. The number is
     * shared by every part, so one cursor serves them all: what a poll asks the database when nothing changed is one
     * query, not one per part.
     */
    Map<String, List<Row>> changesSince(java.util.Collection<String> parts, long afterSeq) throws StoreException;

    /**
     * The last number handed out and committed. Every row numbered up to it is readable; a write still in progress
     * carries a higher one.
     */
    long currentSeq() throws StoreException;

    /** Writes all of {@code changes} or none of them. */
    WriteResult write(String part, List<Change> changes, String server) throws StoreException;

    /**
     * Records that {@code server} runs as {@code instance}, and answers the other instances seen under the same name
     * within {@code liveSeconds}, each with when it was last seen (epoch milliseconds). One still beating means two
     * servers were given the same name; one that stopped beating is an instance that ended without leaving.
     */
    Map<String, Long> heartbeat(String server, String instance, int liveSeconds) throws StoreException;

    /** Forgets this instance, at a clean stop. */
    void leave(String server, String instance) throws StoreException;

    /** For the dashboard: server name to seconds since its last heartbeat. */
    Map<String, Long> servers() throws StoreException;

    /** An activity log entry to share, and which tab it belongs to. */
    record LogLine(String kind, LogEntry entry) {}

    /** A shared activity log entry, numbered by the store, with the server that recorded it. */
    record LogRow(long id, String server, String kind, LogEntry entry) {}

    /** Adds entries this server recorded. */
    void appendLog(String server, List<LogLine> lines) throws StoreException;

    /**
     * Entries numbered after {@code afterId}, and those numbered {@code alsoIds}: numbers skipped earlier that a late
     * commit may still fill (see {@link GapReader}). In number order.
     */
    List<LogRow> logAfter(long afterId, java.util.Collection<Long> alsoIds, int limit) throws StoreException;

    /** The latest entries of one tab, newest first. */
    List<LogRow> recentLog(String kind, int limit) throws StoreException;

    /** Removes entries recorded before {@code beforeTime}; answers how many. */
    int purgeLog(long beforeTime) throws StoreException;

    /** A counted use of a rate-limited command. */
    record Use(String command, String player, long time) {}

    /** A counted use as stored, numbered, with the server that counted it. */
    record UseRow(long id, String server, Use use) {}

    /** Adds uses this server counted. */
    void appendUses(String server, List<Use> uses) throws StoreException;

    /** Uses numbered after {@code afterId}, and those numbered {@code alsoIds}; see {@link #logAfter}. */
    List<UseRow> usesAfter(long afterId, java.util.Collection<Long> alsoIds, int limit) throws StoreException;

    /** Removes uses counted before {@code beforeTime}. */
    int purgeUses(long beforeTime) throws StoreException;

    /** The store could not be reached or refused the operation. */
    class StoreException extends Exception {
        public StoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
