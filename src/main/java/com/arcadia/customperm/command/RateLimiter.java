package com.arcadia.customperm.command;

import java.util.ArrayDeque;
import java.util.Deque;
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

    private RateLimiter() {}

    public record Result(boolean allowed, long retryAfterSeconds) {}

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

    /** Purges all tracked history — called when the dispatcher is torn down (see clearServerState callers). */
    public static void clearServerState() {
        HISTORY.clear();
    }
}
