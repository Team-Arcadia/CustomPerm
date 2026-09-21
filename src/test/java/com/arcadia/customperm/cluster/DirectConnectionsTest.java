/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.cluster.ClusterGate.State;
import com.arcadia.customperm.config.SettingsConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Cluster mode connecting itself: the gate, the pool, and the store contract run through the pool. */
class DirectConnectionsTest extends PartSyncContract {

    private String url;
    private DirectConnections pool;
    private final AtomicBoolean down = new AtomicBoolean();

    @BeforeEach
    void database() throws Exception {
        url = "jdbc:h2:mem:cpd" + UUID.randomUUID().toString().replace("-", "") + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        pool = new DirectConnections(new org.h2.Driver(), url, new Properties());
        new SqlStore(pool::get).createTables();
    }

    @Override
    protected ClusterStore store() {
        return new SqlStore(() -> {
            if (down.get()) throw new SQLException("database down");
            return pool.get();
        });
    }

    @Override
    protected void setDown(boolean value) {
        down.set(value);
    }

    @Test
    void aClosedConnectionGoesBackAndIsHandedOutAgain() throws Exception {
        Connection first = pool.get();
        first.close();
        assertTrue(first.isClosed(), "given back, the caller's handle reads as closed");
        assertThrows(SQLException.class, first::createStatement, "a handle given back cannot be used any more");
        assertEquals(1, pool.idleCount());
        try (Connection second = pool.get()) {
            assertEquals(0, pool.idleCount(), "the kept connection is the one handed out");
            assertTrue(second.isValid(1));
        }
        assertEquals(1, pool.idleCount());
    }

    @Test
    void aConnectionLeftInATransactionIsNotKept() throws Exception {
        Connection c = pool.get();
        c.setAutoCommit(false);
        c.close();
        assertEquals(0, pool.idleCount());
    }

    @Test
    void closingThePoolRefusesNewConnections() throws Exception {
        pool.get().close();
        pool.close();
        assertEquals(0, pool.idleCount());
        assertThrows(SQLException.class, pool::get);
    }

    @Test
    void theResolvedDriverLoadsAndFailsCleanlyOnAClosedPort() throws Exception {
        java.sql.Driver driver = JdbcDrivers.first();
        assertNotNull(driver, "the test classpath carries a driver to resolve");
        Properties props = new Properties();
        props.setProperty("user", "nobody");
        props.setProperty("connectTimeout", "1000");
        DirectConnections closed = new DirectConnections(driver,
                JdbcDrivers.urlFor(driver, "127.0.0.1", 1, "customperm"), props);
        assertThrows(SQLException.class, closed::get);
    }

    @Test
    void aDirectConnectionNeedsANameAndAUserButNotArcadiaLib() {
        assertEquals(State.NO_SERVER_NAME, ClusterGate.decide(true, false, true, true, "", "cp", null, () -> {
            throw new AssertionError("Arcadia Lib must not be asked");
        }));
        assertEquals(State.NO_SERVER_NAME, ClusterGate.decide(true, false, true, true, "bad name", "cp", null, () -> false));
        assertEquals(State.NO_DATABASE_USER, ClusterGate.decide(true, false, true, true, "hub", " ", null, () -> false));
        assertEquals(State.READY, ClusterGate.decide(true, false, true, true, "hub", "cp", null, () -> false));
        assertEquals(State.LUCKPERMS, ClusterGate.decide(true, true, true, true, "hub", "cp", null, () -> false),
                "LuckPerms still wins over a direct connection");
    }

    @Test
    void connectionSettingsAreReadWithSafeDefaults() {
        SettingsConfig settings = new Gson().fromJson("{\"cluster\": {\"connection\": \"DIRECT\", \"serverName\": \" hub \", "
                + "\"database\": {\"port\": 0, \"tls\": \"maybe\", \"host\": \" \"}}}", SettingsConfig.class);
        settings.normalize();
        assertTrue(settings.cluster.direct());
        assertEquals("hub", settings.cluster.serverName);
        assertEquals(3306, settings.cluster.database.port);
        assertEquals("localhost", settings.cluster.database.host);
        assertEquals(SettingsConfig.Database.TLS_OFF, settings.cluster.database.tls);

        SettingsConfig defaults = new SettingsConfig();
        defaults.normalize();
        assertFalse(defaults.cluster.direct(), "Arcadia Lib stays the default connection");
    }
}
