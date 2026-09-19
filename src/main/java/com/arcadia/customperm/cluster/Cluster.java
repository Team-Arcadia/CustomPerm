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
        boolean direct = settings.direct();
        state = ClusterGate.decide(settings.enabled, CustomPerm.isLuckPermsActive(),
                event.getServer().isDedicatedServer(), direct, settings.serverName, settings.database.user, version,
                ArcadiaLibBridge::databaseActive);
        decidedWith = fingerprint(settings);
        serverName = state != ClusterGate.State.READY ? null : direct ? settings.serverName : ArcadiaLibBridge.serverId();
        if (state == ClusterGate.State.READY) {
            join(event.getServer(), direct ? directStore(settings.database) : new SqlStore(ArcadiaLibBridge::connection));
        }
        report(direct ? "direct connection to " + settings.database.host + ":" + settings.database.port
                + "/" + settings.database.name : "Arcadia Lib " + version, version);
    }

    /** CustomPerm's own connections, closed with the server. */
    private static volatile DirectConnections directConnections;

    private static SqlStore directStore(com.arcadia.customperm.config.SettingsConfig.Database db) {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", db.user);
        props.setProperty("password", db.password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "10000");
        props.setProperty("sslMode", switch (db.tls) {
            case com.arcadia.customperm.config.SettingsConfig.Database.TLS_TRUST -> "trust";
            case com.arcadia.customperm.config.SettingsConfig.Database.TLS_VERIFY -> "verify-full";
            default -> "disable";
        });
        // IPv6 hosts are bracketed in a JDBC URL, or the colons read as the port separator.
        String host = db.host.contains(":") && !db.host.startsWith("[") ? "[" + db.host + "]" : db.host;
        DirectConnections connections = new DirectConnections(new org.mariadb.jdbc.Driver(),
                "jdbc:mariadb://" + host + ":" + db.port + "/" + db.name, props);
        directConnections = connections;
        return new SqlStore(connections::get);
    }

    /** Opens {@code store} and joins it; any failure leaves this server alone. */
    private static void join(MinecraftServer server, SqlStore store) {
        String instance = UUID.randomUUID().toString();
        try {
            store.createTables();
            List<String> others = store.heartbeat(serverName, instance, ClusterService.LIVE_SECONDS);
            if (!others.isEmpty()) {
                state = ClusterGate.State.DUPLICATE_NAME;
                // The heartbeat just written would make the server that owns the name believe it has a double.
                store.leave(serverName, instance);
                serverName = null;
                closeDirect();
                return;
            }
            attach(store, serverName, instance, server);
        } catch (ClusterStore.StoreException | RuntimeException e) {
            CustomPerm.LOGGER.error("[CustomPerm] Cluster: joining the store failed", e);
            state = ClusterGate.State.STORE_FAILED;
        }
        if (state != ClusterGate.State.READY) {
            serverName = null;
            closeDirect();
        }
    }

    private static void closeDirect() {
        DirectConnections open = directConnections;
        directConnections = null;
        if (open != null) open.close();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        detach();
        closeDirect();
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
        // The name first: joining may already store entries limited to server=<name>.
        serverName = name;
        ClusterService started = new ClusterService(store, name, instance, server,
                CustomPerm.configManager.getSettings().cluster.share);
        started.start();
        service = started;
    }

    public static void detach() {
        ClusterService running = service;
        service = null;
        if (state != ClusterGate.State.READY) serverName = null;
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

    /**
     * The contexts set on this server beyond the world and the game mode: {@code statics}, plus {@code server=<name>}
     * while a cluster runs. What an entry's context is checked against before it is stored, and what every player
     * carries. The same map instance comes back while nothing changed, for the callers that cache on it.
     */
    public static java.util.Map<String, String> declared(java.util.Map<String, String> statics) {
        String name = serverName();
        if (service == null || name == null) return statics;
        java.util.Map<String, String> cached = declaredCache;
        if (cached != null && declaredFrom == statics && name.equals(declaredName)) return cached;
        java.util.TreeMap<String, String> all = new java.util.TreeMap<>(statics);
        all.put(com.arcadia.customperm.perm.Contexts.SERVER, name.toLowerCase(java.util.Locale.ROOT));
        java.util.Map<String, String> made = java.util.Collections.unmodifiableMap(all);
        declaredFrom = statics;
        declaredName = name;
        declaredCache = made;
        return made;
    }

    private static volatile java.util.Map<String, String> declaredCache;
    private static volatile java.util.Map<String, String> declaredFrom;
    private static volatile String declaredName;

    /** An activity log entry recorded here, shared when a cluster runs with {@code share.log}. Any thread. */
    public static void log(com.arcadia.customperm.log.LogKind kind, com.arcadia.customperm.log.LogEntry entry) {
        ClusterService running = service;
        if (running != null) running.log(kind, entry);
    }

    /** A rate-limited use counted here, shared when its rule's scope reaches other servers. Any thread. */
    public static void use(String command, java.util.UUID player, long time) {
        ClusterService running = service;
        if (running != null) running.use(command, player, time);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ClusterService running = service;
        if (running != null) running.tick();
    }

    /**
     * One line for the dashboard: this server's name, what it shares and which servers it hears; why it runs alone
     * when cluster mode is on; empty when cluster mode is off or LuckPerms decides.
     */
    public static String summary() {
        ClusterService running = service;
        if (running != null) {
            java.util.Map<String, Long> peers = running.peers();
            String others = peers.isEmpty() ? "no other server heard yet"
                    : "with " + String.join(", ", new java.util.TreeMap<>(peers).entrySet().stream()
                            .map(e -> e.getKey() + " (" + e.getValue() + " s ago)").toList());
            return "Cluster \"" + running.name() + "\", " + others + ". Sharing " + String.join(", ", running.sharedParts()) + ".";
        }
        return switch (state) {
            case OFF, LUCKPERMS -> "";
            default -> "Cluster mode is on, but this server runs alone: see the alert.";
        };
    }

    /** A line for the reload answer when the {@code cluster} settings changed; null otherwise. */
    public static String reloadNote() {
        String before = decidedWith;
        if (before == null || before.equals(fingerprint(CustomPerm.configManager.getSettings().cluster))) return null;
        CustomPerm.LOGGER.info("[CustomPerm] Cluster settings changed; they apply at the next server start.");
        return "Cluster settings changed: they apply at the next server start.";
    }

    private static void report(String how, String version) {
        switch (state) {
            case OFF -> AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "cluster mode is off.");
            case LUCKPERMS -> CustomPerm.LOGGER.info("[CustomPerm] {}", ClusterGate.reason(state, version));
            case READY -> {
                ClusterService running = service;
                CustomPerm.LOGGER.info("[CustomPerm] Cluster mode: {} connected, this server is \"{}\", sharing {}.",
                        how, serverName, running == null ? "nothing" : running.sharedParts());
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
        var db = c.database;
        return c.enabled + "|" + c.connection + "|" + c.serverName + "|" + db.host + "|" + db.port + "|" + db.name + "|"
                + db.user + "|" + db.password.hashCode() + "|" + db.tls + "|" + c.pollSeconds + "|" + c.whenDatabaseLost
                + "|" + s.grades + s.commands + s.aliases + s.rateLimits + s.log;
    }
}
