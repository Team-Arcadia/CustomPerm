/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

/**
 * Connections cluster mode opens itself, without Arcadia Lib. A handful kept open and handed out again: the
 * cluster reads the database every couple of seconds, and a new connection each time would pay the handshake
 * each time. What a caller closes goes back here instead, unless it broke.
 *
 * <p>The driver is instantiated, not looked up through {@code DriverManager}, which does not see a driver packed
 * inside a mod.</p>
 */
public final class DirectConnections implements AutoCloseable {

    /** Connections kept open at most; the cluster uses one at a time, rarely two. */
    private static final int IDLE_MAX = 3;
    private static final int VALID_SECONDS = 2;

    private final Driver driver;
    private final String url;
    private final Properties properties;
    private final Deque<Connection> idle = new ArrayDeque<>();
    private boolean closed;

    public DirectConnections(Driver driver, String url, Properties properties) {
        this.driver = driver;
        this.url = url;
        this.properties = properties;
    }

    /** A connection to use and close; closing gives it back. */
    public Connection get() throws SQLException {
        Connection raw = null;
        synchronized (this) {
            if (closed) throw new SQLException("cluster connections are closed");
            while (raw == null && !idle.isEmpty()) {
                Connection candidate = idle.pop();
                if (candidate.isValid(VALID_SECONDS)) {
                    raw = candidate;
                } else {
                    quietlyClose(candidate);
                }
            }
        }
        if (raw == null) {
            raw = driver.connect(url, properties);
            if (raw == null) throw new SQLException("the driver does not accept " + url);
        }
        return lend(raw);
    }

    @Override
    public synchronized void close() {
        closed = true;
        idle.forEach(DirectConnections::quietlyClose);
        idle.clear();
    }

    /** Connections kept open now, for tests. */
    synchronized int idleCount() {
        return idle.size();
    }

    private Connection lend(Connection raw) {
        boolean[] returned = {false};
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "close" -> {
                    if (!returned[0]) {
                        returned[0] = true;
                        giveBack(raw);
                    }
                    return null;
                }
                case "isClosed" -> {
                    return returned[0] || raw.isClosed();
                }
                default -> {
                    if (returned[0]) throw new SQLException("connection already given back");
                    try {
                        return method.invoke(raw, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    private void giveBack(Connection raw) {
        boolean keep;
        try {
            // A connection left inside a transaction, or broken, is not one to hand out again.
            keep = !raw.isClosed() && raw.getAutoCommit();
        } catch (SQLException e) {
            keep = false;
        }
        synchronized (this) {
            if (keep && !closed && idle.size() < IDLE_MAX) {
                idle.push(raw);
                return;
            }
        }
        quietlyClose(raw);
    }

    private static void quietlyClose(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Closing a connection that is already gone: nothing left to release.
        }
    }
}
