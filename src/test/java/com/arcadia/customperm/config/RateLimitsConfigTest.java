package com.arcadia.customperm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche data pour les limites d'exécution par commande.
 * Zéro import Minecraft — tests JUnit 5 purs sur RateLimitsConfig.
 */
class RateLimitsConfigTest {

    @Test
    void shouldDefaultToEnabledWithSaneValues_whenRuleIsFreshlyCreated() {
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();

        assertTrue(rule.enabled);
        assertEquals(10, rule.maxExecutions);
        assertEquals(3600, rule.windowSeconds);
    }

    @Test
    void shouldReportEnforced_onlyWhenRuleExistsAndIsEnabled() {
        RateLimitsConfig cfg = new RateLimitsConfig();
        assertFalse(cfg.isEnforced("observable"), "Pas de règle -> pas d'application");

        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.enabled = false;
        cfg.rules.put("observable", rule);
        assertFalse(cfg.isEnforced("observable"), "Règle désactivée -> pas d'application");

        rule.enabled = true;
        assertTrue(cfg.isEnforced("observable"), "Règle activée -> appliquée");
    }

    @Test
    void shouldNormalizeNullMap_toEmptyMap() {
        RateLimitsConfig cfg = new RateLimitsConfig();
        cfg.rules = null;

        cfg.normalize();

        assertNotNull(cfg.rules);
        assertTrue(cfg.rules.isEmpty());
    }

    @Test
    void shouldDropNullRuleEntries_onNormalize() {
        RateLimitsConfig cfg = new RateLimitsConfig();
        cfg.rules.put("observable", null);
        cfg.rules.put("heal", new RateLimitsConfig.Rule());

        cfg.normalize();

        assertFalse(cfg.rules.containsKey("observable"));
        assertTrue(cfg.rules.containsKey("heal"));
    }

    @Test
    void shouldClampInvalidValues_toMinimumOfOne_onNormalize() {
        RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
        rule.maxExecutions = 0;
        rule.windowSeconds = -5;

        rule.normalize();

        assertEquals(1, rule.maxExecutions);
        assertEquals(1, rule.windowSeconds);
    }
}
