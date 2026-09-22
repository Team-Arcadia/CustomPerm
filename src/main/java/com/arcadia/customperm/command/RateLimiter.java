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

    /**
     * The windows uses were counted with, per command: window in ms to the time of its latest use. A grade or a
     * player can have a window of their own, longer than the rule's, so history is kept as long as the longest
     * window still in use asks, not only the rule's. An entry lapses once its window has passed since its last use.
     */
    private static final Map<String, Map<Long, Long>> WINDOWS = new ConcurrentHashMap<>();

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
            WINDOWS.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>()).put(windowMillis, now);
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
            long windowMillis = windowFor(command.getKey(), resolver, now);
            if (windowMillis <= 0L) {
                commands.remove();
                WINDOWS.remove(command.getKey());
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

    private static long windowFor(String key, WindowResolver resolver, long now) {
        Long internal = INTERNAL_WINDOWS.get(key);
        return internal != null ? internal : kept(key, resolver.windowMillisFor(key), now);
    }

    /**
     * How long history of {@code command} is kept: {@code configured}, or longer when a window still in use asks it.
     * {@code <= 0} stays as it is, the rule being gone.
     */
    private static long kept(String command, long configured, long now) {
        if (configured <= 0L) return configured;
        Map<Long, Long> used = WINDOWS.get(command);
        if (used == null) return configured;
        long longest = configured;
        for (Map.Entry<Long, Long> entry : used.entrySet()) {
            if (entry.getValue() + entry.getKey() > now) longest = Math.max(longest, entry.getKey());
        }
        return longest;
    }

    /** The windows still in use, for persistence: lapsed ones and those of rules now gone left out. */
    static Map<String, Map<Long, Long>> windowsSnapshot(long now, WindowResolver resolver) {
        Map<String, Map<Long, Long>> copy = new LinkedHashMap<>();
        WINDOWS.forEach((command, used) -> {
            if (resolver.windowMillisFor(command) <= 0L) return;
            Map<Long, Long> live = new LinkedHashMap<>();
            used.forEach((window, last) -> {
                if (last + window > now) live.put(window, Math.min(last, now));
            });
            if (!live.isEmpty()) copy.put(command, live);
        });
        return copy;
    }

    /**
     * Persistable copy of the history: admin rules only, each player's timestamps still inside the
     * rule's current window, empty entries and rules without an active window left out.
     */
    static Map<String, Map<UUID, List<Long>>> snapshot(long now, WindowResolver resolver) {
        Map<String, Map<UUID, List<Long>>> copy = new LinkedHashMap<>();
        HISTORY.forEach((command, perPlayer) -> {
            if (INTERNAL_WINDOWS.containsKey(command)) return;
            long windowMillis = kept(command, resolver.windowMillisFor(command), now);
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
        restore(persisted, Map.of(), now, resolver);
    }

    /** {@link #restore(Map, long, WindowResolver)}, with the windows in use when the history was saved. */
    static void restore(Map<String, Map<UUID, List<Long>>> persisted, Map<String, Map<Long, Long>> windows,
                        long now, WindowResolver resolver) {
        HISTORY.keySet().removeIf(command -> !INTERNAL_WINDOWS.containsKey(command));
        WINDOWS.clear();
        windows.forEach((command, used) -> {
            Map<Long, Long> live = new ConcurrentHashMap<>();
            used.forEach((window, last) -> {
                if (window > 0L && Math.min(last, now) + window > now) live.put(window, Math.min(last, now));
            });
            if (!live.isEmpty()) WINDOWS.put(command, live);
        });
        persisted.forEach((command, players) -> {
            if (INTERNAL_WINDOWS.containsKey(command)) return;
            long windowMillis = kept(command, resolver.windowMillisFor(command), now);
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
        WINDOWS.clear();
        lastSweepMillis = 0L;
        dirty = false;
    }
}
