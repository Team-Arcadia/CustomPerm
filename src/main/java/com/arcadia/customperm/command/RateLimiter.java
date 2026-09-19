/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter, keyed by command name (exposed command or alias name) and player UUID.
 * Timestamps are Unix epoch milliseconds.
 *
 * <p>History of admin-defined limits is persisted with the world (see {@link RateLimitPersistence}):
 * {@link #snapshot} and {@link #restore} carry it across restarts, and nothing but a server stop clears
 * it, so a vanilla {@code /reload} no longer hands every player a fresh quota. Internal budgets (the
 * LuckPerms editor's anti-spam caps, registered with {@link #registerInternalBudget}) stay in memory only.</p>
 *
 * <p>Only players are tracked: console/command-block invocations have no stable UUID to key on and are
 * left unlimited by callers.</p>
 */
public final class RateLimiter {

    private static final Map<String, Map<UUID, Deque<Long>>> HISTORY = new ConcurrentHashMap<>();

    /** Window, in ms, of every internal budget; these keys are never persisted nor dropped by the sweep. */
    private static final Map<String, Long> INTERNAL_WINDOWS = new ConcurrentHashMap<>();

    /** Coarse cadence for the amortised memory sweep (see maybeSweep). */
    static final long SWEEP_INTERVAL_MILLIS = 5L * 60L * 1000L;
    private static volatile long lastSweepMillis = 0L;

    /** Set when persisted history changed since the last {@link #consumeDirty()}. */
    private static volatile boolean dirty = false;

    private RateLimiter() {}

    public record Result(boolean allowed, long retryAfterSeconds) {}

    /**
     * Resolves the currently-active sliding window for a command, in milliseconds.
     * Returns {@code <= 0} when no enabled rule exists for the command, signalling that
     * the whole history bucket can be dropped. Supplied by the caller so RateLimiter stays
     * free of any config/Minecraft dependency (keeps it unit-testable in isolation).
     */
    @FunctionalInterface
    interface WindowResolver {
        long windowMillisFor(String commandName);
    }

    /**
     * Declares a budget that is not an admin rule: its history is kept out of the persisted state and
     * the sweep prunes it with this window instead of dropping it for lack of a configured rule.
     */
    public static void registerInternalBudget(String key, int windowSeconds) {
        INTERNAL_WINDOWS.put(key, windowSeconds * 1000L);
    }

    public static Result tryAcquire(String commandName, UUID player, int maxExecutions, int windowSeconds) {
        return tryAcquire(commandName, player, maxExecutions, windowSeconds, System.currentTimeMillis());
    }

    static Result tryAcquire(String commandName, UUID player, int maxExecutions, int windowSeconds, long now) {
        Map<UUID, Deque<Long>> perPlayer = HISTORY.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>());
        Deque<Long> timestamps = perPlayer.computeIfAbsent(player, k -> new ArrayDeque<>());

        long windowMillis = windowSeconds * 1000L;
        long cutoff = now - windowMillis;

        synchronized (timestamps) {
            clampToNow(timestamps, now);
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxExecutions) {
                long oldest = timestamps.peekFirst();
                long retryAfterMillis = (oldest + windowMillis) - now;
                long retryAfterSeconds = Math.max(1L, (retryAfterMillis + 999) / 1000);
                return new Result(false, retryAfterSeconds);
            }
            timestamps.addLast(now);
        }
        if (!INTERNAL_WINDOWS.containsKey(commandName)) {
            dirty = true;
            com.arcadia.customperm.cluster.Cluster.use(commandName, player, now);
        }
        return new Result(true, 0L);
    }

    /**
     * A use another server of the cluster counted, so a player limited to 3 uses an hour gets 3 across the network,
     * not 3 per server. Put in time order; counted by the next {@link #tryAcquire} like a use made here.
     */
    public static void recordForeign(String commandName, UUID player, long time) {
        if (INTERNAL_WINDOWS.containsKey(commandName)) return;
        Deque<Long> timestamps = HISTORY.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            if (timestamps.isEmpty() || timestamps.peekLast() <= time) {
                timestamps.addLast(time);
            } else {
                List<Long> sorted = new ArrayList<>(timestamps);
                sorted.add(time);
                sorted.sort(null);
                timestamps.clear();
                timestamps.addAll(sorted);
            }
        }
        dirty = true;
    }

    /**
     * A timestamp later than now means the system clock went backwards (manual change, NTP correction).
     * Left as is, it would stay "inside the window" until the clock caught up, possibly hours later, and
     * keep a player locked out. Such timestamps are brought back to now: the usage still counts, for one
     * window from now.
     */
    private static void clampToNow(Deque<Long> timestamps, long now) {
        if (timestamps.isEmpty() || timestamps.peekLast() <= now) return;
        int size = timestamps.size();
        for (int i = 0; i < size; i++) {
            timestamps.addLast(Math.min(timestamps.pollFirst(), now));
        }
    }

    /**
     * Amortised memory reclaim: at most once per {@link #SWEEP_INTERVAL_MILLIS}, evicts
     * per-player entries whose window has fully elapsed and command buckets whose rule was
     * removed or disabled. Without it, HISTORY keeps a permanent {@code UUID -> Deque} entry
     * for every player who ever ran a limited command, so memory grows with the count of unique
     * players over a long-lived server. tryAcquire prunes only the caller's own Deque, never idle
     * players' — hence this global pass. Command dispatch is single-threaded (server tick), so
     * callers piggyback this on execution rather than running a separate scheduled task.
     */
    static void maybeSweep(long now, WindowResolver resolver) {
        if (now - lastSweepMillis < SWEEP_INTERVAL_MILLIS) return;
        lastSweepMillis = now;
        sweep(now, resolver);
    }

    /**
     * Single eviction pass. A bucket whose window resolves to {@code <= 0} (rule gone/disabled) is
     * dropped whole; otherwise every player Deque is pruned to the current window and emptied
     * entries are removed, dropping the command bucket once its last player is gone. Internal budgets
     * use their registered window. Package-private so tests drive it directly with a stub resolver.
     */
    static void sweep(long now, WindowResolver resolver) {
        Iterator<Map.Entry<String, Map<UUID, Deque<Long>>>> commands = HISTORY.entrySet().iterator();
        while (commands.hasNext()) {
            Map.Entry<String, Map<UUID, Deque<Long>>> command = commands.next();
            long windowMillis = windowFor(command.getKey(), resolver);
            if (windowMillis <= 0L) {
                commands.remove();
                dirty = true;
                continue;
            }
            long cutoff = now - windowMillis;
            Map<UUID, Deque<Long>> perPlayer = command.getValue();
            perPlayer.entrySet().removeIf(entry -> {
                Deque<Long> timestamps = entry.getValue();
                synchronized (timestamps) {
                    while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                        timestamps.pollFirst();
                    }
                    return timestamps.isEmpty();
                }
            });
            if (perPlayer.isEmpty()) {
                commands.remove();
            }
        }
    }

    private static long windowFor(String key, WindowResolver resolver) {
        Long internal = INTERNAL_WINDOWS.get(key);
        return internal != null ? internal : resolver.windowMillisFor(key);
    }

    /**
     * Persistable copy of the history: admin rules only, each player's timestamps still inside the
     * rule's current window, empty entries and rules without an active window left out.
     */
    static Map<String, Map<UUID, List<Long>>> snapshot(long now, WindowResolver resolver) {
        Map<String, Map<UUID, List<Long>>> copy = new LinkedHashMap<>();
        HISTORY.forEach((command, perPlayer) -> {
            if (INTERNAL_WINDOWS.containsKey(command)) return;
            long windowMillis = resolver.windowMillisFor(command);
            if (windowMillis <= 0L) return;
            long cutoff = now - windowMillis;
            Map<UUID, List<Long>> players = new LinkedHashMap<>();
            perPlayer.forEach((uuid, timestamps) -> {
                List<Long> kept = new ArrayList<>();
                synchronized (timestamps) {
                    for (long ts : timestamps) {
                        if (ts > cutoff) kept.add(Math.min(ts, now));
                    }
                }
                if (!kept.isEmpty()) players.put(uuid, kept);
            });
            if (!players.isEmpty()) copy.put(command, players);
        });
        return copy;
    }

    /**
     * Loads persisted history into memory, replacing whatever admin-rule history is there. Entries are
     * pruned with the rules as configured now (a window shortened while the server was down applies at
     * once), timestamps from the future are clamped, and rules that no longer exist are ignored.
     */
    static void restore(Map<String, Map<UUID, List<Long>>> persisted, long now, WindowResolver resolver) {
        HISTORY.keySet().removeIf(command -> !INTERNAL_WINDOWS.containsKey(command));
        persisted.forEach((command, players) -> {
            if (INTERNAL_WINDOWS.containsKey(command)) return;
            long windowMillis = resolver.windowMillisFor(command);
            if (windowMillis <= 0L) return;
            long cutoff = now - windowMillis;
            Map<UUID, Deque<Long>> perPlayer = new ConcurrentHashMap<>();
            players.forEach((uuid, timestamps) -> {
                Deque<Long> kept = new ArrayDeque<>();
                timestamps.stream().map(ts -> Math.min(ts, now)).filter(ts -> ts > cutoff).sorted().forEach(kept::addLast);
                if (!kept.isEmpty()) perPlayer.put(uuid, kept);
            });
            if (!perPlayer.isEmpty()) HISTORY.put(command, perPlayer);
        });
        dirty = false;
    }

    /** Returns whether persisted history changed since the previous call, and resets the flag. */
    static boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    /** Test hook: whether a (command, player) pair currently has a tracked history entry. */
    static boolean isTracked(String commandName, UUID player) {
        Map<UUID, Deque<Long>> perPlayer = HISTORY.get(commandName);
        return perPlayer != null && perPlayer.containsKey(player);
    }

    /** Purges all tracked history. Called on server stop, after the history was persisted. */
    public static void clearServerState() {
        HISTORY.clear();
        lastSweepMillis = 0L;
        dirty = false;
    }
}
