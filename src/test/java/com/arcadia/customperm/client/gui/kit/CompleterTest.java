/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.kit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a field proposes, and what taking a candidate leaves in it. The list itself is drawn and checked in game. */
class CompleterTest {

    private static final List<String> NODES = List.of(
            "essentials.fly", "essentials.fly.others", "customperm.command.fly", "flyspeed.use", "minecraft.command.tp");

    @Test
    void prefixThenSegmentThenAnywhere() {
        assertEquals(List.of("flyspeed.use", "essentials.fly", "essentials.fly.others", "customperm.command.fly"),
                Completer.match("fly", NODES));
        assertEquals(List.of("essentials.fly", "essentials.fly.others"), Completer.match("ESS", NODES));
    }

    @Test
    void theTypedValueItselfIsNotProposed() {
        assertEquals(List.of("essentials.fly.others"), Completer.match("essentials.fly", NODES));
    }

    @Test
    void emptyTextProposesEverythingOnceInSourceOrder() {
        assertEquals(List.of("a", "b"), Completer.match("", List.of("a", "b", "a", "")));
    }

    @Test
    void theListIsCapped() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) many.add("node." + i);
        assertEquals(Completer.LIMIT, Completer.match("node", many).size());
        assertEquals(Completer.LIMIT, Completer.match("", many).size());
    }

    @Test
    void lastPartCompletesAfterTheSeparatorOnly() {
        Completer contexts = Completer.lastPart(',', () -> List.of("world=the_nether", "server=hub", "gamemode=creative"));
        Completer.Proposal proposal = contexts.propose("world=the_nether, ser");
        assertEquals(List.of("server=hub"), proposal.candidates());
        assertEquals("world=the_nether, server=hub", proposal.apply("world=the_nether, ser", "server=hub"));
        // A bare world name finds its world= form, the prefix before the value being a segment.
        assertEquals(List.of("world=the_nether"), contexts.propose("nether").candidates());
    }

    @Test
    void commandLineCompletesTheRootThenTheArguments() {
        Completer line = Completer.commandLine(() -> List.of("tp", "tell", "give"), () -> List.of("${target}", "Steve"));
        Completer.Proposal root = line.propose("/t");
        assertEquals(List.of("tp", "tell"), root.candidates());
        assertEquals("/tp", root.apply("/t", "tp"));
        Completer.Proposal argument = line.propose("tp ${t");
        assertEquals(List.of("${target}"), argument.candidates());
        assertEquals("tp ${target}", argument.apply("tp ${t", "${target}"));
        assertTrue(line.propose("tp Zed").isEmpty());
    }
}
