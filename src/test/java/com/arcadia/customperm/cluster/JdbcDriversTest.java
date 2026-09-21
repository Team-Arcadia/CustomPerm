/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Finding a driver CustomPerm does not ship, and speaking to it in its own vocabulary. */
class JdbcDriversTest {

    @Test
    void aDriverOnTheClasspathIsFoundByName() {
        Driver driver = JdbcDrivers.first();
        assertNotNull(driver);
        assertEquals("org.mariadb.jdbc.Driver", driver.getClass().getName());
    }

    @Test
    void aServerCarryingNoneGetsNull() {
        assertNull(JdbcDrivers.firstOf("org.example.NoSuchDriver", "com.example.AlsoMissing"));
    }

    @Test
    void theFirstCandidateAvailableWins() {
        Driver driver = JdbcDrivers.firstOf("org.example.NoSuchDriver", "org.h2.Driver", "org.mariadb.jdbc.Driver");
        assertNotNull(driver);
        assertEquals("org.h2.Driver", driver.getClass().getName());
    }

    @Test
    void theUrlUsesASchemeTheDriverAccepts() throws Exception {
        Driver driver = JdbcDrivers.first();
        assertEquals("jdbc:mariadb://db.example:3306/customperm",
                JdbcDrivers.urlFor(driver, "db.example", 3306, "customperm"));
    }

    @Test
    void anIpv6HostIsBracketed() throws Exception {
        Driver driver = JdbcDrivers.first();
        assertEquals("jdbc:mariadb://[2001:db8::1]:3306/customperm",
                JdbcDrivers.urlFor(driver, "2001:db8::1", 3306, "customperm"));
    }

    @Test
    void anAlreadyBracketedHostIsLeftAlone() throws Exception {
        Driver driver = JdbcDrivers.first();
        assertEquals("jdbc:mariadb://[2001:db8::1]:3306/customperm",
                JdbcDrivers.urlFor(driver, "[2001:db8::1]", 3306, "customperm"));
    }

    @Test
    void mariaDbIsToldApartFromTheOthers() throws Exception {
        assertTrue(JdbcDrivers.mariaDb(JdbcDrivers.first()));
        assertFalse(JdbcDrivers.mariaDb(JdbcDrivers.firstOf("org.h2.Driver")));
    }

    @Test
    void aDriverSpeakingNeitherSchemeIsRefused() {
        Driver h2 = JdbcDrivers.firstOf("org.h2.Driver");
        assertNotNull(h2);
        assertThrows(SQLException.class, () -> JdbcDrivers.urlFor(h2, "db.example", 3306, "customperm"));
    }

    @Test
    void theStateSaysWhyTheClusterStayedOut() {
        assertNotNull(ClusterGate.reason(ClusterGate.State.NO_JDBC_DRIVER, null));
    }
}
