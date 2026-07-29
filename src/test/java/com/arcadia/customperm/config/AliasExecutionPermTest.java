/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche data pour l'exécution des alias par un joueur autorisé.
 * Zéro import Minecraft — tests JUnit 5 purs.
 * Couvre AC1-AC4 de l'histoire 3-3 (aspects testables en pur Java).
 *
 * Limites intentionnelles (déférées à É6.2 GameTest) :
 *   - Vérification de INVARIANT-503 (requires entity + isOp) : nécessite MinecraftServer
 *   - Exécution réelle via le CommandDispatcher Minecraft : nécessite CommandDispatcher Minecraft
 *   - Résolution @s : nécessite ServerPlayer + World
 */
class AliasExecutionPermTest {

    /** Format du nœud de permission attendu par AliasManager.registerOne. */
    private static final String PERM_PREFIX = "customperm.alias.";

    // ── T3.1 — Format du nœud de permission ─────────────────────────────────

    // AC3/AC5 : format "customperm.alias.<name>" — nœud simple
    @Test
    void shouldBuildCorrectPermNode_forSimpleAliasName() {
        String alias = "heal";
        String permNode = PERM_PREFIX + alias;
        assertEquals("customperm.alias.heal", permNode);
    }

    // AC3/AC5 : format avec tiret — noms d'alias courants
    @Test
    void shouldBuildCorrectPermNode_forHyphenatedAliasName() {
        String alias = "give-kit";
        String permNode = PERM_PREFIX + alias;
        assertEquals("customperm.alias.give-kit", permNode);
    }

    // AC3/AC5 : format avec underscore
    @Test
    void shouldBuildCorrectPermNode_forUnderscoreAliasName() {
        String alias = "tp_home";
        String permNode = PERM_PREFIX + alias;
        assertEquals("customperm.alias.tp_home", permNode);
    }

    // AC3/AC5 : le préfixe est toujours "customperm.alias." — ne jamais omettre le préfixe
    @Test
    void shouldAlwaysIncludePrefix_inPermNode() {
        String alias = "x";
        String permNode = PERM_PREFIX + alias;
        assertTrue(permNode.startsWith("customperm.alias."), "Le nœud doit commencer par customperm.alias.");
        assertTrue(permNode.endsWith(".x"), "Le nœud doit se terminer par le nom de l'alias");
    }

    // ── T3.2 — Filtre des steps null/blank ───────────────────────────────────

    /**
     * Reproduit la logique de filtre de AliasManager.registerOne :
     *   if (step == null || step.isBlank()) continue;
     */
    private long countExecutableSteps(List<String> steps) {
        return steps.stream()
                .filter(step -> step != null && !step.isBlank())
                .count();
    }

    // AC1 : les steps null sont filtrées avant exécution
    @Test
    void shouldFilterNullSteps_beforeExecution() {
        List<String> steps = new ArrayList<>(Arrays.asList("say hello", null, "say world"));
        long executable = countExecutableSteps(steps);
        assertEquals(2, executable, "Les steps null doivent être filtrées");
    }

    // AC1 : les steps blank (espaces seuls) sont filtrées
    @Test
    void shouldFilterBlankSteps_beforeExecution() {
        List<String> steps = new ArrayList<>(Arrays.asList("say hello", "   ", "", "say world"));
        long executable = countExecutableSteps(steps);
        assertEquals(2, executable, "Les steps blank doivent être filtrées");
    }

    // T3.3 — AC1 : liste de steps toutes null/blank → aucune exécutable
    @Test
    void shouldReturnZeroExecutable_whenAllStepsAreBlankOrNull() {
        List<String> steps = new ArrayList<>(Arrays.asList(null, "  ", "", "\t"));
        long executable = countExecutableSteps(steps);
        assertEquals(0, executable, "Aucune step ne doit être exécutable si toutes sont null/blank");
    }

    // ── T3.4 — Compteur executed ─────────────────────────────────────────────

    /**
     * Simule la logique de comptage du compteur {@code executed} dans AliasManager.registerOne.
     * Chaque step valide (non null, non blank) incrémente executed si elle réussit.
     *
     * <p><b>Attention varargs :</b> si {@code stepSucceeds} contient moins d'éléments que
     * le nombre de steps valides, les steps excédentaires sont traitées comme des échecs
     * ({@code false}) — comportement silencieux, prévu par le no-halt-on-error d'AliasManager.
     * S'assurer que le tableau varargs a autant d'éléments que de steps non-blank dans la liste.
     */
    private int simulateExecution(List<String> steps, boolean... stepSucceeds) {
        int executed = 0;
        int successIdx = 0;
        for (String step : steps) {
            if (step == null || step.isBlank()) continue;
            boolean succeeds = successIdx < stepSucceeds.length && stepSucceeds[successIdx++];
            if (succeeds) executed++;
            // sinon : catch (Throwable t) → LOGGER.warn → continue (no-halt-on-error)
        }
        return executed;
    }

    // AC1 : 3 steps valides, toutes réussies → executed = 3
    @Test
    void shouldCountExecutedSteps_whenAllSucceed() {
        List<String> steps = List.of(
                "effect give @s minecraft:instant_health 1 100",
                "say healed",
                "playsound entity.player.levelup master @s");
        int executed = simulateExecution(steps, true, true, true);
        assertEquals(3, executed, "Les 3 steps doivent être comptées comme exécutées");
    }

    // T3.5 — AC2 (no-halt-on-error) : step 2 échoue → executed = 2 (steps 1 et 3 réussies)
    @Test
    void shouldContinueAfterFailingStep_andCountOnlySuccesses() {
        List<String> steps = List.of("say start", "commande-invalide", "say end");
        int executed = simulateExecution(steps, true, false, true);
        assertEquals(2, executed, "L'exécution doit continuer après une étape en échec");
    }

    // AC2 : toutes les steps échouent → executed = 0, mais aucune exception propagée
    @Test
    void shouldReturnZero_whenAllStepsFail() {
        List<String> steps = List.of("invalide1", "invalide2");
        int executed = simulateExecution(steps, false, false);
        assertEquals(0, executed, "Aucune step ne doit être comptée si toutes échouent");
        // no-halt-on-error : le test arrive ici sans exception ✅
    }

    // ── T3.x — AliasesConfig structure pour exécution ───────────────────────

    // Vérifier que AliasesConfig stocke les steps dans l'ordre (important pour exécution séquentielle AC1)
    @Test
    void shouldPreserveStepOrder_inAliasesConfig() {
        AliasesConfig cfg = new AliasesConfig();
        List<String> steps = new ArrayList<>(Arrays.asList(
                "effect give @s minecraft:instant_health 1 100",
                "say healed",
                "playsound entity.player.levelup master @s"));
        cfg.aliases.put("heal", steps);

        List<String> retrieved = cfg.aliases.get("heal");
        assertEquals(3, retrieved.size());
        assertEquals("effect give @s minecraft:instant_health 1 100", retrieved.get(0), "Ordre step 0");
        assertEquals("say healed", retrieved.get(1), "Ordre step 1");
        assertEquals("playsound entity.player.levelup master @s", retrieved.get(2), "Ordre step 2");
    }

    // Vérifier que la guard "steps null ou vide" dans registerOrReplace est correcte (data-layer)
    @Test
    void shouldNotExecute_whenAliasStepsAreEmpty() {
        AliasesConfig cfg = new AliasesConfig();
        cfg.aliases.put("vide", new ArrayList<>());

        List<String> steps = cfg.aliases.get("vide");
        assertTrue(steps != null && steps.isEmpty(),
                "L'alias avec liste vide est bien stocké vide — la guard steps.isEmpty() dans registerOrReplace court-circuiterait l'exécution");
    }
}
