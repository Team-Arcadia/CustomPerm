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
import com.arcadia.customperm.admin.ConfigAdmin;
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.UsernameCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Cluster mode running on this server: the shared parts of the configuration kept in step with the store. Reads of
 * the store run on one thread of its own; everything that touches the configuration runs on the server thread, like
 * every other change to it. A change made here is written before the command answers, so a refusal is the answer.
 */
final class ClusterService {

    /** How long a server may stay silent before it no longer counts as running. */
    static final int LIVE_SECONDS = 45;
    private static final int HEARTBEAT_TICKS = 10 * 20;

    private final ClusterStore store;
    private final String name;
    private final String instance;
    private final MinecraftServer server;
    private final List<PartSync<?>> parts = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CustomPerm-Cluster");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean polling = new AtomicBoolean();
    private int ticks;
    private int heartbeatTicks;

    ClusterService(ClusterStore store, String name, String instance, MinecraftServer server, SettingsConfig.Share share) {
        this.store = store;
        this.name = name;
        this.instance = instance;
        this.server = server;
        var config = CustomPerm.configManager;
        if (share.grades) {
            add(new GradesCodec(), config::getGrades, () -> PermissionService.get().onConfigReload(config.getSnapshot()));
        }
        if (share.commands) add(new CommandsCodec(), config::getCommands, this::commandTreeChanged);
        if (share.aliases) add(new AliasesCodec(), config::getAliases, this::commandTreeChanged);
        if (share.rateLimits) add(new RateLimitsCodec(), config::getRateLimits, () -> { });
    }

    String name() {
        return name;
    }

    /** Names of the parts shared, for the log and the dashboard. */
    List<String> sharedParts() {
        return parts.stream().<String>map(PartSync::part).toList();
    }

    /** First contact, on the server thread. A part the store already holds replaces this server's, backed up first. */
    void start() throws ClusterStore.StoreException {
        CustomPerm.configManager.backupNow();
        for (PartSync<?> part : parts) {
            PartSync.Start start = part.start();
            switch (start) {
                case SEEDED -> CustomPerm.LOGGER.info("[CustomPerm] Cluster: the store held no {}; this server's were written to it.",
                        part.part());
                case ADOPTED_SAME -> CustomPerm.LOGGER.info("[CustomPerm] Cluster: this server's {} already match the store.",
                        part.part());
                case ADOPTED_REPLACED -> CustomPerm.LOGGER.warn("[CustomPerm] Cluster: this server's {} differed from the store and "
                        + "were replaced by it. The previous files are in the backup folder.", part.part());
            }
        }
    }

    /** After a change on the server thread: null when written, else the refusal, the change undone. */
    String publish() {
        String refusal = null;
        try {
            for (PartSync<?> part : parts) {
                String refused = part.publish();
                if (refusal == null) refusal = refused;
            }
            recovered();
            return refusal;
        } catch (PartSync.Unreachable e) {
            lost(e.getMessage());
            return "the cluster storage is unreachable, so changes are refused until it is back. This server keeps "
                    + "the configuration it last read.";
        }
    }

    void tick() {
        if (++heartbeatTicks >= HEARTBEAT_TICKS) {
            heartbeatTicks = 0;
            worker.execute(this::beat);
        }
        int every = Math.max(1, CustomPerm.configManager.getSettings().cluster.pollSeconds) * 20;
        if (++ticks < every) return;
        ticks = 0;
        if (!polling.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try {
                List<List<ClusterStore.Row>> fetched = new ArrayList<>();
                for (PartSync<?> part : parts) fetched.add(part.fetch());
                server.execute(() -> {
                    try {
                        for (int i = 0; i < parts.size(); i++) parts.get(i).apply(fetched.get(i));
                        recovered();
                    } finally {
                        polling.set(false);
                    }
                });
            } catch (ClusterStore.StoreException e) {
                server.execute(() -> lost(e.getMessage()));
                polling.set(false);
            } catch (RuntimeException e) {
                CustomPerm.LOGGER.error("[CustomPerm] Cluster: reading the store failed", e);
                polling.set(false);
            }
        });
    }

    /** Reads and applies what changed, now, on the calling thread (the server thread). */
    boolean pollNow() throws ClusterStore.StoreException {
        boolean changed = false;
        for (PartSync<?> part : parts) changed |= part.apply(part.fetch());
        recovered();
        return changed;
    }

    /** Stops polling and, at a clean stop, takes this instance off the list of running servers. */
    void stop() {
        worker.shutdownNow();
        try {
            store.leave(name, instance);
        } catch (ClusterStore.StoreException e) {
            CustomPerm.LOGGER.debug("[CustomPerm] Cluster: leaving the store failed: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ parts

    private <T> void add(PartCodec<T> codec, Supplier<T> current, Runnable onChanged) {
        parts.add(new PartSync<>(codec, store, name, new PartSync.Host<T>() {
            @Override
            public T current() {
                return current.get();
            }

            @Override
            public void changed(T config, Set<String> holders) {
                onChanged.run();
                ConfigAdmin.resyncCommands(server);
                // The local files are this server's copy of the store: what it starts from if the store is down then.
                CustomPerm.configManager.save();
            }

            @Override
            public String label(String holder) {
                return ClusterService.label(holder);
            }
        }));
    }

    private void commandTreeChanged() {
        CustomPerm.treeReloader.onConfigReload(CustomPerm.configManager.getSnapshot(), server);
    }

    static String label(String holder) {
        if (holder.startsWith(GradesCodec.GRADE)) return "Grade " + holder.substring(GradesCodec.GRADE.length());
        if (holder.startsWith(GradesCodec.PLAYER)) {
            String id = holder.substring(GradesCodec.PLAYER.length());
            try {
                String known = UsernameCache.getLastKnownUsername(UUID.fromString(id));
                return "Player " + (known == null ? id : known);
            } catch (IllegalArgumentException e) {
                return "Player " + id;
            }
        }
        if (holder.startsWith(CommandsCodec.COMMAND)) return "Command /" + holder.substring(CommandsCodec.COMMAND.length());
        if (holder.startsWith(AliasesCodec.ALIAS)) return "Alias /" + holder.substring(AliasesCodec.ALIAS.length());
        if (holder.startsWith(RateLimitsCodec.RULE)) {
            return "The rate limit on /" + holder.substring(RateLimitsCodec.RULE.length());
        }
        return "The tracks";
    }

    // ------------------------------------------------------------------ health

    private void beat() {
        try {
            List<String> others = store.heartbeat(name, instance, LIVE_SECONDS);
            if (!others.isEmpty()) {
                server.execute(() -> AdminNotifier.raise(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "Another running server "
                        + "now uses this server's name \"" + name + "\" in the cluster. Give each server its own "
                        + "server_id in config/arcadia/lib/server.toml and restart one of them."));
            }
        } catch (ClusterStore.StoreException e) {
            server.execute(() -> lost(e.getMessage()));
        }
    }

    private void lost(String reason) {
        AdminNotifier.raise(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "The cluster storage is unreachable (" + reason
                + "). This server keeps the configuration it last read and refuses changes until it is back.");
    }

    private void recovered() {
        if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) {
            AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "the cluster storage is reachable again.");
        }
    }
}
