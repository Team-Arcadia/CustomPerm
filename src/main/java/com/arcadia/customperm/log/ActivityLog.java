/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.log;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.AdminResult;
import com.arcadia.customperm.config.SettingsConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Activity log: admin changes, always, and player commands, when {@code playerCommandLog} is on.
 *
 * <p>Entries are kept in memory for the interface (the latest {@link #MEMORY_MAX} per tab, reloaded from
 * disk at start) and appended to daily JSON Lines files in {@code <world>/customperm/logs/}, like the
 * rate-limit history the log is server state that lives with the world. Files older than
 * {@code logRetentionDays} are deleted at start and when the day changes.</p>
 *
 * <p>Disk writes run on one background thread, in order, so a busy server does not wait on the disk for
 * every command a player types. Recording may be called from any thread: LuckPerms publishes its log
 * entries off the server thread.</p>
 */
public final class ActivityLog {

    public static final int MEMORY_MAX = 1000;
    static final int TEXT_MAX = 512;

    private static final Map<LogKind, Deque<LogEntry>> MEMORY = new EnumMap<>(LogKind.class);
    private static volatile Path directory;
    private static volatile ExecutorService writer;
    /** Last day a purge ran, touched on the writer thread only. */
    private static LocalDate purgedOn;

    static {
        for (LogKind kind : LogKind.values()) MEMORY.put(kind, new ArrayDeque<>());
    }

    private ActivityLog() {
    }

    // ------------------------------------------------------------------ lifecycle

    public static void onServerStarted(ServerStartedEvent event) {
        start(event.getServer().getWorldPath(LevelResource.ROOT).resolve("customperm").resolve("logs"));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    static synchronized void start(Path dir) {
        stop();
        directory = dir;
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CustomPerm activity log");
            thread.setDaemon(true);
            return thread;
        });
        purgedOn = null;
        writer.execute(ActivityLog::purgeIfNewDay);
        for (LogKind kind : LogKind.values()) loadRecent(kind);
    }

    /** Waits for pending writes, then forgets the server: nothing is recorded until the next start. */
    static synchronized void stop() {
        ExecutorService current = writer;
        writer = null;
        directory = null;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(5, TimeUnit.SECONDS)) {
                    CustomPerm.LOGGER.warn("[CustomPerm] Activity log writes still pending at shutdown were dropped.");
                    current.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (MEMORY) {
            MEMORY.values().forEach(Deque::clear);
        }
    }

    /** Where the files of the running server are written, or {@code null} with no server. */
    public static Path directory() {
        return directory;
    }

    /** Blocks until every write queued so far is on disk. For tests and diagnostics. */
    public static void flush() {
        ExecutorService current = writer;
        if (current == null) return;
        try {
            current.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Waiting for the activity log writer failed.", e);
        }
    }

    // ------------------------------------------------------------------ recording

    /** An admin change made through a CustomPerm command or the interface, with the result the admin saw. */
    public static void admin(CommandSourceStack source, String origin, String action, AdminResult result) {
        String actorId = source.getEntity() instanceof ServerPlayer player ? player.getUUID().toString() : "";
        record(LogKind.ADMIN, new LogEntry(System.currentTimeMillis(), source.getTextName(), actorId, origin,
                action, result.success(), result.summary()));
    }

    public static void admin(String actor, String actorId, String origin, String action, boolean success, String result) {
        record(LogKind.ADMIN, new LogEntry(System.currentTimeMillis(), actor, actorId == null ? "" : actorId, origin,
                action, success, result == null ? "" : result));
    }

    /** A change LuckPerms published to its own action log: /lp commands and the web editor. */
    public static void luckPerms(long time, String actor, String actorId, String description, String target) {
        record(LogKind.ADMIN, new LogEntry(time, actor, actorId, LogEntry.SOURCE_LUCKPERMS, description, true, target));
    }

    /** Commands typed by players; command blocks, functions and the console are not recorded. */
    public static void onCommand(CommandEvent event) {
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        if (!settings.playerCommandLog) return;
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        String command = LogFiles.maskCommand(event.getParseResults().getReader().getString(),
                settings.maskPlayerCommandArguments, settings.maskedCommands, TEXT_MAX);
        record(LogKind.PLAYERS, new LogEntry(System.currentTimeMillis(), player.getGameProfile().getName(),
                player.getUUID().toString(), LogEntry.SOURCE_PLAYER, command, true, ""));
    }

    private static void record(LogKind kind, LogEntry raw) {
        Path dir = directory;
        ExecutorService current = writer;
        if (dir == null || current == null) return;
        LogEntry entry = new LogEntry(raw.time(), cap(raw.actor()), raw.actorId(), raw.source(), cap(raw.action()),
                raw.success(), cap(raw.result()));
        synchronized (MEMORY) {
            Deque<LogEntry> entries = MEMORY.get(kind);
            entries.addLast(entry);
            while (entries.size() > MEMORY_MAX) entries.removeFirst();
        }
        try {
            current.execute(() -> append(dir, kind, entry));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The server is stopping: the entry stays in memory only.
        }
    }

    /** The latest entries of a tab, newest first. */
    public static List<LogEntry> recent(LogKind kind, int limit) {
        List<LogEntry> out = new ArrayList<>(Math.min(limit, MEMORY_MAX));
        synchronized (MEMORY) {
            var it = MEMORY.get(kind).descendingIterator();
            while (it.hasNext() && out.size() < limit) out.add(it.next());
        }
        return out;
    }

    // ------------------------------------------------------------------ disk

    private static void append(Path dir, LogKind kind, LogEntry entry) {
        purgeIfNewDay();
        LocalDate day = Instant.ofEpochMilli(entry.time()).atZone(ZoneId.systemDefault()).toLocalDate();
        try {
            Files.createDirectories(dir);
            try (BufferedWriter out = Files.newBufferedWriter(dir.resolve(LogFiles.fileName(kind, day)),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(toJson(entry));
                out.newLine();
            }
        } catch (IOException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not write the activity log in {}.", dir, e);
        }
    }

    /** Writer thread only. */
    private static void purgeIfNewDay() {
        Path dir = directory;
        if (dir == null) return;
        LocalDate today = LocalDate.now();
        if (today.equals(purgedOn)) return;
        purgedOn = today;
        purge(dir, today);
    }

    /** Deletes files past the retention period now. */
    public static void purge(Path dir, LocalDate today) {
        try {
            int deleted = LogFiles.purge(dir, today, CustomPerm.configManager.getSettings().logRetentionDays).size();
            if (deleted > 0) CustomPerm.LOGGER.info("[CustomPerm] Deleted {} activity log file(s) past retention.", deleted);
        } catch (IOException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not apply the activity log retention in {}.", dir, e);
        }
    }

    /**
     * Replaces the entries in memory with the latest ones on disk, after pending writes: what a restart
     * would show. Used by tests and after editing the files by hand.
     */
    public static void reloadFromDisk() {
        flush();
        for (LogKind kind : LogKind.values()) loadRecent(kind);
    }

    /** Fills memory with the latest entries on disk, newest files first, skipping unreadable lines. */
    static void loadRecent(LogKind kind) {
        Path dir = directory;
        if (dir == null) return;
        Deque<LogEntry> loaded = new ArrayDeque<>();
        try {
            for (Path file : LogFiles.newestFirst(dir, kind)) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = lines.size() - 1; i >= 0 && loaded.size() < MEMORY_MAX; i--) {
                    LogEntry entry = fromJson(lines.get(i));
                    if (entry != null) loaded.addFirst(entry);
                }
                if (loaded.size() >= MEMORY_MAX) break;
            }
        } catch (IOException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not read the activity log in {}.", dir, e);
        }
        synchronized (MEMORY) {
            Deque<LogEntry> entries = MEMORY.get(kind);
            entries.clear();
            entries.addAll(loaded);
        }
    }

    static String toJson(LogEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("time", entry.time());
        json.addProperty("actor", entry.actor());
        json.addProperty("actorId", entry.actorId());
        json.addProperty("source", entry.source());
        json.addProperty("action", entry.action());
        json.addProperty("success", entry.success());
        json.addProperty("result", entry.result());
        return json.toString();
    }

    static LogEntry fromJson(String line) {
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            return new LogEntry(json.get("time").getAsLong(), text(json, "actor"), text(json, "actorId"),
                    text(json, "source"), text(json, "action"), json.has("success") && json.get("success").getAsBoolean(),
                    text(json, "result"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? cap(json.get(key).getAsString()) : "";
    }

    private static String cap(String text) {
        if (text == null) return "";
        return text.length() <= TEXT_MAX ? text : text.substring(0, TEXT_MAX) + "...";
    }
}
