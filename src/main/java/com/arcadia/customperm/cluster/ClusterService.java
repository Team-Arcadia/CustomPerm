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
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.UsernameCache;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cluster mode running on this server: the grades kept in step with the store. Reads of the store run on one
 * thread of its own; everything that touches the configuration runs on the server thread, like every other
 * change to it. A change made here is written before the command answers, so a refusal is the answer.
 */
final class ClusterService implements PartSync.Host<GradesConfig> {

    private final String name;
    private final MinecraftServer server;
    private final PartSync<GradesConfig> grades;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CustomPerm-Cluster");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean polling = new AtomicBoolean();
    private int ticks;

    ClusterService(ClusterStore store, String name, MinecraftServer server) {
        this.name = name;
        this.server = server;
        this.grades = new PartSync<>(new GradesCodec(), store, name, this);
    }

    String name() {
        return name;
    }

    /** First contact, on the server thread. A store already filled replaces this server's grades, backed up first. */
    void start() throws ClusterStore.StoreException {
        CustomPerm.configManager.backupNow();
        PartSync.Start start = grades.start();
        switch (start) {
            case SEEDED -> CustomPerm.LOGGER.info("[CustomPerm] Cluster: the store was empty; this server's grades were written to it.");
            case ADOPTED_SAME -> CustomPerm.LOGGER.info("[CustomPerm] Cluster: this server's grades already match the store.");
            case ADOPTED_REPLACED -> CustomPerm.LOGGER.warn("[CustomPerm] Cluster: this server's grades differed from the store and "
                    + "were replaced by it. The previous files are in the backup folder.");
        }
    }

    /** After a change on the server thread: null when written, else the refusal, the change undone. */
    String publish() {
        try {
            String refusal = grades.publish();
            recovered();
            return refusal;
        } catch (PartSync.Unreachable e) {
            lost(e.getMessage());
            return "the cluster storage is unreachable, so changes are refused until it is back. This server keeps "
                    + "the rights it last read.";
        }
    }

    void tick() {
        int every = Math.max(1, CustomPerm.configManager.getSettings().cluster.pollSeconds) * 20;
        if (++ticks < every) return;
        ticks = 0;
        if (!polling.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try {
                List<ClusterStore.Row> rows = grades.fetch();
                server.execute(() -> {
                    try {
                        grades.apply(rows);
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
        boolean changed = grades.apply(grades.fetch());
        recovered();
        return changed;
    }

    void stop() {
        worker.shutdownNow();
    }

    // ------------------------------------------------------------------ PartSync.Host

    @Override
    public GradesConfig current() {
        return CustomPerm.configManager.getGrades();
    }

    @Override
    public void changed(GradesConfig config, Set<String> holders) {
        PermissionService.get().onConfigReload(CustomPerm.configManager.getSnapshot());
        ConfigAdmin.resyncCommands(server);
        // The local files are this server's copy of the store: what it starts from if the store is down then.
        CustomPerm.configManager.save();
    }

    @Override
    public String label(String holder) {
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
        return "The tracks";
    }

    private void lost(String reason) {
        AdminNotifier.raise(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "The cluster storage is unreachable (" + reason
                + "). This server keeps the rights it last read and refuses changes until it is back.");
    }

    private void recovered() {
        if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) {
            AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "the cluster storage is reachable again.");
        }
    }
}
