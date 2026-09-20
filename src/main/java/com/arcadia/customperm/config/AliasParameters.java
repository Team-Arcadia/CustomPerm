/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import com.arcadia.customperm.config.AliasesConfig.Parameter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules an alias argument follows: what a name may be, what a value may be, and how a step
 * reaches it.
 *
 * <p>A step reaches an argument through <code>${name}</code>. The form is deliberate: no command
 * syntax produces a dollar sign followed by a brace, so the NBT and JSON braces that fill real
 * commands ({@code give @s diamond_sword{Enchantments:[]}}) are never mistaken for an argument.
 * Anything that is not a declared name is left exactly as it was typed.
 *
 * <p>Pure Java, no Minecraft class: the Brigadier side lives in {@code AliasManager}.
 */
public final class AliasParameters {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,15}");
    private static final String OPEN = "${";

    private AliasParameters() {
    }

    /** Why this argument name is refused, or null when it is fine. */
    public static String nameProblem(String name) {
        if (name == null || name.isBlank()) return "An argument needs a name.";
        if (!NAME.matcher(name).matches()) {
            return "Invalid argument name '" + name
                    + "': start with a letter, then 1 to 16 lowercase letters, digits or _.";
        }
        return null;
    }

    /** Why this argument type is refused, or null when it is one of the four. */
    public static String typeProblem(String type) {
        if (type == null || !AliasesConfig.TYPES.contains(type)) {
            return "Unknown argument type '" + type + "': use " + String.join(", ", AliasesConfig.TYPES) + ".";
        }
        return null;
    }

    /**
     * Why adding {@code candidate} at the end of {@code declared} is refused, or null when it fits.
     * The ordering rules are Brigadier's: a greedy argument reads the rest of the line so nothing can
     * follow it, and an argument that may be left out cannot be followed by one that may not.
     */
    public static String appendProblem(List<Parameter> declared, Parameter candidate) {
        String problem = nameProblem(candidate.name);
        if (problem != null) return problem;
        problem = typeProblem(candidate.type);
        if (problem != null) return problem;
        if (declared.size() >= AliasesConfig.MAX_PARAMETERS) {
            return "Too many arguments (limit " + AliasesConfig.MAX_PARAMETERS + ").";
        }
        for (Parameter existing : declared) {
            if (existing.name.equals(candidate.name)) {
                return "Argument '" + candidate.name + "' is already declared.";
            }
        }
        if (!declared.isEmpty()) {
            Parameter last = declared.get(declared.size() - 1);
            if (last.greedy()) {
                return "Argument '" + last.name + "' is text, which reads the rest of the line, so nothing can follow it.";
            }
            if (last.optional && !candidate.optional) {
                return "Argument '" + last.name + "' may be left out, so '" + candidate.name
                        + "' may not be required. Make it optional, or move it first.";
            }
        }
        return null;
    }

    /**
     * Why this order of arguments is refused, or null when it holds. Used by the move and the
     * optional switch, which can break the rules an append already checks.
     */
    public static String orderProblem(List<Parameter> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            if (parameter.greedy() && i != parameters.size() - 1) {
                return "Argument '" + parameter.name + "' is text, which reads the rest of the line, so it must come last.";
            }
            if (!parameter.optional && i > 0 && parameters.get(i - 1).optional) {
                return "Argument '" + parameter.name + "' is required but follows '" + parameters.get(i - 1).name
                        + "', which may be left out.";
            }
        }
        return null;
    }

    /**
     * Why a player typing {@code value} for this argument is refused, or null when it is accepted.
     * The type itself already rules out most of it, Brigadier refusing a word with a space and a
     * number outside its range before this is reached; what is left is the declared choices and the
     * selector guard.
     */
    public static String valueProblem(Parameter parameter, String value) {
        if (value == null) return "Missing value for '" + parameter.name + "'.";
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "A line break cannot be part of '" + parameter.name + "'.";
        }
        if (!parameter.choices.isEmpty() && !parameter.choices.contains(value)) {
            return "'" + value + "' is not one of the accepted values for '" + parameter.name + "': "
                    + String.join(", ", parameter.choices) + ".";
        }
        if (!parameter.allowSelectors && holdsSelector(value)) {
            return "'" + parameter.name + "' does not take an entity selector. The steps of an alias run at op"
                    + " level 4, so a selector there would reach more than the player it names.";
        }
        return null;
    }

    /**
     * Whether the value carries what Minecraft would read as an entity selector: an {@code @}
     * starting a token and followed by a letter. A mail address or a chat mention of the form
     * {@code hello@there} is not one, so ordinary text stays usable.
     */
    public static boolean holdsSelector(String value) {
        for (int i = 0; i < value.length() - 1; i++) {
            if (value.charAt(i) != '@') continue;
            if (i > 0 && !Character.isWhitespace(value.charAt(i - 1))) continue;
            if (Character.isLetter(value.charAt(i + 1))) return true;
        }
        return false;
    }

    /**
     * The step with every <code>${name}</code> of {@code values} replaced by its value. A name that
     * is not in the map is left as it was typed, so a step written before its argument was declared
     * is not quietly mangled.
     */
    public static String substitute(String step, Map<String, String> values) {
        if (step == null || step.isEmpty() || values.isEmpty()) return step;
        int open = step.indexOf(OPEN);
        if (open < 0) return step;

        StringBuilder out = new StringBuilder(step.length() + 16);
        int from = 0;
        while (open >= 0) {
            int close = step.indexOf('}', open + OPEN.length());
            if (close < 0) break;
            String name = step.substring(open + OPEN.length(), close);
            String value = values.get(name);
            if (value == null) {
                // Not an argument of this alias: copy the whole thing, braces included.
                out.append(step, from, close + 1);
            } else {
                out.append(step, from, open).append(value);
            }
            from = close + 1;
            open = step.indexOf(OPEN, from);
        }
        return out.append(step, from, step.length()).toString();
    }

    /** Every <code>${name}</code> a step refers to, in the order they appear. */
    public static Set<String> placeholders(String step) {
        Set<String> names = new LinkedHashSet<>();
        if (step == null) return names;
        int open = step.indexOf(OPEN);
        while (open >= 0) {
            int close = step.indexOf('}', open + OPEN.length());
            if (close < 0) break;
            String name = step.substring(open + OPEN.length(), close);
            if (nameProblem(name) == null) names.add(name);
            open = step.indexOf(OPEN, close + 1);
        }
        return names;
    }

    /** The arguments as tab completion shows them: {@code <player> [count]}. */
    public static String usage(List<Parameter> parameters) {
        StringBuilder usage = new StringBuilder();
        for (Parameter parameter : parameters) {
            if (usage.length() > 0) usage.append(' ');
            usage.append(parameter.optional ? '[' : '<').append(parameter.name).append(parameter.optional ? ']' : '>');
        }
        return usage.toString();
    }

    /** One line describing an argument, for listings and the interface. */
    public static String describe(Parameter parameter) {
        StringBuilder line = new StringBuilder();
        line.append(parameter.optional ? "[" : "<").append(parameter.name).append(parameter.optional ? "]" : ">");
        line.append("  ").append(parameter.type);
        if (AliasesConfig.TYPE_INTEGER.equals(parameter.type) && (parameter.min != null || parameter.max != null)) {
            line.append(' ').append(parameter.min == null ? "" : parameter.min)
                    .append("..").append(parameter.max == null ? "" : parameter.max);
        }
        if (!parameter.choices.isEmpty()) line.append(" of ").append(String.join("|", parameter.choices));
        if (parameter.defaultValue != null) line.append(", default ").append(parameter.defaultValue);
        if (parameter.allowSelectors) line.append(", selectors allowed");
        return line.toString();
    }
}
