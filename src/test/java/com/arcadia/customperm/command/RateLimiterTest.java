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
}
