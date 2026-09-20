/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure Java tests (no Minecraft import) of the in-memory sliding-window counter.
 */
class RateLimiterTest {

    @AfterEach
    void clearHistory() {
        RateLimiter.clearServerState();
    }

    @Test
    void shouldAllowExecutions_untilMaxIsReached() {
        UUID player = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            RateLimiter.Result result = RateLimiter.tryAcquire("observable", player, 5, 3600);
            assertTrue(result.allowed(), "use #" + i + " must be allowed");
        }

        RateLimiter.Result blocked = RateLimiter.tryAcquire("observable", player, 5, 3600);
        assertFalse(blocked.allowed(), "the 6th use must be refused (max=5)");
        assertTrue(blocked.retryAfterSeconds() > 0);
    }

    @Test
    void shouldTrackEachPlayerIndependently() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertTrue(RateLimiter.tryAcquire("observable", alice, 1, 3600).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", alice, 1, 3600).allowed(),
                "Alice a atteint sa limite");
        assertTrue(RateLimiter.tryAcquire("observable", bob, 1, 3600).allowed(),
                "Bob has his own counter, independent of Alice's");
    }

    @Test
    void shouldTrackEachCommandIndependently() {
        UUID player = UUID.randomUUID();

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());
        assertTrue(RateLimiter.tryAcquire("heal", player, 1, 3600).allowed(),
                "another command has its own counter");
    }

    @Test
    void shouldAllowExecutionAgain_afterWindowExpires() throws InterruptedException {
        UUID player = UUID.randomUUID();

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 1).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", player, 1, 1).allowed());

        Thread.sleep(1100);

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 1).allowed(),
                "once the window has passed, a new use must be allowed");
    }

    @Test
    void shouldResetAllHistory_onClearServerState() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());

        RateLimiter.clearServerState();

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed(),
                "clearServerState() must drop the whole history");
    }

    // ---------------- purge amortie (anti-fuite mémoire) ----------------

    @Test
    void sweep_evictsIdlePlayer_afterWindowElapses() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 10).allowed());
        assertTrue(RateLimiter.isTracked("observable", player));

        // The 10s window has passed, so the sweep drops the idle player's entry.
        long future = System.currentTimeMillis() + 11_000L;
        RateLimiter.sweep(future, name -> 10_000L);

        assertFalse(RateLimiter.isTracked("observable", player),
                "an idle player whose window has passed must be evicted");
    }

    @Test
    void sweep_keepsPlayerStillWithinWindow() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 3600).allowed());

        RateLimiter.sweep(System.currentTimeMillis(), name -> 3_600_000L);

        assertTrue(RateLimiter.isTracked("observable", player),
                "a player still inside their window must not be evicted");
    }

    @Test
    void sweep_dropsWholeBucket_whenRuleRemovedOrDisabled() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("gone", player, 5, 3600).allowed());

        // The resolver answers <= 0, so the rule is gone and the whole bucket is dropped,
        // even where the player's timestamps have not expired yet.
        RateLimiter.sweep(System.currentTimeMillis(), name -> -1L);

        assertFalse(RateLimiter.isTracked("gone", player),
                "the bucket of a command with no active rule must be dropped");
    }

    // ---------------- persistence ----------------

    @Test
    void snapshotAndRestore_carryHistoryAcrossAClear() {
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        assertTrue(RateLimiter.tryAcquire("persisted", player, 1, 3600, now).allowed());

        var saved = RateLimiter.snapshot(now, name -> 3_600_000L);
        RateLimiter.clearServerState();
        RateLimiter.restore(saved, now + 1_000L, name -> 3_600_000L);

        assertFalse(RateLimiter.tryAcquire("persisted", player, 1, 3600, now + 2_000L).allowed(),
                "a use recorded before the restart must still count after restore");
    }

    @Test
    void restore_appliesTheCurrentWindowAndDropsRulesThatNoLongerExist() {
        UUID player = UUID.randomUUID();
        long now = 10_000_000L;
        var persisted = java.util.Map.of(
                "shortened", java.util.Map.of(player, java.util.List.of(now - 120_000L, now - 10_000L)),
                "removed", java.util.Map.of(player, java.util.List.of(now - 1_000L)));

        RateLimiter.restore(persisted, now, name -> name.equals("shortened") ? 60_000L : -1L);

        assertTrue(RateLimiter.isTracked("shortened", player));
        assertFalse(RateLimiter.isTracked("removed", player), "history of a removed rule must not be restored");
        assertTrue(RateLimiter.tryAcquire("shortened", player, 2, 60, now).allowed(),
                "the use older than the current 60s window must no longer count");
        assertFalse(RateLimiter.tryAcquire("shortened", player, 2, 60, now).allowed());
    }

    @Test
    void clockGoingBackwards_doesNotLockPlayersOut() {
        UUID player = UUID.randomUUID();
        long before = 50_000_000L;
        assertTrue(RateLimiter.tryAcquire("clock", player, 1, 10, before).allowed());

        long afterClockMovedBackOneHour = before - 3_600_000L;
        assertFalse(RateLimiter.tryAcquire("clock", player, 1, 10, afterClockMovedBackOneHour).allowed(),
                "the use still counts right after the clock change");
        assertTrue(RateLimiter.tryAcquire("clock", player, 1, 10, afterClockMovedBackOneHour + 11_000L).allowed(),
                "one window later the player must be free again, not an hour later");
    }

    @Test
    void restore_clampsTimestampsFromTheFuture() {
        UUID player = UUID.randomUUID();
        long now = 20_000_000L;
        RateLimiter.restore(java.util.Map.of("future", java.util.Map.of(player, java.util.List.of(now + 86_400_000L))),
                now, name -> 10_000L);
        assertTrue(RateLimiter.tryAcquire("future", player, 1, 10, now + 11_000L).allowed(),
                "a timestamp saved with a clock set a day ahead must expire one window after now");
    }

    @Test
    void internalBudgets_areNeitherPersistedNorDroppedBySweep() {
        String key = "gui:test_budget_" + UUID.randomUUID();
        RateLimiter.registerInternalBudget(key, 10);
        UUID player = UUID.randomUUID();
        long now = System.currentTimeMillis();
        assertTrue(RateLimiter.tryAcquire(key, player, 5, 10, now).allowed());

        assertFalse(RateLimiter.snapshot(now, name -> 3_600_000L).containsKey(key));
        RateLimiter.sweep(now + 1_000L, name -> -1L);
        assertTrue(RateLimiter.isTracked(key, player),
                "a registered internal budget must survive a sweep that finds no admin rule for it");
    }

    @Test
    void dirtyFlag_tracksPersistedChangesOnly() {
        RateLimiter.consumeDirty();
        String key = "gui:dirty_budget_" + UUID.randomUUID();
        RateLimiter.registerInternalBudget(key, 10);
        RateLimiter.tryAcquire(key, UUID.randomUUID(), 5, 10);
        assertFalse(RateLimiter.consumeDirty(), "internal budgets must not trigger a history write");

        RateLimiter.tryAcquire("dirty", UUID.randomUUID(), 5, 10);
        assertTrue(RateLimiter.consumeDirty());
        assertFalse(RateLimiter.consumeDirty(), "consuming the flag resets it");
    }

    @Test
    void maybeSweep_runsAtMostOncePerInterval() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 10).allowed());

        // The first maybeSweep (last sweep = 0 after the clear) runs and drops the expired entry.
        long base = System.currentTimeMillis() + 11_000L;
        RateLimiter.maybeSweep(base, name -> 10_000L);
        assertFalse(RateLimiter.isTracked("observable", player));

        // Insert again, then a second maybeSweep at once: inside the interval, so no sweep.
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 10).allowed());
        RateLimiter.maybeSweep(base + 1_000L, name -> 10_000L);
        assertTrue(RateLimiter.isTracked("observable", player),
                "two sweeps close together: the second must not run, the cost being amortised");
    }
}
