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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The store in memory, with the semantics the SQL one must have: serialised writes, one shared sequence,
 * versions checked, tombstones. Used by the tests to run two servers against one store in one process, and able
 * to play an outage.
 */
public final class MemoryStore implements ClusterStore {

    private final Map<String, Map<String, Row>> parts = new HashMap<>();
    private final Map<String, Map<String, Long>> heartbeats = new HashMap<>();
    private final LongSupplier clock;
    private final List<LogRow> log = new ArrayList<>();
    private final List<UseRow> uses = new ArrayList<>();
    private long seq;
    private long logId;
    private long useId;
    private boolean down;
    private int writeCalls;

    public MemoryStore() {
        this(System::currentTimeMillis);
    }

    public MemoryStore(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    /** While down, every call fails as an unreachable database would. */
    public synchronized void setDown(boolean down) {
        this.down = down;
    }

    @Override
    public synchronized List<Row> changesSince(String part, long afterSeq) throws StoreException {
        check();
        List<Row> rows = new ArrayList<>();
        for (Row row : parts.getOrDefault(part, Map.of()).values()) {
            if (row.seq() > afterSeq) rows.add(row);
        }
        rows.sort(Comparator.comparingLong(Row::seq));
        return rows;
    }

    @Override
    public synchronized Map<String, List<Row>> changesSince(java.util.Collection<String> wanted, long afterSeq)
            throws StoreException {
        check();
        Map<String, List<Row>> rows = new HashMap<>();
        for (String part : wanted) {
            List<Row> some = changesSince(part, afterSeq);
            if (!some.isEmpty()) rows.put(part, some);
        }
        return rows;
    }

    @Override
    public synchronized long currentSeq() throws StoreException {
        check();
        return seq;
    }

    @Override
    public synchronized WriteResult write(String part, List<Change> changes, String server) throws StoreException {
        writeCalls++;
        check();
        Map<String, Row> rows = parts.computeIfAbsent(part, p -> new HashMap<>());
        List<Row> conflicts = new ArrayList<>();
        for (Change change : changes) {
            Row current = rows.get(change.holder());
            long version = current == null ? 0 : current.version();
            if (version != change.expectedVersion()) conflicts.add(current);
        }
        if (!conflicts.isEmpty()) return new WriteResult(List.of(), conflicts);
        long number = ++seq;
        List<Row> written = new ArrayList<>();
        for (Change change : changes) {
            Row row = new Row(change.holder(), change.expectedVersion() + 1, change.body(), server, number);
            rows.put(change.holder(), row);
            written.add(row);
        }
        return new WriteResult(written, List.of());
    }

    @Override
    public synchronized Map<String, Long> heartbeat(String server, String instance, int liveSeconds) throws StoreException {
        check();
        long now = clock.getAsLong();
        Map<String, Long> instances = heartbeats.computeIfAbsent(server, s -> new HashMap<>());
        instances.put(instance, now);
        Map<String, Long> others = new HashMap<>();
        instances.forEach((other, seen) -> {
            if (!other.equals(instance) && now - seen <= liveSeconds * 1000L) others.put(other, seen);
        });
        return others;
    }

    @Override
    public synchronized void leave(String server, String instance) throws StoreException {
        check();
        Map<String, Long> instances = heartbeats.get(server);
        if (instances != null) instances.remove(instance);
    }

    @Override
    public synchronized Map<String, Long> servers() throws StoreException {
        check();
        long now = clock.getAsLong();
        Map<String, Long> ages = new HashMap<>();
        heartbeats.forEach((server, instances) -> instances.values().stream().max(Long::compare)
                .ifPresent(seen -> ages.put(server, (now - seen) / 1000)));
        return ages;
    }

    @Override
    public synchronized void appendLog(String server, List<LogLine> lines) throws StoreException {
        check();
        for (LogLine line : lines) log.add(new LogRow(++logId, server, line.kind(), line.entry()));
    }

    @Override
    public synchronized List<LogRow> logAfter(long afterId, java.util.Collection<Long> alsoIds, int limit)
            throws StoreException {
        check();
        List<LogRow> rows = new ArrayList<>();
        for (LogRow row : log) {
            if (row.id() > afterId || alsoIds.contains(row.id())) rows.add(row);
            if (rows.size() >= limit) break;
        }
        return rows;
    }

    @Override
    public synchronized List<LogRow> recentLog(String kind, int limit) throws StoreException {
        check();
        List<LogRow> rows = new ArrayList<>();
        for (int i = log.size() - 1; i >= 0 && rows.size() < limit; i--) {
            if (log.get(i).kind().equals(kind)) rows.add(log.get(i));
        }
        return rows;
    }

    @Override
    public synchronized int purgeLog(long beforeTime) throws StoreException {
        check();
        int before = log.size();
        log.removeIf(row -> row.entry().time() < beforeTime);
        return before - log.size();
    }

    @Override
    public synchronized void appendUses(String server, List<Use> added) throws StoreException {
        check();
        for (Use use : added) uses.add(new UseRow(++useId, server, use));
    }

    @Override
    public synchronized List<UseRow> usesAfter(long afterId, java.util.Collection<Long> alsoIds, int limit)
            throws StoreException {
        check();
        List<UseRow> rows = new ArrayList<>();
        for (UseRow row : uses) {
            if (row.id() > afterId || alsoIds.contains(row.id())) rows.add(row);
            if (rows.size() >= limit) break;
        }
        return rows;
    }

    @Override
    public synchronized int purgeUses(long beforeTime) throws StoreException {
        check();
        int before = uses.size();
        uses.removeIf(row -> row.use().time() < beforeTime);
        return before - uses.size();
    }

    /** How many writes were asked, failed ones included, for tests. */
    public synchronized int writeCalls() {
        return writeCalls;
    }

    /** Entries as stored, for tests. */
    public synchronized List<LogEntry> logEntries() {
        return log.stream().map(LogRow::entry).toList();
    }

    private void check() throws StoreException {
        if (down) throw new StoreException("store is down", null);
    }
}
