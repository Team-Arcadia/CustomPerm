/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Cluster mode: several servers on the internal backend sharing their grades through Arcadia Lib's MySQL
 * connection. Decided once, when the server has started and Arcadia Lib has opened its pool; a change of the
 * {@code cluster} settings applies at the next start, since joining or leaving a cluster is not something to do
 * under players' feet.
 */
public final class Cluster {

    private static volatile ClusterGate.State state = ClusterGate.State.OFF;
    private static volatile String serverName;
    /** The settings the state was decided with, to tell an admin when a reload changed them. */
    private static volatile String decidedWith;

    private Cluster() {}

    public static ClusterGate.State state() {
        return state;
    }

    /** This server's name in the cluster, null unless {@link ClusterGate.State#READY}. */
    public static String serverName() {
        return serverName;
    }

    public static void onServerStarted(ServerStartedEvent event) {
        SettingsConfig.Cluster settings = CustomPerm.configManager.getSettings().cluster;
        String version = arcadiaLibVersion();
        state = ClusterGate.decide(settings.enabled, CustomPerm.isLuckPermsActive(),
                event.getServer().isDedicatedServer(), version, ArcadiaLibBridge::databaseActive);
        decidedWith = fingerprint(settings);
        serverName = state == ClusterGate.State.READY ? ArcadiaLibBridge.serverId() : null;
        report(version);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        state = ClusterGate.State.OFF;
        serverName = null;
        decidedWith = null;
    }

    /** A line for the reload answer when the {@code cluster} settings changed; null otherwise. */
    public static String reloadNote() {
        String before = decidedWith;
        if (before == null || before.equals(fingerprint(CustomPerm.configManager.getSettings().cluster))) return null;
        CustomPerm.LOGGER.info("[CustomPerm] Cluster settings changed; they apply at the next server start.");
        return "Cluster settings changed: they apply at the next server start.";
    }

    /** A connection from Arcadia Lib's pool, only while {@link ClusterGate.State#READY}. The caller closes it. */
    static Connection connection() throws SQLException {
        if (state != ClusterGate.State.READY) throw new SQLException("cluster mode is not running");
        return ArcadiaLibBridge.connection();
    }

    private static void report(String version) {
        switch (state) {
            case OFF -> AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "cluster mode is off.");
            case LUCKPERMS -> CustomPerm.LOGGER.info("[CustomPerm] {}", ClusterGate.reason(state, version));
            case READY -> {
                CustomPerm.LOGGER.info("[CustomPerm] Cluster mode: Arcadia Lib {} database connected, this server is \"{}\".",
                        version, serverName);
                AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "cluster mode is connected.");
            }
            default -> AdminNotifier.raise(AdminAlerts.Key.CLUSTER_UNAVAILABLE,
                    "Cluster mode is on, but " + ClusterGate.reason(state, version));
        }
    }

    /** Arcadia Lib's version, null when it is not loaded. */
    private static String arcadiaLibVersion() {
        return ModList.get().getModContainerById(ClusterGate.ARCADIA_LIB_MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static String fingerprint(SettingsConfig.Cluster c) {
        SettingsConfig.Share s = c.share;
        return c.enabled + "|" + c.pollSeconds + "|" + c.whenDatabaseLost + "|" + s.grades + s.commands + s.aliases
                + s.rateLimits + s.rateLimitCounters + s.log;
    }
}
