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
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

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
    /** The running cluster, null while this server runs alone. */
    private static volatile ClusterService service;

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
        if (state == ClusterGate.State.READY) join(event.getServer());
        report(version);
    }

    /** Opens the SQL store on Arcadia Lib's connections and joins it; any failure leaves this server alone. */
    private static void join(MinecraftServer server) {
        SqlStore store = new SqlStore(ArcadiaLibBridge::connection);
        String instance = UUID.randomUUID().toString();
        try {
            store.createTables();
            List<String> others = store.heartbeat(serverName, instance, ClusterService.LIVE_SECONDS);
            if (!others.isEmpty()) {
                state = ClusterGate.State.DUPLICATE_NAME;
                return;
            }
            attach(store, serverName, instance, server);
        } catch (ClusterStore.StoreException | RuntimeException e) {
            CustomPerm.LOGGER.error("[CustomPerm] Cluster: joining the store failed", e);
            state = ClusterGate.State.STORE_FAILED;
        }
        if (state != ClusterGate.State.READY) serverName = null;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        detach();
        state = ClusterGate.State.OFF;
        serverName = null;
        decidedWith = null;
    }

    /** Whether this server is in step with a cluster store. */
    public static boolean running() {
        return service != null;
    }

    /**
     * Joins {@code store} as {@code name}: an empty store is filled from this server's grades, a filled one
     * replaces them. On the server thread. Public for the GameTests, which run two servers against one store.
     */
    public static void attach(ClusterStore store, String name, MinecraftServer server) throws ClusterStore.StoreException {
        attach(store, name, UUID.randomUUID().toString(), server);
    }

    private static void attach(ClusterStore store, String name, String instance, MinecraftServer server)
            throws ClusterStore.StoreException {
        detach();
        ClusterService started = new ClusterService(store, name, instance, server,
                CustomPerm.configManager.getSettings().cluster.share);
        started.start();
        service = started;
    }

    public static void detach() {
        ClusterService running = service;
        service = null;
        if (running != null) running.stop();
    }

    /**
     * Writes the change just made to the configuration, when a cluster runs. Null when written or when no
     * cluster runs; otherwise the reason it was refused, the change having been undone.
     */
    public static String publish() {
        ClusterService running = service;
        return running == null ? null : running.publish();
    }

    /** Reads and applies what the other servers changed, now. The server thread; for the GameTests. */
    public static boolean pollNow() throws ClusterStore.StoreException {
        ClusterService running = service;
        return running != null && running.pollNow();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ClusterService running = service;
        if (running != null) running.tick();
    }

    /** A line for the reload answer when the {@code cluster} settings changed; null otherwise. */
    public static String reloadNote() {
        String before = decidedWith;
        if (before == null || before.equals(fingerprint(CustomPerm.configManager.getSettings().cluster))) return null;
        CustomPerm.LOGGER.info("[CustomPerm] Cluster settings changed; they apply at the next server start.");
        return "Cluster settings changed: they apply at the next server start.";
    }

    private static void report(String version) {
        switch (state) {
            case OFF -> AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "cluster mode is off.");
            case LUCKPERMS -> CustomPerm.LOGGER.info("[CustomPerm] {}", ClusterGate.reason(state, version));
            case READY -> {
                ClusterService running = service;
                CustomPerm.LOGGER.info("[CustomPerm] Cluster mode: Arcadia Lib {} database connected, this server is \"{}\", "
                        + "sharing {}.", version, serverName, running == null ? "nothing" : running.sharedParts());
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
