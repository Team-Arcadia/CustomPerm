/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.admin.KnownNames;
import com.arcadia.customperm.client.gui.kit.Completer;
import com.arcadia.customperm.network.gui.GuiVocabulary;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The completers of the admin pages, over the vocabulary the server sent last. A field that designates
 * something existing completes; a field that creates a name does not, since what it proposes would be a
 * name already taken.
 *
 * <p>Client thread only, like the screens that read it.
 */
public final class Completions {

    private static GuiVocabulary vocabulary = GuiVocabulary.EMPTY;

    private Completions() {
    }

    public static void store(GuiVocabulary received) {
        vocabulary = received;
    }

    public static GuiVocabulary vocabulary() {
        return vocabulary;
    }

    public static Completer nodes() {
        return Completer.of(() -> vocabulary.nodes());
    }

    public static Completer grades() {
        return Completer.of(() -> vocabulary.grades());
    }

    public static Completer players() {
        return Completer.of(() -> vocabulary.players());
    }

    /** Contexts, one after the other separated by commas, as the world fields take them. */
    public static Completer contexts() {
        return Completer.lastPart(',', () -> vocabulary.contexts());
    }

    public static Completer durations() {
        return Completer.of(() -> KnownNames.DURATIONS);
    }

    /** Commands and aliases together: what a rate limit or a command row names. */
    public static Completer commandsAndAliases() {
        return Completer.of(Completions::commandsThenAliases);
    }

    /**
     * A command line: its first word among the commands and aliases, a later word among {@code arguments}
     * then player names, which is what the arguments of a step most often are.
     */
    public static Completer commandLine(Supplier<? extends java.util.Collection<String>> arguments) {
        return Completer.commandLine(Completions::commandsThenAliases, () -> {
            List<String> names = new ArrayList<>(arguments.get());
            names.addAll(vocabulary.players());
            return names;
        });
    }

    /** Server names of the cluster, one after the other separated by commas. */
    public static Completer servers(List<String> alsoAccepted) {
        return Completer.lastPart(',', () -> {
            List<String> names = new ArrayList<>(alsoAccepted);
            names.addAll(vocabulary.servers());
            return names;
        });
    }

    public static Completer metaKeys() {
        return Completer.of(() -> vocabulary.metaKeys());
    }

    /** The values in use for the key {@code key} returns at the time of typing. */
    public static Completer metaValues(Supplier<String> key) {
        return Completer.of(() -> vocabulary.valuesOf(key.get().trim()));
    }

    public static Completer chatTexts() {
        return Completer.of(() -> vocabulary.chatTexts());
    }

    /** Completes a search box against the names on its page. */
    public static Completer search(Supplier<? extends java.util.Collection<String>> names) {
        return Completer.of(names);
    }

    private static List<String> commandsThenAliases() {
        List<String> names = new ArrayList<>(vocabulary.commands());
        names.addAll(vocabulary.aliases());
        return names;
    }
}
