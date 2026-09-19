/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.lib.ServerContext;
import com.arcadia.lib.data.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The only class that names Arcadia Lib. The JVM loads it the first time one of its methods runs, and
 * {@link Cluster} calls them only once {@code arcadia_lib} is loaded, so CustomPerm loads without Arcadia Lib.
 * Only JDK types cross this boundary.
 */
final class ArcadiaLibBridge {

    private ArcadiaLibBridge() {}

    /** False when Arcadia Lib runs in memory: singleplayer, debug mode, database disabled or unreachable. */
    static boolean databaseActive() {
        return DatabaseManager.isDatabaseActive();
    }

    /** A connection from Arcadia Lib's pool; the caller closes it. */
    static Connection connection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    /** Arcadia Lib's server name, {@code server1} when nobody set one. */
    static String serverId() {
        return ServerContext.SERVER_ID;
    }
}
