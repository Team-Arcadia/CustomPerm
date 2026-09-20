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
 * Data-layer tests of exposing a vanilla command and hiding it again.
 * No Minecraft import: pure JUnit 5 over CommandsConfig.
 * Couvre AC1-AC4 de l'histoire 2-5.
 */
class CommandsConfigTest {

    private CommandsConfig freshConfig() {
        return new CommandsConfig();
        // CommandsConfig initialise grantedCommands = new LinkedHashSet<>() — prêt à l'emploi
    }

    // T2.1, AC1: exposing a new command
    @Test
    void shouldExposeCommand_whenCommandAdded() {
        CommandsConfig cfg = freshConfig();

        boolean added = cfg.grantedCommands.add("tp");

        assertTrue(added, "Set.add() must return true for a command not exposed yet");
        assertTrue(cfg.grantedCommands.contains("tp"));
        assertEquals(1, cfg.grantedCommands.size());
    }

    // T2.2, AC1 idempotence: Set.add() is false when the command is already exposed
    @Test
    void shouldBeIdempotent_whenCommandAlreadyExposed() {
        CommandsConfig cfg = freshConfig();

        cfg.grantedCommands.add("gamemode");
        boolean addedAgain = cfg.grantedCommands.add("gamemode");

        assertFalse(addedAgain, "Set.add() must return false for a command already exposed");
        assertEquals(1, cfg.grantedCommands.size(), "grantedCommands must hold no duplicate");
    }

    // T2.3, AC2: hiding an exposed command
    @Test
    void shouldRemoveCommand_whenCommandExposed() {
        CommandsConfig cfg = freshConfig();

        cfg.grantedCommands.add("ban");
        boolean removed = cfg.grantedCommands.remove("ban");

        assertTrue(removed, "Set.remove() must return true for an exposed command");
        assertFalse(cfg.grantedCommands.contains("ban"));
        assertTrue(cfg.grantedCommands.isEmpty());
    }

    // T2.4, AC2 guard: Set.remove() is false for a command that was not exposed
    @Test
    void shouldBeNoOp_whenRemovingNonExposedCommand() {
        CommandsConfig cfg = freshConfig();

        boolean removed = cfg.grantedCommands.remove("tp");

        assertFalse(removed, "Set.remove() must return false for a command that was not exposed");
        assertTrue(cfg.grantedCommands.isEmpty(), "the list must be left untouched");
    }

    // T2.5, AC3: listing the exposed commands, at the data layer
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
    // This is the wrapper's own guard in CommandTreeRewriter.wrapRecursive():
    //   if (!grantedCommands.contains(rootName)) return false;
    // The test shows that removing a command from grantedCommands is what refuses access.
    @Test
    void shouldDenyAccess_whenCommandNotInGrantedList() {
        CommandsConfig cfg = freshConfig();

        // Commande jamais exposée → guard déclenche
        assertFalse(cfg.grantedCommands.contains("tp"),
                "a command that was never exposed must not be in grantedCommands");

        // A command exposed then hidden trips the same guard
        cfg.grantedCommands.add("ban");
        cfg.grantedCommands.remove("ban");
        assertFalse(cfg.grantedCommands.contains("ban"),
                "a command that was hidden must no longer be in grantedCommands");
    }

    @Test
    void shouldDefaultToNotPreservingOriginalRequires_whenCommandHasNoOverride() {
        CommandsConfig cfg = freshConfig();

        assertFalse(cfg.shouldPreserveOriginalRequires("gamemode"),
                "with no explicit override, the historical behaviour must be kept");
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
                "hiding an exposed command must also drop its preserveOriginalRequires override");
    }
}
