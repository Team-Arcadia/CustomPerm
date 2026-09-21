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

import java.sql.Driver;
import java.sql.SQLException;

/**
 * Finds a JDBC driver already loaded on this server. CustomPerm ships none: the direct cluster connection uses
 * whatever driver another mod brought, and says so rather than failing obscurely when there is none.
 *
 * <p>The driver is instantiated by name rather than looked up through {@code DriverManager}, which does not see a
 * driver packed inside a mod.</p>
 */
public final class JdbcDrivers {

    /** Drivers cluster mode can drive, in the order it looks for them. */
    static final String[] CANDIDATES = {"org.mariadb.jdbc.Driver", "com.mysql.cj.jdbc.Driver"};

    /** URL schemes offered to a driver, in the order they are tried. */
    private static final String[] SCHEMES = {"jdbc:mariadb://", "jdbc:mysql://"};

    private JdbcDrivers() {}

    /** The first candidate this server can load, or null when it carries none. */
    public static Driver first() {
        return firstOf(CANDIDATES);
    }

    /** {@link #first()} over a given list, to name the candidates rather than take the standard order. */
    public static Driver firstOf(String... candidates) {
        for (String candidate : candidates) {
            try {
                return (Driver) Class.forName(candidate).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError absent) {
                // Not on this server: try the next one.
            }
        }
        return null;
    }

    /**
     * The value to give the {@code sslMode} connection property, in the vocabulary the driver understands.
     * MariaDB and MySQL name the same three modes differently and each rejects the other's spelling, so the
     * translation has to happen here rather than at the call site.
     */
    public static String sslMode(boolean mariaDb, String tls) {
        if (mariaDb) {
            return switch (tls) {
                case SettingsConfig.Database.TLS_TRUST -> "trust";
                case SettingsConfig.Database.TLS_VERIFY -> "verify-full";
                default -> "disable";
            };
        }
        return switch (tls) {
            case SettingsConfig.Database.TLS_TRUST -> "REQUIRED";
            case SettingsConfig.Database.TLS_VERIFY -> "VERIFY_IDENTITY";
            default -> "DISABLED";
        };
    }

    /**
     * Whether this driver speaks MariaDB's own URL scheme. It also decides the option vocabulary: MariaDB names its
     * TLS modes {@code disable}, {@code trust} and {@code verify-full}, MySQL names them {@code DISABLED},
     * {@code REQUIRED} and {@code VERIFY_IDENTITY}, and each rejects the other's.
     */
    public static boolean mariaDb(Driver driver) throws SQLException {
        return driver.acceptsURL(SCHEMES[0] + "host:3306/database");
    }

    /**
     * The URL this driver accepts for that database, MariaDB's scheme first. An IPv6 host is bracketed, or the
     * colons read as the port separator.
     */
    public static String urlFor(Driver driver, String host, int port, String database) throws SQLException {
        String written = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        for (String scheme : SCHEMES) {
            String url = scheme + written + ":" + port + "/" + database;
            if (driver.acceptsURL(url)) return url;
        }
        throw new SQLException("the JDBC driver on this server accepts neither " + SCHEMES[0] + " nor " + SCHEMES[1]);
    }
}
