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
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.network.gui.LogsData;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.UsernameCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    private static final String UNREACHABLE = "the cluster storage is unreachable, so changes are refused until it is "
            + "back. This server keeps the configuration it last read.";

    /** How long a server may stay silent before it no longer counts as running. */
    static final int LIVE_SECONDS = 45;
    /** Seconds between two heartbeats. */
    static final int HEARTBEAT_SECONDS = 10;
    private static final int HEARTBEAT_TICKS = HEARTBEAT_SECONDS * 20;
    private static final int LOG_READ_MAX = 2000;
    /** Entries kept for a retry when the store refused them, at most. */
    private static final int LOG_UNSENT_MAX = 10_000;
    private static final long LOG_PURGE_EVERY_MILLIS = 3600_000;

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
    /** Server thread: an outage is under way, its cause already logged. */
    private boolean outage;
    private final boolean shareLog;
    private final ConcurrentLinkedQueue<ClusterStore.LogLine> pendingLog = new ConcurrentLinkedQueue<>();
    /** Worker thread only: entries not yet accepted by the store, the newest log number read, what was read lately. */
    private final List<ClusterStore.LogLine> unsentLog = new ArrayList<>();
    private final GapReader logReader = new GapReader();
    private long lastPurge;
    private final ConcurrentLinkedQueue<ClusterStore.Use> pendingUses = new ConcurrentLinkedQueue<>();
    /** Worker thread only, like the log fields above. */
    private final List<ClusterStore.Use> unsentUses = new ArrayList<>();
    private final GapReader useReader = new GapReader();
    /** Worker thread: the number every shared part has been read up to, one query for them all. */
    private volatile long partsCursor;
    private long lastUsePurge;
    /** Other servers and seconds since each was last heard, from the latest heartbeat; for the dashboard. */
    private volatile Map<String, Long> peers = Map.of();

    ClusterService(ClusterStore store, String name, String instance, MinecraftServer server, SettingsConfig.Share share) {
        this.store = store;
        this.name = name;
        this.instance = instance;
        this.server = server;
        this.shareLog = share.log;
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

    Map<String, Long> peers() {
        return peers;
    }

    /** Names of the parts shared, for the log and the dashboard. */
    List<String> sharedParts() {
        List<String> names = new ArrayList<>(parts.stream().<String>map(PartSync::part).toList());
        if (shareLog) names.add("activity log");
        names.add("rate-limit counters by rule scope");
        return names;
    }

    /** A use of a rate-limited command counted here, to share. Any thread. */
    void use(String command, UUID player, long time) {
        var rule = CustomPerm.configManager.getRateLimits().get(command);
        if (rule != null && rule.shared(name)) pendingUses.add(new ClusterStore.Use(command, player.toString(), time));
    }

    /** An entry recorded here, to share. Any thread. */
    void log(LogKind kind, LogEntry entry) {
        if (shareLog) pendingLog.add(new ClusterStore.LogLine(kind.name(), entry));
    }

    /** First contact, on the server thread. A part the store already holds replaces this server's, backed up first. */
    void start() throws ClusterStore.StoreException {
        CustomPerm.configManager.backupNow();
        // Read before the parts start: every row numbered up to it is committed and seen by their start, and a row
        // written meanwhile carries a higher number, read at the next poll. The highest number the starts read would
        // skip a row written to one part while another was starting.
        long cursor = store.currentSeq();
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
        partsCursor = cursor;
        // Uses the other servers counted still apply here: the table only holds those within the longest window.
        List<ClusterStore.UseRow> uses = store.usesAfter(0, List.of(), Integer.MAX_VALUE);
        long lastUse = 0;
        for (ClusterStore.UseRow row : uses) lastUse = Math.max(lastUse, row.id());
        useReader.startAfter(lastUse);
        if (CustomPerm.configManager.getRateLimits().anyShared(name)) {
            showForeignUses(uses.stream().filter(r -> !r.server().equals(name)).toList());
        }
        if (shareLog) {
            // What the other servers recorded lately, shown beside this server's own from its files.
            long lastLog = 0;
            for (LogKind kind : LogKind.values()) {
                List<ClusterStore.LogRow> recent = new ArrayList<>(store.recentLog(kind.name(), LogsData.ENTRIES_MAX));
                java.util.Collections.reverse(recent);
                for (ClusterStore.LogRow row : recent) {
                    lastLog = Math.max(lastLog, row.id());
                    if (!row.server().equals(name)) ActivityLog.addForeign(kind, foreign(row));
                }
            }
            logReader.startAfter(lastLog);
        }
    }

    /** After a change on the server thread: null when written, else the refusal, the change undone. */
    String publish() {
        if (outage) {
            // Known to be unreachable: refused at once rather than after a connection timeout on the server thread.
            // The background poll finds the store again and ends the outage.
            boolean undone = false;
            for (PartSync<?> part : parts) undone |= part.revert();
            return undone ? UNREACHABLE : null;
        }
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
            return UNREACHABLE;
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
                List<ClusterStore.LogRow> foreignLog = syncLog();
                List<ClusterStore.UseRow> foreignUses = syncUses();
                Map<String, List<ClusterStore.Row>> fetched = fetchParts();
                server.execute(() -> {
                    try {
                        applyParts(fetched);
                        showForeign(foreignLog);
                        showForeignUses(foreignUses);
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
        boolean changed = applyParts(fetchParts());
        showForeign(syncLog());
        showForeignUses(syncUses());
        recovered();
        return changed;
    }

    /** Stops polling and, at a clean stop, takes this instance off the list of running servers. */
    void stop() {
        worker.shutdownNow();
        try {
            if (shareLog) syncLog();
            syncUses();
        } catch (ClusterStore.StoreException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Cluster: activity log entries not yet shared were dropped at stop: {}",
                    e.getMessage());
        }
        try {
            store.leave(name, instance);
        } catch (ClusterStore.StoreException e) {
            CustomPerm.LOGGER.debug("[CustomPerm] Cluster: leaving the store failed: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ parts

    /** Every shared part's new rows in one query, from the shared cursor. Worker thread, or the server thread. */
    private synchronized Map<String, List<ClusterStore.Row>> fetchParts() throws ClusterStore.StoreException {
        if (parts.isEmpty()) return Map.of();
        Map<String, List<ClusterStore.Row>> rows = store.changesSince(sharedPartNames(), partsCursor);
        for (List<ClusterStore.Row> some : rows.values()) {
            for (ClusterStore.Row row : some) partsCursor = Math.max(partsCursor, row.seq());
        }
        return rows;
    }

    /** Server thread. True when something changed here. */
    private boolean applyParts(Map<String, List<ClusterStore.Row>> rows) {
        boolean changed = false;
        for (PartSync<?> part : parts) {
            List<ClusterStore.Row> some = rows.get(part.part());
            if (some != null) changed |= part.apply(some);
        }
        return changed;
    }

    private List<String> sharedPartNames() {
        return parts.stream().<String>map(PartSync::part).toList();
    }

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

    // ------------------------------------------------------------------ activity log

    /**
     * Sends what this server recorded and reads what the others did. Worker thread (or the server thread when the
     * worker is not running). Entries the store refused are kept for the next round, up to a limit.
     */
    private synchronized List<ClusterStore.LogRow> syncLog() throws ClusterStore.StoreException {
        if (!shareLog) return List.of();
        ClusterStore.LogLine line;
        while ((line = pendingLog.poll()) != null) unsentLog.add(line);
        if (unsentLog.size() > LOG_UNSENT_MAX) unsentLog.subList(0, unsentLog.size() - LOG_UNSENT_MAX).clear();
        if (!unsentLog.isEmpty()) {
            store.appendLog(name, List.copyOf(unsentLog));
            unsentLog.clear();
        }
        long now = System.currentTimeMillis();
        List<ClusterStore.LogRow> foreign = new ArrayList<>();
        for (ClusterStore.LogRow row : store.logAfter(logReader.last(), List.copyOf(logReader.missing()), LOG_READ_MAX)) {
            if (logReader.accept(row.id(), now) && !row.server().equals(name)) foreign.add(row);
        }
        logReader.prune(now);
        int days = CustomPerm.configManager.getSettings().logRetentionDays;
        if (days > 0 && now - lastPurge > LOG_PURGE_EVERY_MILLIS) {
            lastPurge = now;
            store.purgeLog(now - days * 86_400_000L);
        }
        return foreign;
    }

    /** Server thread. */
    private static void showForeign(List<ClusterStore.LogRow> rows) {
        for (ClusterStore.LogRow row : rows) {
            LogKind kind;
            try {
                kind = LogKind.valueOf(row.kind());
            } catch (IllegalArgumentException e) {
                continue;
            }
            ActivityLog.addForeign(kind, foreign(row));
        }
    }

    private static LogEntry foreign(ClusterStore.LogRow row) {
        LogEntry e = row.entry();
        return new LogEntry(e.time(), e.actor(), e.actorId(), e.source(), e.action(), e.success(), e.result(), row.server());
    }

    // ------------------------------------------------------------------ rate-limit counters

    /** Sends the uses counted here and reads the others'. Same threading as the log. */
    private synchronized List<ClusterStore.UseRow> syncUses() throws ClusterStore.StoreException {
        ClusterStore.Use use;
        while ((use = pendingUses.poll()) != null) unsentUses.add(use);
        // No rule shared from here, nothing waiting: no query at all, the usual case.
        if (unsentUses.isEmpty() && !CustomPerm.configManager.getRateLimits().anyShared(name)) return List.of();
        if (unsentUses.size() > LOG_UNSENT_MAX) unsentUses.subList(0, unsentUses.size() - LOG_UNSENT_MAX).clear();
        if (!unsentUses.isEmpty()) {
            store.appendUses(name, List.copyOf(unsentUses));
            unsentUses.clear();
        }
        long now = System.currentTimeMillis();
        List<ClusterStore.UseRow> foreign = new ArrayList<>();
        for (ClusterStore.UseRow row : store.usesAfter(useReader.last(), List.copyOf(useReader.missing()), LOG_READ_MAX * 5)) {
            if (useReader.accept(row.id(), now) && !row.server().equals(name)) foreign.add(row);
        }
        useReader.prune(now);
        if (now - lastUsePurge > LOG_PURGE_EVERY_MILLIS) {
            lastUsePurge = now;
            store.purgeUses(now - longestWindowMillis());
        }
        return foreign;
    }

    /** Counts here the uses another server counted, for the rules whose scope joins the two servers. */
    private void showForeignUses(List<ClusterStore.UseRow> rows) {
        var limits = CustomPerm.configManager.getRateLimits();
        for (ClusterStore.UseRow row : rows) {
            var rule = limits.get(row.use().command());
            if (rule == null || !rule.sharedWith(name, row.server())) continue;
            try {
                com.arcadia.customperm.command.RateLimiter.recordForeign(row.use().command(),
                        UUID.fromString(row.use().player()), row.use().time());
            } catch (IllegalArgumentException e) {
                // Not a UUID: written by something else than CustomPerm, nothing to count.
            }
        }
    }

    /** The longest window of any rule, at least an hour, which is how long a counted use can matter. */
    private static long longestWindowMillis() {
        long longest = 3600_000L;
        for (var entry : CustomPerm.configManager.getRateLimits().rules.entrySet()) {
            longest = Math.max(longest,
                    com.arcadia.customperm.command.RateLimits.longestWindowSeconds(entry.getKey(), entry.getValue()) * 1000L);
        }
        return longest;
    }

    // ------------------------------------------------------------------ health

    private void beat() {
        try {
            Map<String, Long> others = store.heartbeat(name, instance, LIVE_SECONDS);
            Map<String, Long> servers = new HashMap<>(store.servers());
            servers.remove(name);
            servers.values().removeIf(age -> age > LIVE_SECONDS);
            peers = Map.copyOf(servers);
            server.execute(() -> {
                if (!others.isEmpty()) {
                    AdminNotifier.raise(AdminAlerts.Key.CLUSTER_NAME_TAKEN, "Another running server now uses this "
                            + "server's name \"" + name + "\" in the cluster. Give each server its own name ("
                            + ClusterGate.NAME_SETTING + ") and restart one of them.");
                } else if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_NAME_TAKEN)) {
                    AdminNotifier.clear(AdminAlerts.Key.CLUSTER_NAME_TAKEN, "no other server uses this name any more.");
                }
            });
        } catch (ClusterStore.StoreException e) {
            server.execute(() -> lost(e.getMessage()));
        }
    }

    /**
     * The alert's text stays the same for the whole outage: an alert is sent again whenever its text changes, and
     * the failing operation (heartbeat, log, write) changes every few seconds. The cause goes to the log, once.
     * Server thread.
     */
    private void lost(String reason) {
        if (!outage) {
            outage = true;
            CustomPerm.LOGGER.warn("[CustomPerm] Cluster: the store is unreachable: {}", reason);
        }
        AdminNotifier.raise(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "The cluster storage is unreachable. This server keeps "
                + "the configuration it last read and refuses changes until it is back; the server log has the cause.");
    }

    /** Ends an outage; anything else the alert may say is not this method's to clear. Server thread. */
    private void recovered() {
        if (!outage) return;
        outage = false;
        if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) {
            AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "the cluster storage is reachable again.");
        }
    }
}
