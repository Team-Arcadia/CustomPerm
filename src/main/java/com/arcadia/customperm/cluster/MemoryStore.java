/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

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
    private long seq;
    private boolean down;

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
    public synchronized WriteResult write(String part, List<Change> changes, String server) throws StoreException {
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
    public synchronized List<String> heartbeat(String server, String instance, int liveSeconds) throws StoreException {
        check();
        long now = clock.getAsLong();
        Map<String, Long> instances = heartbeats.computeIfAbsent(server, s -> new HashMap<>());
        instances.put(instance, now);
        List<String> others = new ArrayList<>();
        instances.forEach((other, seen) -> {
            if (!other.equals(instance) && now - seen <= liveSeconds * 1000L) others.add(other);
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

    private void check() throws StoreException {
        if (down) throw new StoreException("store is down", null);
    }
}
