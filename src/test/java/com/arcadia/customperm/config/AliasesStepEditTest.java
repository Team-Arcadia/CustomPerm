package com.arcadia.customperm.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche data pour l'édition incrémentale des alias.
 * Zéro import Minecraft — tests JUnit 5 purs sur AliasesConfig.
 * Couvre AC1-AC5 de l'histoire 3-2.
 */
class AliasesStepEditTest {

    private AliasesConfig freshConfig() {
        return new AliasesConfig();
    }

    /** Helper : alias avec N steps pré-chargées. */
    private AliasesConfig configWith(String aliasName, String... steps) {
        AliasesConfig cfg = freshConfig();
        cfg.aliases.put(aliasName, new ArrayList<>(Arrays.asList(steps)));
        return cfg;
    }

    // T2.1 — AC1 : computeIfAbsent sur alias existant → step ajoutée en fin de liste
    @Test
    void shouldAppendStep_whenAliasExists() {
        AliasesConfig cfg = configWith("heal", "effect give @s minecraft:instant_health 1 100");

        // Simule aliasAddStep : computeIfAbsent puis add
        cfg.aliases.computeIfAbsent("heal", k -> new ArrayList<>()).add("say healed");

        List<String> steps = cfg.aliases.get("heal");
        assertEquals(2, steps.size(), "La step doit être ajoutée en fin de liste");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0));
        assertEquals("say healed", steps.get(1));
    }

    // T2.2 — AC2 : computeIfAbsent sur clé absente → alias créé avec 1 étape
    @Test
    void shouldCreateAlias_implicitly_whenAddStep_andAliasDoesNotExist() {
        AliasesConfig cfg = freshConfig();
        assertFalse(cfg.aliases.containsKey("kit"), "L'alias ne doit pas exister avant ajout");

        cfg.aliases.computeIfAbsent("kit", k -> new ArrayList<>()).add("give @s diamond_sword 1");

        assertTrue(cfg.aliases.containsKey("kit"), "L'alias doit être créé implicitement");
        assertEquals(1, cfg.aliases.get("kit").size(), "L'alias doit avoir 1 étape");
        assertEquals("give @s diamond_sword 1", cfg.aliases.get("kit").get(0));
    }

    // T2.3 — AC3 : steps.remove(0) → step correcte retirée, liste réduite
    @Test
    void shouldRemoveStep_byIndex_whenExists() {
        AliasesConfig cfg = configWith("kit", "give @s diamond_sword 1", "give @s bread 64");

        // Simule aliasRemoveStep : List.remove(int)
        String removed = cfg.aliases.get("kit").remove(0);

        assertEquals("give @s diamond_sword 1", removed, "La step retirée doit être la première");
        assertEquals(1, cfg.aliases.get("kit").size(), "La liste doit être réduite à 1 step");
        assertEquals("give @s bread 64", cfg.aliases.get("kit").get(0));
    }

    // T2.4 — AC3 ré-indexation : retirer index 1 sur [A,B,C] → [A,C], accès par index 1 = C
    @Test
    void shouldReindex_remainingSteps_afterMiddleRemoval() {
        AliasesConfig cfg = configWith("combo", "stepA", "stepB", "stepC");

        cfg.aliases.get("combo").remove(1);  // retire stepB (index 1)

        List<String> steps = cfg.aliases.get("combo");
        assertEquals(2, steps.size(), "La liste doit avoir 2 steps après suppression du milieu");
        assertEquals("stepA", steps.get(0), "Index 0 doit être stepA");
        assertEquals("stepC", steps.get(1), "Index 1 doit être stepC (ré-indexation automatique)");
    }

    // T2.5 — AC3 edge : if (steps.isEmpty()) aliases.remove(name) — alias disparu quand plus aucune step
    @Test
    void shouldRemoveAliasFromMap_whenLastStepDeleted() {
        AliasesConfig cfg = configWith("solo", "say hello");

        List<String> steps = cfg.aliases.get("solo");
        steps.remove(0);
        // Reproduit le comportement aliasRemoveStep : supprime l'alias si steps vide
        if (steps.isEmpty()) cfg.aliases.remove("solo");

        assertFalse(cfg.aliases.containsKey("solo"), "L'alias doit disparaître de la map quand sa dernière step est supprimée");
        assertTrue(cfg.aliases.isEmpty(), "La map doit être vide");
    }

    // T2.6 — AC4 guard : aliases.get("inexistant") → null, aucune mutation
    @Test
    void shouldReturnNull_whenRemovingStep_fromNonexistentAlias() {
        AliasesConfig cfg = freshConfig();

        // Reproduit le premier guard de aliasRemoveStep
        List<String> steps = cfg.aliases.get("inexistant");

        assertNull(steps, "aliases.get() doit retourner null pour un alias inexistant");
        assertTrue(cfg.aliases.isEmpty(), "La map ne doit pas être altérée");
    }

    // T2.7 — AC4 : index >= steps.size() → steps inchangées (vérification de la condition)
    @Test
    void shouldNotRemoveStep_whenIndexOutOfBounds() {
        AliasesConfig cfg = configWith("heal", "effect give @s minecraft:instant_health 1 100", "say healed");
        List<String> steps = cfg.aliases.get("heal");
        int outOfBoundsIndex = steps.size(); // premier index invalide (juste après le dernier)

        // Reproduit le guard de aliasRemoveStep : ne supprime rien si index hors bornes
        boolean indexValid = outOfBoundsIndex >= 0 && outOfBoundsIndex < steps.size();
        if (indexValid) steps.remove(outOfBoundsIndex);

        assertFalse(indexValid, "L'index doit être détecté hors bornes");
        assertEquals(2, steps.size(), "Les steps ne doivent pas être modifiées");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0));
        assertEquals("say healed", steps.get(1));
    }

    // T2.8 — AC5 data-layer : liste de 3 steps, vérifier accès steps.get(0), get(1), get(2)
    @Test
    void shouldListSteps_withZeroBasedIndex() {
        AliasesConfig cfg = configWith("combo",
                "effect give @s minecraft:instant_health 1 100",
                "say healed",
                "playsound entity.player.levelup master @s");

        List<String> steps = cfg.aliases.get("combo");

        assertEquals(3, steps.size(), "L'alias doit avoir 3 étapes");
        assertEquals("effect give @s minecraft:instant_health 1 100", steps.get(0), "Index 0");
        assertEquals("say healed", steps.get(1), "Index 1");
        assertEquals("playsound entity.player.levelup master @s", steps.get(2), "Index 2");
    }

    // T2.9 — AC5 guard : aliasSteps sur alias inexistant → null (chemin symétrique à T2.6 pour aliasRemoveStep)
    @Test
    void shouldReturnNull_forSteps_whenAliasDoesNotExist() {
        AliasesConfig cfg = freshConfig();

        // Reproduit le premier guard de aliasSteps : aliases.get(name) == null
        List<String> steps = cfg.aliases.get("inexistant");

        assertNull(steps, "aliases.get() doit retourner null pour un alias inexistant (chemin aliasSteps)");
        assertTrue(cfg.aliases.isEmpty(), "La map ne doit pas être altérée");
    }

    // T2.10 — AC5 guard : aliasSteps sur alias avec liste vide → steps.isEmpty() == true
    @Test
    void shouldHaveEmptyList_whenAliasExistsButHasNoSteps() {
        AliasesConfig cfg = freshConfig();
        // Alias présent dans la map mais sans steps (état transitoire possible)
        cfg.aliases.put("vide", new ArrayList<>());

        List<String> steps = cfg.aliases.get("vide");

        assertNotNull(steps, "La liste doit exister dans la map");
        assertTrue(steps.isEmpty(), "La liste doit être vide — aliasSteps retournerait 'No such alias'");
    }

    // T2.11 — AC3 boundary : suppression au dernier index valide (steps.size()-1)
    @Test
    void shouldRemoveStep_atLastIndex() {
        AliasesConfig cfg = configWith("combo", "stepA", "stepB", "stepC");
        List<String> steps = cfg.aliases.get("combo");
        int lastIndex = steps.size() - 1; // index 2

        String removed = steps.remove(lastIndex);

        assertEquals("stepC", removed, "La dernière step doit être retirée");
        assertEquals(2, steps.size(), "La liste doit être réduite à 2 steps");
        assertEquals("stepA", steps.get(0));
        assertEquals("stepB", steps.get(1));
    }
}
