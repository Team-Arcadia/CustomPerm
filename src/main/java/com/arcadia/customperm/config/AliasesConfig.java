/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AliasesConfig {
    /**
     * alias name -> ordered list of commands (without leading slash) to execute in sequence.
     * Example: "heal" -> ["effect give @s minecraft:instant_health 1 100", "effect give @s minecraft:saturation 1 100"].
     * A single-step alias is just a list of one element.
     */
    public Map<String, List<String>> aliases = new LinkedHashMap<>();

    /**
     * alias name -> the arguments it takes, in the order they are typed. An alias that takes none is
     * absent from this map, which is what every alias written before the feature existed looks like:
     * a file from an earlier version loads unchanged.
     *
     * <p>A step reaches an argument through {@code ${name}} (see {@link AliasParameters}), a form no
     * command syntax produces, so NBT and JSON braces in a step are left alone.
     */
    public Map<String, List<Parameter>> aliasParameters = new LinkedHashMap<>();

    /** Resolved through the command source, so only an online player is accepted. */
    public static final String TYPE_PLAYER = "player";
    /** A whole number, within the declared range when there is one. */
    public static final String TYPE_INTEGER = "integer";
    /** One word, no space; suggested and restricted by the declared choices when there are any. */
    public static final String TYPE_WORD = "word";
    /** The rest of the line, spaces included. Only the last argument may be one. */
    public static final String TYPE_TEXT = "text";

    public static final List<String> TYPES = List.of(TYPE_PLAYER, TYPE_INTEGER, TYPE_WORD, TYPE_TEXT);

    /** Most arguments one alias takes; past that the chain is unreadable in tab completion. */
    public static final int MAX_PARAMETERS = 8;
    /** Most choices one argument offers. */
    public static final int MAX_CHOICES = 32;

    /** One argument of an alias. */
    public static class Parameter {
        public String name;
        public String type = TYPE_WORD;
        /** The argument may be left out, in which case {@link #defaultValue} is substituted. */
        public boolean optional;
        /** Substituted when an optional argument is left out; an empty string when null. */
        public String defaultValue;
        /** Lowest accepted value, {@code integer} only; null means no lower bound. */
        public Integer min;
        /** Highest accepted value, {@code integer} only; null means no upper bound. */
        public Integer max;
        /** {@code word} only: what tab completion offers, and the only values accepted. */
        public List<String> choices = new ArrayList<>();
        /**
         * {@code text} only: whether the value may contain an entity selector. Off by default,
         * because a step runs at op level 4 and a selector slipped into one widens what the step
         * touches (see the README). The other types cannot carry one anyway: Brigadier's word
         * reader refuses an {@code @}, and a player argument is resolved before substitution.
         */
        public boolean allowSelectors;

        public Parameter() {
        }

        public Parameter(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public boolean greedy() {
            return TYPE_TEXT.equals(type);
        }

        /** The value substituted when the argument is left out. */
        public String fallback() {
            return defaultValue == null ? "" : defaultValue;
        }

        public void normalize() {
            name = name == null ? null : name.trim().toLowerCase(Locale.ROOT);
            type = type == null ? TYPE_WORD : type.trim().toLowerCase(Locale.ROOT);
            if (!TYPES.contains(type)) type = TYPE_WORD;
            if (defaultValue != null && defaultValue.isEmpty()) defaultValue = null;
            if (defaultValue != null && !optional) optional = true;
            if (!TYPE_INTEGER.equals(type)) {
                min = null;
                max = null;
            } else if (min != null && max != null && min > max) {
                int low = max;
                max = min;
                min = low;
            }
            if (choices == null) choices = new ArrayList<>();
            choices.removeIf(choice -> choice == null || choice.isBlank());
            if (!TYPE_WORD.equals(type)) choices.clear();
            if (choices.size() > MAX_CHOICES) choices = new ArrayList<>(choices.subList(0, MAX_CHOICES));
            if (!TYPE_TEXT.equals(type)) allowSelectors = false;
        }
    }

    public void normalize() {
        if (aliases == null) aliases = new LinkedHashMap<>();
        // A null step list or a null step would NPE in alias listing, GUI sync and execution.
        aliases.values().removeIf(Objects::isNull);
        aliases.values().forEach(steps -> steps.removeIf(Objects::isNull));
        aliases.values().removeIf(List::isEmpty);

        if (aliasParameters == null) aliasParameters = new LinkedHashMap<>();
        aliasParameters.values().removeIf(Objects::isNull);
        // Parameters of an alias that no longer exists would be invisible and still take a row in the
        // file and in a cluster store.
        aliasParameters.keySet().retainAll(aliases.keySet());
        aliasParameters.values().forEach(AliasesConfig::normalizeAll);
        aliasParameters.values().removeIf(List::isEmpty);
    }

    private static void normalizeAll(List<Parameter> parameters) {
        parameters.removeIf(Objects::isNull);
        parameters.forEach(Parameter::normalize);
        parameters.removeIf(parameter -> AliasParameters.nameProblem(parameter.name) != null);

        Set<String> seen = new LinkedHashSet<>();
        parameters.removeIf(parameter -> !seen.add(parameter.name));
        if (parameters.size() > MAX_PARAMETERS) {
            parameters.subList(MAX_PARAMETERS, parameters.size()).clear();
        }
        // A greedy argument reads the rest of the line, so nothing can follow it, and an argument
        // that may be left out cannot be followed by one that may not.
        for (int i = 0; i < parameters.size() - 1; i++) {
            if (parameters.get(i).greedy()) {
                parameters.subList(i + 1, parameters.size()).clear();
                break;
            }
        }
        boolean optionalSeen = false;
        for (Parameter parameter : parameters) {
            if (parameter.optional) optionalSeen = true;
            else if (optionalSeen) parameter.optional = true;
        }
    }

    /** The arguments of an alias, in order; an empty list when it takes none. */
    public List<Parameter> parameters(String alias) {
        List<Parameter> declared = aliasParameters.get(alias);
        return declared == null ? List.of() : declared;
    }
}
