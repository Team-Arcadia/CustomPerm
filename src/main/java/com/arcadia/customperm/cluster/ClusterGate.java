/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.util.VersionUtils;

import java.util.function.BooleanSupplier;

/**
 * Whether cluster mode may run on this server, and what to tell the admin when it may not. Pure Java, so the
 * order of the checks is unit-tested: the first unmet condition is the one reported.
 */
public final class ClusterGate {

    public static final String ARCADIA_LIB_MOD_ID = "arcadia_lib";
    public static final int MIN_MAJOR = 1;
    public static final int MIN_MINOR = 3;
    public static final int MIN_PATCH = 0;
    public static final String MIN_VERSION = MIN_MAJOR + "." + MIN_MINOR + "." + MIN_PATCH;

    public enum State {
        /** {@code cluster.enabled} is false: the server runs alone, as it always has. */
        OFF,
        /** LuckPerms decides and already shares its own storage between servers. */
        LUCKPERMS,
        /** A singleplayer or LAN world: Arcadia Lib never connects there. */
        SINGLEPLAYER,
        NO_ARCADIA_LIB,
        ARCADIA_LIB_TOO_OLD,
        /** Arcadia Lib is recent enough by its version, but a member CustomPerm calls is missing. */
        ARCADIA_LIB_INCOMPATIBLE,
        /** Arcadia Lib runs in memory: database disabled in its config, or unreachable at start. */
        NO_DATABASE,
        /** Another running server already uses this server's name in the cluster. */
        DUPLICATE_NAME,
        /** The database answered Arcadia Lib but refused cluster mode's tables or first read. */
        STORE_FAILED,
        READY
    }

    private ClusterGate() {}

    /**
     * @param arcadiaLibVersion null when Arcadia Lib is not loaded
     * @param databaseActive asked last, and only when every other condition holds; may throw a
     *                       {@link LinkageError} when Arcadia Lib changed its API
     */
    public static State decide(boolean enabled, boolean luckPermsActive, boolean dedicated,
                               String arcadiaLibVersion, BooleanSupplier databaseActive) {
        if (!enabled) return State.OFF;
        if (luckPermsActive) return State.LUCKPERMS;
        if (!dedicated) return State.SINGLEPLAYER;
        if (arcadiaLibVersion == null) return State.NO_ARCADIA_LIB;
        if (!VersionUtils.isVersionAtLeast(arcadiaLibVersion, MIN_MAJOR, MIN_MINOR, MIN_PATCH)) {
            return State.ARCADIA_LIB_TOO_OLD;
        }
        try {
            return databaseActive.getAsBoolean() ? State.READY : State.NO_DATABASE;
        } catch (LinkageError e) {
            return State.ARCADIA_LIB_INCOMPATIBLE;
        }
    }

    /** Why this server runs alone although cluster mode is on; null for {@link State#OFF} and {@link State#READY}. */
    public static String reason(State state, String arcadiaLibVersion) {
        return switch (state) {
            case OFF, READY -> null;
            case LUCKPERMS -> "LuckPerms decides permissions and already shares its storage between servers, so "
                    + "cluster mode does nothing here.";
            case SINGLEPLAYER -> "cluster mode needs a dedicated server; a singleplayer or LAN world runs alone.";
            case NO_ARCADIA_LIB -> "cluster mode needs Arcadia Lib " + MIN_VERSION + " or later, which is not "
                    + "installed. This server runs alone.";
            case ARCADIA_LIB_TOO_OLD -> "cluster mode needs Arcadia Lib " + MIN_VERSION + " or later, and "
                    + arcadiaLibVersion + " is installed. This server runs alone.";
            case ARCADIA_LIB_INCOMPATIBLE -> "Arcadia Lib " + arcadiaLibVersion + " no longer offers what cluster "
                    + "mode uses. This server runs alone; update CustomPerm.";
            case NO_DATABASE -> "Arcadia Lib has no database connection (disabled in its database config, or "
                    + "unreachable at start: see its log lines). This server runs alone.";
            case DUPLICATE_NAME -> "another running server already uses this server's name. Give each server its own "
                    + "server_id in config/arcadia/lib/server.toml (the default is server1). This server runs alone.";
            case STORE_FAILED -> "the database refused cluster mode's tables or first read (see the server log). "
                    + "This server runs alone on its own files.";
        };
    }
}
