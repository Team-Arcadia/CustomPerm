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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;

/**
 * The store in MySQL (or MariaDB), through connections Arcadia Lib lends. Four tables:
 * {@code customperm_rows} (one row per holder, tombstones included), {@code customperm_seq} (the one counter every
 * write takes its number from), {@code customperm_servers} (heartbeats, to catch two servers under one name) and
 * {@code customperm_log} (the shared activity log).
 *
 * <p>Every write starts by bumping the counter, which locks its row until the transaction ends: writers run one
 * after another, so numbers are committed in order and a server reading "after N" never misses a row. The SQL keeps
 * to what MySQL, MariaDB and H2 in MySQL mode (the tests) all accept.</p>
 */
public final class SqlStore implements ClusterStore {

    private static final String ROWS = "customperm_rows";
    private static final String SEQ = "customperm_seq";
    private static final String SERVERS = "customperm_servers";
    private static final String LOG = "customperm_log";
    /** Longest text kept in a log column; the activity log caps its own text well below. */
    private static final int LOG_TEXT_MAX = 2000;
    /** Heartbeats older than this are removed when a server beats. */
    private static final long FORGET_AFTER_MILLIS = 24L * 3600 * 1000;

    private final Callable<Connection> connections;
    private final LongSupplier clock;

    public SqlStore(Callable<Connection> connections) {
        this(connections, System::currentTimeMillis);
    }

    public SqlStore(Callable<Connection> connections, LongSupplier clockMillis) {
        this.connections = connections;
        this.clock = clockMillis;
    }

    /** Creates the tables and the counter row when missing. Safe to run on every start, from every server. */
    public void createTables() throws StoreException {
        try (Connection c = open(); Statement st = c.createStatement()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            // Bodies carry prefixes and nicknames: MySQL's older default character sets cannot hold every one.
            String charset = product.contains("mysql") || product.contains("mariadb") ? " DEFAULT CHARSET=utf8mb4" : "";
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + ROWS + " ("
                    + "part VARCHAR(32) NOT NULL, holder VARCHAR(191) NOT NULL, version BIGINT NOT NULL, "
                    + "body MEDIUMTEXT NULL, updated_by VARCHAR(64) NOT NULL, seq BIGINT NOT NULL, "
                    + "PRIMARY KEY (part, holder), KEY customperm_rows_seq (part, seq))" + charset);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + SEQ + " (id INT NOT NULL PRIMARY KEY, seq BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + SERVERS + " ("
                    + "server VARCHAR(64) NOT NULL, instance VARCHAR(64) NOT NULL, seen BIGINT NOT NULL, "
                    + "PRIMARY KEY (server, instance))" + charset);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + LOG + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, server VARCHAR(64) NOT NULL, kind VARCHAR(16) NOT NULL, "
                    + "time BIGINT NOT NULL, actor VARCHAR(191) NOT NULL, actor_id VARCHAR(64) NOT NULL, "
                    + "source VARCHAR(32) NOT NULL, action TEXT NOT NULL, success BOOLEAN NOT NULL, result TEXT NOT NULL, "
                    + "KEY customperm_log_time (time))" + charset);
            try {
                st.executeUpdate("INSERT INTO " + SEQ + " (id, seq) VALUES (1, 0)");
            } catch (SQLIntegrityConstraintViolationException exists) {
                // Another server, or an earlier start, created it.
            } catch (SQLException e) {
                if (!isDuplicate(e)) throw e;
            }
        } catch (SQLException e) {
            throw new StoreException("creating the cluster tables failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Row> changesSince(String part, long afterSeq) throws StoreException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT holder, version, body, updated_by, seq FROM " + ROWS
                     + " WHERE part = ? AND seq > ? ORDER BY seq")) {
            ps.setString(1, part);
            ps.setLong(2, afterSeq);
            List<Row> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(row(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw new StoreException("reading the cluster store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public WriteResult write(String part, List<Change> changes, String server) throws StoreException {
        try (Connection c = open()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long number = nextNumber(c);
                List<Row> written = new ArrayList<>();
                List<String> conflicting = new ArrayList<>();
                for (Change change : changes) {
                    if (apply(c, part, change, server, number)) {
                        written.add(new Row(change.holder(), change.expectedVersion() + 1, change.body(), server, number));
                    } else {
                        conflicting.add(change.holder());
                    }
                }
                if (!conflicting.isEmpty()) {
                    c.rollback();
                    return new WriteResult(List.of(), current(c, part, conflicting));
                }
                c.commit();
                return new WriteResult(written, List.of());
            } catch (SQLException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // The connection is going back to the pool broken either way; the first error is the one to report.
                }
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new StoreException("writing to the cluster store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> heartbeat(String server, String instance, int liveSeconds) throws StoreException {
        long now = clock.getAsLong();
        try (Connection c = open()) {
            try (PreparedStatement up = c.prepareStatement("UPDATE " + SERVERS + " SET seen = ? WHERE server = ? AND instance = ?")) {
                up.setLong(1, now);
                up.setString(2, server);
                up.setString(3, instance);
                if (up.executeUpdate() == 0) {
                    try (PreparedStatement in = c.prepareStatement("INSERT INTO " + SERVERS + " (server, instance, seen) VALUES (?, ?, ?)")) {
                        in.setString(1, server);
                        in.setString(2, instance);
                        in.setLong(3, now);
                        in.executeUpdate();
                    }
                }
            }
            try (PreparedStatement old = c.prepareStatement("DELETE FROM " + SERVERS + " WHERE seen < ?")) {
                old.setLong(1, now - FORGET_AFTER_MILLIS);
                old.executeUpdate();
            }
            List<String> others = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT instance FROM " + SERVERS
                    + " WHERE server = ? AND instance <> ? AND seen >= ?")) {
                ps.setString(1, server);
                ps.setString(2, instance);
                ps.setLong(3, now - liveSeconds * 1000L);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) others.add(rs.getString(1));
                }
            }
            return others;
        } catch (SQLException e) {
            throw new StoreException("recording this server in the cluster store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void leave(String server, String instance) throws StoreException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + SERVERS + " WHERE server = ? AND instance = ?")) {
            ps.setString(1, server);
            ps.setString(2, instance);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("leaving the cluster store failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Long> servers() throws StoreException {
        long now = clock.getAsLong();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT server, MAX(seen) FROM " + SERVERS + " GROUP BY server");
             ResultSet rs = ps.executeQuery()) {
            Map<String, Long> ages = new HashMap<>();
            while (rs.next()) ages.put(rs.getString(1), Math.max(0, (now - rs.getLong(2)) / 1000));
            return ages;
        } catch (SQLException e) {
            throw new StoreException("reading the cluster servers failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void appendLog(String server, List<LogLine> lines) throws StoreException {
        if (lines.isEmpty()) return;
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("INSERT INTO " + LOG
                     + " (server, kind, time, actor, actor_id, source, action, success, result) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (LogLine line : lines) {
                LogEntry e = line.entry();
                ps.setString(1, server);
                ps.setString(2, line.kind());
                ps.setLong(3, e.time());
                ps.setString(4, cut(e.actor(), 191));
                ps.setString(5, cut(e.actorId(), 64));
                ps.setString(6, cut(e.source(), 32));
                ps.setString(7, cut(e.action(), LOG_TEXT_MAX));
                ps.setBoolean(8, e.success());
                ps.setString(9, cut(e.result(), LOG_TEXT_MAX));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new StoreException("writing the shared activity log failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<LogRow> logAfter(long afterId, long sinceTime, int limit) throws StoreException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT id, server, kind, time, actor, actor_id, source, action, "
                     + "success, result FROM " + LOG + " WHERE id > ? OR time >= ? ORDER BY id LIMIT " + Math.max(1, limit))) {
            ps.setLong(1, afterId);
            ps.setLong(2, sinceTime);
            return logRows(ps);
        } catch (SQLException e) {
            throw new StoreException("reading the shared activity log failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<LogRow> recentLog(String kind, int limit) throws StoreException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT id, server, kind, time, actor, actor_id, source, action, "
                     + "success, result FROM " + LOG + " WHERE kind = ? ORDER BY id DESC LIMIT " + Math.max(1, limit))) {
            ps.setString(1, kind);
            return logRows(ps);
        } catch (SQLException e) {
            throw new StoreException("reading the shared activity log failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int purgeLog(long beforeTime) throws StoreException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + LOG + " WHERE time < ?")) {
            ps.setLong(1, beforeTime);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("purging the shared activity log failed: " + e.getMessage(), e);
        }
    }

    private static List<LogRow> logRows(PreparedStatement ps) throws SQLException {
        List<LogRow> rows = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new LogRow(rs.getLong(1), rs.getString(2), rs.getString(3), new LogEntry(rs.getLong(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getBoolean(9),
                        rs.getString(10), rs.getString(2))));
            }
        }
        return rows;
    }

    private static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Takes the next number, locking the counter until the transaction ends. */
    private static long nextNumber(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            if (st.executeUpdate("UPDATE " + SEQ + " SET seq = seq + 1 WHERE id = 1") != 1) {
                throw new SQLException("the cluster counter row is missing");
            }
            try (ResultSet rs = st.executeQuery("SELECT seq FROM " + SEQ + " WHERE id = 1")) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** One change against the version it expects. False when someone else changed the holder meanwhile. */
    private static boolean apply(Connection c, String part, Change change, String server, long number) throws SQLException {
        if (change.expectedVersion() == 0) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + ROWS
                    + " (part, holder, version, body, updated_by, seq) VALUES (?, ?, 1, ?, ?, ?)")) {
                ps.setString(1, part);
                ps.setString(2, change.holder());
                ps.setString(3, change.body());
                ps.setString(4, server);
                ps.setLong(5, number);
                ps.executeUpdate();
                return true;
            } catch (SQLIntegrityConstraintViolationException taken) {
                return false;
            } catch (SQLException e) {
                if (isDuplicate(e)) return false;
                throw e;
            }
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + ROWS + " SET version = version + 1, body = ?, "
                + "updated_by = ?, seq = ? WHERE part = ? AND holder = ? AND version = ?")) {
            ps.setString(1, change.body());
            ps.setString(2, server);
            ps.setLong(3, number);
            ps.setString(4, part);
            ps.setString(5, change.holder());
            ps.setLong(6, change.expectedVersion());
            return ps.executeUpdate() == 1;
        }
    }

    private static List<Row> current(Connection c, String part, List<String> holders) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT holder, version, body, updated_by, seq FROM " + ROWS
                + " WHERE part = ? AND holder = ?")) {
            for (String holder : holders) {
                ps.setString(1, part);
                ps.setString(2, holder);
                try (ResultSet rs = ps.executeQuery()) {
                    // Gone since: reported as a tombstone, so the caller still knows which holder conflicted.
                    rows.add(rs.next() ? row(rs) : new Row(holder, 0, null, "", 0));
                }
            }
        }
        return rows;
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5));
    }

    /** SQLState class 23 is an integrity violation; some drivers do not use the dedicated exception type. */
    private static boolean isDuplicate(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }

    private Connection open() throws SQLException {
        try {
            return connections.call();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("no connection: " + e.getMessage(), e);
        }
    }
}
