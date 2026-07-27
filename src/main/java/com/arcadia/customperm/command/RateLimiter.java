package com.arcadia.customperm.command;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter, keyed by command name (exposed command or
 * alias name) and player UUID. Counters are not persisted — they reset on server
 * restart, which is the intended anti-spam behaviour (see RateLimitsConfig).
 *
 * Only players are tracked: console/command-block invocations have no stable UUID to
 * key on and are left unlimited by callers.
 */
public final class RateLimiter {

    private static final Map<String, Map<UUID, Deque<Long>>> HISTORY = new ConcurrentHashMap<>();

    /** Coarse cadence for the amortised memory sweep (see maybeSweep). */
    static final long SWEEP_INTERVAL_MILLIS = 5L * 60L * 1000L;
    private static volatile long lastSweepMillis = 0L;

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

    public static Result tryAcquire(String commandName, UUID player, int maxExecutions, int windowSeconds) {
        Map<UUID, Deque<Long>> perPlayer = HISTORY.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>());
        Deque<Long> timestamps = perPlayer.computeIfAbsent(player, k -> new ArrayDeque<>());

        long windowMillis = windowSeconds * 1000L;
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        synchronized (timestamps) {
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
            return new Result(true, 0L);
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
     * Single eviction pass. A bucket whose resolver returns {@code <= 0} (rule gone/disabled) is
     * dropped whole; otherwise every player Deque is pruned to the current window and emptied
     * entries are removed, dropping the command bucket once its last player is gone. Package-private
     * so tests drive it directly with a stub resolver and no live config.
     */
    static void sweep(long now, WindowResolver resolver) {
        Iterator<Map.Entry<String, Map<UUID, Deque<Long>>>> commands = HISTORY.entrySet().iterator();
        while (commands.hasNext()) {
            Map.Entry<String, Map<UUID, Deque<Long>>> command = commands.next();
            long windowMillis = resolver.windowMillisFor(command.getKey());
            if (windowMillis <= 0L) {
                commands.remove();
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

    /** Test hook: whether a (command, player) pair currently has a tracked history entry. */
    static boolean isTracked(String commandName, UUID player) {
        Map<UUID, Deque<Long>> perPlayer = HISTORY.get(commandName);
        return perPlayer != null && perPlayer.containsKey(player);
    }

    /** Purges all tracked history — called when the dispatcher is torn down (see clearServerState callers). */
    public static void clearServerState() {
        HISTORY.clear();
        lastSweepMillis = 0L;
    }
}
