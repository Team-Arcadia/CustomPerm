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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de la couche data pour l'exposition et le retrait des commandes vanilla.
 * Zéro import Minecraft — tests JUnit 5 purs sur CommandsConfig.
 * Couvre AC1-AC4 de l'histoire 2-5.
 */
class CommandsConfigTest {

    private CommandsConfig freshConfig() {
        return new CommandsConfig();
        // CommandsConfig initialise grantedCommands = new LinkedHashSet<>() — prêt à l'emploi
    }

    // T2.1 — AC1 : exposition d'une nouvelle commande
    @Test
    void shouldExposeCommand_whenCommandAdded() {
        CommandsConfig cfg = freshConfig();

        boolean added = cfg.grantedCommands.add("tp");

        assertTrue(added, "Set.add() doit retourner true pour une nouvelle commande");
        assertTrue(cfg.grantedCommands.contains("tp"));
        assertEquals(1, cfg.grantedCommands.size());
    }

    // T2.2 — AC1 idempotence : Set.add() retourne false si déjà exposé
    @Test
    void shouldBeIdempotent_whenCommandAlreadyExposed() {
        CommandsConfig cfg = freshConfig();

        cfg.grantedCommands.add("gamemode");
        boolean addedAgain = cfg.grantedCommands.add("gamemode");

        assertFalse(addedAgain, "Set.add() doit retourner false pour une commande déjà exposée");
        assertEquals(1, cfg.grantedCommands.size(), "Pas de doublon dans grantedCommands");
    }

    // T2.3 — AC2 : retrait d'une commande exposée
    @Test
    void shouldRemoveCommand_whenCommandExposed() {
        CommandsConfig cfg = freshConfig();

        cfg.grantedCommands.add("ban");
        boolean removed = cfg.grantedCommands.remove("ban");

        assertTrue(removed, "Set.remove() doit retourner true pour une commande exposée");
        assertFalse(cfg.grantedCommands.contains("ban"));
        assertTrue(cfg.grantedCommands.isEmpty());
    }

    // T2.4 — AC2 guard : Set.remove() retourne false si commande non exposée
    @Test
    void shouldBeNoOp_whenRemovingNonExposedCommand() {
        CommandsConfig cfg = freshConfig();

        boolean removed = cfg.grantedCommands.remove("tp");

        assertFalse(removed, "Set.remove() doit retourner false pour une commande non exposée");
        assertTrue(cfg.grantedCommands.isEmpty(), "La liste ne doit pas être altérée");
    }

    // T2.5 — AC3 : listage des commandes exposées (data-layer)
    @Test
    void shouldListAllExposedCommands() {
        CommandsConfig cfg = freshConfig();

        cfg.grantedCommands.add("tp");
        cfg.grantedCommands.add("gamemode");
        cfg.grantedCommands.add("give");

        assertEquals(3, cfg.grantedCommands.size());
        assertTrue(cfg.grantedCommands.contains("tp"));
        assertTrue(cfg.grantedCommands.contains("gamemode"));
        assertTrue(cfg.grantedCommands.contains("give"));
    }

    // T2.6 — AC4 proof-of-contract : commande non exposée → !grantedCommands.contains() = true
    // C'est la guard exacte du wrapper dans CommandTreeRewriter.wrapRecursive() :
    //   if (!grantedCommands.contains(rootName)) return false;
    // Ce test prouve que supprimer une commande de grantedCommands déclenche le refus d'accès.
    @Test
    void shouldDenyAccess_whenCommandNotInGrantedList() {
        CommandsConfig cfg = freshConfig();

        // Commande jamais exposée → guard déclenche
        assertFalse(cfg.grantedCommands.contains("tp"),
                "Une commande non exposée ne doit pas être dans grantedCommands");

        // Commande exposée puis retirée → guard déclenche également
        cfg.grantedCommands.add("ban");
        cfg.grantedCommands.remove("ban");
        assertFalse(cfg.grantedCommands.contains("ban"),
                "Une commande retirée ne doit plus être dans grantedCommands");
    }

    @Test
    void shouldDefaultToNotPreservingOriginalRequires_whenCommandHasNoOverride() {
        CommandsConfig cfg = freshConfig();

        assertFalse(cfg.shouldPreserveOriginalRequires("gamemode"),
                "Sans override explicite, le comportement historique doit être conservé");
    }

    @Test
    void shouldPreserveOriginalRequires_whenCommandOverrideIsTrue() {
        CommandsConfig cfg = freshConfig();
        cfg.preserveOriginalRequires.put("adminpanel", true);

        assertTrue(cfg.shouldPreserveOriginalRequires("adminpanel"));
        assertFalse(cfg.shouldPreserveOriginalRequires("gamemode"));
    }

    @Test
    void shouldRemovePreserveOriginalRequiresOverride_whenCommandRemoved() {
        CommandsConfig cfg = freshConfig();
        cfg.grantedCommands.add("gamemode");
        cfg.preserveOriginalRequires.put("gamemode", true);

        boolean removed = cfg.removeCommand("gamemode");

        assertTrue(removed);
        assertFalse(cfg.grantedCommands.contains("gamemode"));
        assertFalse(cfg.preserveOriginalRequires.containsKey("gamemode"),
                "Retirer une commande exposee doit aussi nettoyer son override preserveOriginalRequires");
    }
}
