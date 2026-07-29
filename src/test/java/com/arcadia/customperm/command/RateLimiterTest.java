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
 * Tests purs Java (zéro import Minecraft) pour le compteur en mémoire à fenêtre glissante.
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
            assertTrue(result.allowed(), "Exécution #" + i + " doit être autorisée");
        }

        RateLimiter.Result blocked = RateLimiter.tryAcquire("observable", player, 5, 3600);
        assertFalse(blocked.allowed(), "La 6e exécution doit être refusée (max=5)");
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
                "Bob a son propre compteur, indépendant d'Alice");
    }

    @Test
    void shouldTrackEachCommandIndependently() {
        UUID player = UUID.randomUUID();

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());
        assertTrue(RateLimiter.tryAcquire("heal", player, 1, 3600).allowed(),
                "Une commande différente a son propre compteur");
    }

    @Test
    void shouldAllowExecutionAgain_afterWindowExpires() throws InterruptedException {
        UUID player = UUID.randomUUID();

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 1).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", player, 1, 1).allowed());

        Thread.sleep(1100);

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 1).allowed(),
                "Après expiration de la fenêtre, une nouvelle exécution doit être autorisée");
    }

    @Test
    void shouldResetAllHistory_onClearServerState() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());
        assertFalse(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed());

        RateLimiter.clearServerState();

        assertTrue(RateLimiter.tryAcquire("observable", player, 1, 3600).allowed(),
                "clearServerState() doit purger tout l'historique");
    }

    // ---------------- purge amortie (anti-fuite mémoire) ----------------

    @Test
    void sweep_evictsIdlePlayer_afterWindowElapses() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 10).allowed());
        assertTrue(RateLimiter.isTracked("observable", player));

        // Fenêtre de 10s écoulée : la sweep purge l'entrée du joueur inactif.
        long future = System.currentTimeMillis() + 11_000L;
        RateLimiter.sweep(future, name -> 10_000L);

        assertFalse(RateLimiter.isTracked("observable", player),
                "Un joueur inactif dont la fenêtre a expiré doit être évincé");
    }

    @Test
    void sweep_keepsPlayerStillWithinWindow() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 3600).allowed());

        RateLimiter.sweep(System.currentTimeMillis(), name -> 3_600_000L);

        assertTrue(RateLimiter.isTracked("observable", player),
                "Un joueur encore dans sa fenêtre ne doit pas être évincé");
    }

    @Test
    void sweep_dropsWholeBucket_whenRuleRemovedOrDisabled() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("gone", player, 5, 3600).allowed());

        // resolver renvoie <= 0 : la règle n'existe plus → le bucket entier est supprimé,
        // même si les timestamps du joueur ne sont pas encore expirés.
        RateLimiter.sweep(System.currentTimeMillis(), name -> -1L);

        assertFalse(RateLimiter.isTracked("gone", player),
                "Le bucket d'une commande sans règle active doit être supprimé");
    }

    @Test
    void maybeSweep_runsAtMostOncePerInterval() {
        UUID player = UUID.randomUUID();
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 10).allowed());

        // Premier maybeSweep (dernier sweep = 0 après clear) : s'exécute et purge l'entrée expirée.
        long base = System.currentTimeMillis() + 11_000L;
        RateLimiter.maybeSweep(base, name -> 10_000L);
        assertFalse(RateLimiter.isTracked("observable", player));

        // Ré-insertion, puis un second maybeSweep juste après : sous l'intervalle → aucune sweep.
        assertTrue(RateLimiter.tryAcquire("observable", player, 5, 10).allowed());
        RateLimiter.maybeSweep(base + 1_000L, name -> 10_000L);
        assertTrue(RateLimiter.isTracked("observable", player),
                "Deux sweeps rapprochés : le second ne doit pas s'exécuter (amortissement)");
    }
}
