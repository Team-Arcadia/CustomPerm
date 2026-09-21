/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.SettingsConfig;
import org.junit.jupiter.api.Test;

import java.sql.Driver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the direct connection does with the driver it was handed: the case where a server carries none is the one
 * that leaves cluster mode out, and it is reachable here without a server that actually carries none.
 */
class DirectStoreTest {

    private static SettingsConfig.Database database() {
        SettingsConfig.Database db = new SettingsConfig.Database();
        db.host = "127.0.0.1";
        db.port = 3306;
        db.name = "customperm";
        db.user = "cp";
        db.password = "cp";
        db.tls = SettingsConfig.Database.TLS_OFF;
        return db;
    }

    @Test
    void aServerCarryingNoDriverGetsNoStore() {
        assertNull(Cluster.directStore(null, database()),
                "no driver means no store, which is what turns into NO_JDBC_DRIVER");
    }

    @Test
    void aDriverAcceptingNeitherSchemeGetsNoStore() {
        Driver h2 = JdbcDrivers.firstOf("org.h2.Driver");
        assertNotNull(h2, "the test classpath carries H2");
        assertNull(Cluster.directStore(h2, database()),
                "a driver that speaks neither jdbc:mariadb nor jdbc:mysql cannot reach the cluster");
    }

    @Test
    void theResolvedDriverOpensAStore() {
        Driver driver = JdbcDrivers.first();
        assertNotNull(driver, "the test classpath carries a driver to resolve");
        assertNotNull(Cluster.directStore(driver, database()),
                "a usable driver yields a store; connecting is a later step");
    }

    @Test
    void theStateThatCaseProducesSaysWhatToInstall() {
        String reason = ClusterGate.reason(ClusterGate.State.NO_JDBC_DRIVER, null);
        assertNotNull(reason);
        assertTrue(reason.contains("JDBC driver"), reason);
        assertTrue(reason.contains("Arcadia Lib"), "the message must name a mod that provides one: " + reason);
    }
}
