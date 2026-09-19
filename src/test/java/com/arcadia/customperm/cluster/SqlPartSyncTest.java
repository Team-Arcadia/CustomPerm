/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.cluster.ClusterStore.Change;
import com.arcadia.customperm.cluster.ClusterStore.Row;
import com.arcadia.customperm.log.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The contract on SQL, H2 in MySQL mode, each server with its own connections; then what only SQL can get wrong. */
class SqlPartSyncTest extends PartSyncContract {

    private String url;
    private final AtomicBoolean down = new AtomicBoolean();

    @BeforeEach
    void database() throws Exception {
        url = "jdbc:h2:mem:cp" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        sql().createTables();
    }

    private SqlStore sql() {
        return new SqlStore(() -> {
            if (down.get()) throw new SQLException("database down");
            return DriverManager.getConnection(url);
        });
    }

    @Override
    protected ClusterStore store() {
        return sql();
    }

    @Override
    protected void setDown(boolean value) {
        down.set(value);
    }

    @Test
    void creatingTheTablesAgainChangesNothing() throws Exception {
        SqlStore store = sql();
        assertTrue(store.write("grades", List.of(new Change("grade:vip", 0, "{}")), "a").ok());
        store.createTables();
        store.createTables();
        assertEquals(1, store.changesSince("grades", 0).size());
    }

    @Test
    void aRemovedHolderIsKeptAsATombstoneWithANewNumber() throws Exception {
        SqlStore store = sql();
        Row created = store.write("grades", List.of(new Change("grade:vip", 0, "{}")), "a").written().get(0);
        Row removed = store.write("grades", List.of(new Change("grade:vip", created.version(), null)), "a").written().get(0);
        List<Row> after = store.changesSince("grades", created.seq());
        assertEquals(1, after.size());
        assertNull(after.get(0).body());
        assertEquals(removed.seq(), after.get(0).seq());
        assertEquals(2, after.get(0).version());
    }

    @Test
    void aWriteWithOneConflictWritesNothing() throws Exception {
        SqlStore store = sql();
        store.write("grades", List.of(new Change("grade:a", 0, "{\"a\":1}")), "a");
        var result = store.write("grades", List.of(new Change("grade:b", 0, "{}"), new Change("grade:a", 0, "{}")), "b");
        assertFalse(result.ok());
        assertEquals("grade:a", result.conflicts().get(0).holder());
        assertEquals("a", result.conflicts().get(0).updatedBy());
        List<Row> rows = store.changesSince("grades", 0);
        assertEquals(1, rows.size(), "grade:b must not have been written");
    }

    @Test
    void bodiesKeepCharactersOutsideLatin1() throws Exception {
        SqlStore store = sql();
        String body = "{\"prefix\":\"★ été 🔥\"}";
        store.write("grades", List.of(new Change("grade:vip", 0, body)), "a");
        assertEquals(body, store.changesSince("grades", 0).get(0).body());
    }

    /**
     * Two servers writing at once while a third reads "after the last number seen" over and over: every row must
     * reach the reader. A counter that did not serialise writers would let a later number commit first and the
     * reader skip the earlier one.
     */
    @Test
    void concurrentWritersNeverHideARowFromAReader() throws Exception {
        int perWriter = 60;
        ExecutorService pool = Executors.newFixedThreadPool(3);
        AtomicBoolean writing = new AtomicBoolean(true);
        Set<String> seen = new HashSet<>();
        AtomicLong last = new AtomicLong();
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (String server : List.of("a", "b")) {
                SqlStore store = sql();
                writers.add(pool.submit(() -> {
                    for (int i = 0; i < perWriter; i++) {
                        assertTrue(store.write("grades", List.of(new Change("grade:" + server + i, 0, "{}")), server).ok());
                    }
                    return null;
                }));
            }
            SqlStore reader = sql();
            Future<?> reading = pool.submit(() -> {
                while (writing.get()) {
                    for (Row row : reader.changesSince("grades", last.get())) {
                        seen.add(row.holder());
                        last.set(Math.max(last.get(), row.seq()));
                    }
                }
                return null;
            });
            for (Future<?> writer : writers) writer.get(60, TimeUnit.SECONDS);
            writing.set(false);
            reading.get(60, TimeUnit.SECONDS);
            for (Row row : reader.changesSince("grades", last.get())) seen.add(row.holder());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2 * perWriter, seen.size(), "rows skipped by the reader");
    }

    @Test
    void twoLiveInstancesUnderOneNameAreSeenAndALeftOneIsNot() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000);
        SqlStore store = new SqlStore(() -> DriverManager.getConnection(url), now::get);
        assertTrue(store.heartbeat("hub", "one", 30).isEmpty());
        assertEquals(List.of("one"), store.heartbeat("hub", "two", 30));
        assertTrue(store.heartbeat("pvp", "three", 30).isEmpty(), "another name is not a duplicate");

        now.addAndGet(31_000);
        assertTrue(store.heartbeat("hub", "two", 30).isEmpty(), "an instance silent for longer is gone");

        store.leave("hub", "two");
        assertEquals(Set.of("hub", "pvp"), store.servers().keySet());
        now.addAndGet(25L * 3600 * 1000);
        store.heartbeat("pvp", "three", 30);
        assertEquals(Set.of("pvp"), store.servers().keySet(), "day-old heartbeats are forgotten");
    }

    @Test
    void theSharedLogKeepsOrderReadsBackLateEntriesAndPurges() throws Exception {
        SqlStore store = sql();
        LogEntry old = new LogEntry(1_000L, "Alex", "", "command", "grade create vip", true, "Created grade vip");
        LogEntry recent = new LogEntry(90_000L, "Steve", "", "player", "/home", true, "");
        store.appendLog("hub", List.of(new ClusterStore.LogLine("ADMIN", old), new ClusterStore.LogLine("PLAYERS", recent)));

        List<ClusterStore.LogRow> all = store.logAfter(0, Long.MAX_VALUE, 100);
        assertEquals(2, all.size());
        assertEquals("hub", all.get(0).server());
        assertEquals("hub", all.get(0).entry().server());
        long lastId = all.get(1).id();
        assertTrue(store.logAfter(lastId, Long.MAX_VALUE, 100).isEmpty());
        assertEquals(1, store.logAfter(lastId, 60_000L, 100).size(), "entries in the look-back window are read again");

        assertEquals("grade create vip", store.recentLog("ADMIN", 10).get(0).entry().action());
        assertEquals(1, store.purgeLog(50_000L));
        assertEquals(1, store.logAfter(0, Long.MAX_VALUE, 100).size());
    }

    /** H2 needs no server, but the connection it hands out must be closed like a pooled one. */
    @Test
    void connectionsAreGivenBack() throws Exception {
        List<Connection> opened = new ArrayList<>();
        SqlStore store = new SqlStore(() -> {
            Connection c = DriverManager.getConnection(url);
            opened.add(c);
            return c;
        });
        store.write("grades", List.of(new Change("grade:x", 0, "{}")), "a");
        store.changesSince("grades", 0);
        store.heartbeat("a", "i", 30);
        for (Connection c : opened) assertTrue(c.isClosed());
    }
}
