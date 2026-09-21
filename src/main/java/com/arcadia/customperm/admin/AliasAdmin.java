/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.command.AliasManager;
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.arcadia.customperm.config.AliasParameters;
import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.config.AliasesConfig.Parameter;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aliases: named macros whose steps run at op level 4 for holders of {@code customperm.alias.<name>}.
 * Shared by {@code /customperm alias} and the admin interface, server thread only. Step indexes are
 * 0-based, like the text commands.
 */
public final class AliasAdmin {

    /** Same characters Brigadier's {@code word()} accepts, so an alias made here can be typed. */
    private static final Pattern NAME = Pattern.compile("[0-9A-Za-z_\\-.+]{1,64}");
    public static final int MAX_STEPS = 64;

    private AliasAdmin() {
    }

    private static Map<String, List<String>> aliases() {
        return CustomPerm.configManager.getAliases().aliases;
    }

    /** The arguments of an alias, read only. */
    public static List<Parameter> parameters(String name) {
        return CustomPerm.configManager.getAliases().parameters(name);
    }

    /** The arguments of an alias, created on first use so a caller can add to them. */
    private static List<Parameter> editable(String name) {
        return CustomPerm.configManager.getAliases().aliasParameters
                .computeIfAbsent(name, key -> new ArrayList<>());
    }

    private static Parameter find(String alias, String parameter) {
        for (Parameter declared : parameters(alias)) {
            if (declared.name.equals(parameter)) return declared;
        }
        return null;
    }

    /** Whether a live root command other than an alias already uses this name. */
    private static boolean shadowsCommand(MinecraftServer server, String name) {
        return !aliases().containsKey(name) && CommandAdmin.existsInDispatcher(server, name);
    }

    private static String nameProblem(String name) {
        if (name.equals("customperm")) return "Reserved name.";
        if (!NAME.matcher(name).matches()) {
            return "Invalid alias name '" + name + "': use 1 to 64 letters, digits, _ - . or +.";
        }
        return null;
    }

    /** {@code /customperm alias add}: defines an alias, replacing its steps when it exists. */
    public static AdminResult define(MinecraftServer server, String name, List<String> rawSteps) {
        String problem = nameProblem(name);
        if (problem != null) return AdminResult.fail(problem);
        List<String> steps = rawSteps.stream().map(String::trim).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (steps.isEmpty()) return AdminResult.fail("No commands provided.");
        if (steps.size() > MAX_STEPS) return AdminResult.fail("Too many steps (limit " + MAX_STEPS + ").");

        boolean shadows = shadowsCommand(server, name);
        aliases().put(name, steps);
        String warning = ConfigAdmin.persist();
        refresh(server, name);

        AdminResult result = AdminResult.ok("Alias /" + name + " set with " + steps.size() + " step(s).").warn(warning);
        if (shadows) result = result.warn(shadowWarning(name));
        result = result.warn(placeholderWarning(name));
        List<Parameter> parameters = parameters(name);
        if (!parameters.isEmpty()) {
            result = result.note("  Usage: /" + name + " " + AliasParameters.usage(parameters));
        }
        return result.note("  Permission node: customperm.alias." + name
                + "  |  Steps run with op-level 4 — only grant to trusted users.");
    }

    /** Interface creation: like {@link #define} with one step, but never replaces an existing alias. */
    public static AdminResult create(MinecraftServer server, String name, String firstStep) {
        if (aliases().containsKey(name)) return AdminResult.fail("Alias /" + name + " already exists.");
        return define(server, name, List.of(firstStep));
    }

    public static AdminResult addStep(MinecraftServer server, String name, String command) {
        String problem = nameProblem(name);
        if (problem != null) return AdminResult.fail(problem);
        String cmd = command.trim();
        if (cmd.isEmpty()) return AdminResult.fail("Empty command.");
        List<String> existing = aliases().get(name);
        if (existing != null && existing.size() >= MAX_STEPS) {
            return AdminResult.fail("Too many steps (limit " + MAX_STEPS + ").");
        }
        boolean shadows = shadowsCommand(server, name);
        List<String> steps = aliases().computeIfAbsent(name, k -> new ArrayList<>());
        steps.add(cmd);
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        AdminResult result = AdminResult.ok("Appended step #" + (steps.size() - 1) + " to /" + name + ": " + cmd)
                .warn(warning).warn(placeholderWarning(name));
        return shadows ? result.warn(shadowWarning(name)) : result;
    }

    public static AdminResult removeStep(MinecraftServer server, String name, int index) {
        List<String> steps = aliases().get(name);
        if (steps == null) return AdminResult.fail("No such alias: " + name);
        if (index < 0 || index >= steps.size()) {
            return AdminResult.fail("Index out of range (0.." + (steps.size() - 1) + ")");
        }
        String removed = steps.remove(index);
        if (steps.isEmpty()) {
            aliases().remove(name);
            // The alias is gone with its last step, and arguments of a gone alias reach nothing.
            CustomPerm.configManager.getAliases().aliasParameters.remove(name);
        }
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        return AdminResult.ok("Removed step #" + index + " from /" + name + ": " + removed)
                .warn(warning).warn(placeholderWarning(name));
    }

    public static AdminResult moveStep(MinecraftServer server, String name, int from, int to) {
        List<String> steps = aliases().get(name);
        if (steps == null) return AdminResult.fail("No such alias: " + name);
        if (from < 0 || from >= steps.size() || to < 0 || to >= steps.size()) {
            return AdminResult.fail("Index out of range (0.." + (steps.size() - 1) + ")");
        }
        if (from == to) return AdminResult.ok("Step #" + from + " of /" + name + " is already there. No change.");
        steps.add(to, steps.remove(from));
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        return AdminResult.ok("Moved step #" + from + " of /" + name + " to #" + to + ".").warn(warning);
    }

    public static AdminResult setStep(MinecraftServer server, String name, int index, String command) {
        List<String> steps = aliases().get(name);
        if (steps == null) return AdminResult.fail("No such alias: " + name);
        if (index < 0 || index >= steps.size()) {
            return AdminResult.fail("Index out of range (0.." + (steps.size() - 1) + ")");
        }
        String cmd = command.trim();
        if (cmd.isEmpty()) return AdminResult.fail("Empty command.");
        steps.set(index, cmd);
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        return AdminResult.ok("Replaced step #" + index + " of /" + name + ": " + cmd)
                .warn(warning).warn(placeholderWarning(name));
    }

    public static AdminResult remove(MinecraftServer server, String name) {
        if (aliases().remove(name) == null) return AdminResult.fail("No such alias: " + name);
        CustomPerm.configManager.getAliases().aliasParameters.remove(name);
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        return AdminResult.ok("Removed alias /" + name).warn(warning);
    }

    // ---------------- arguments ----------------

    /** Declares an argument at the end of the alias's list. */
    public static AdminResult addParameter(MinecraftServer server, String alias, String name, String type) {
        if (!aliases().containsKey(alias)) return AdminResult.fail("No such alias: " + alias);
        Parameter candidate = new Parameter(name == null ? null : name.trim().toLowerCase(java.util.Locale.ROOT),
                type == null ? null : type.trim().toLowerCase(java.util.Locale.ROOT));
        candidate.normalize();
        String problem = AliasParameters.appendProblem(parameters(alias), candidate);
        if (problem != null) return AdminResult.fail(problem);

        editable(alias).add(candidate);
        return applied(server, alias, "/" + alias + " now takes " + AliasParameters.describe(candidate) + ".");
    }

    public static AdminResult removeParameter(MinecraftServer server, String alias, String name) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        editable(alias).remove(declared);
        return applied(server, alias, "Removed argument '" + name + "' from /" + alias + ".");
    }

    /** Moves an argument to a position, 0-based, the other arguments closing the gap. */
    public static AdminResult moveParameter(MinecraftServer server, String alias, String name, int index) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        List<Parameter> parameters = editable(alias);
        if (index < 0 || index >= parameters.size()) {
            return AdminResult.fail("Index out of range (0.." + (parameters.size() - 1) + ")");
        }
        int from = parameters.indexOf(declared);
        if (from == index) {
            return AdminResult.ok("Argument '" + name + "' of /" + alias + " is already there. No change.");
        }
        parameters.remove(from);
        parameters.add(index, declared);
        String problem = AliasParameters.orderProblem(parameters);
        if (problem != null) {
            parameters.remove(index);
            parameters.add(from, declared);
            return AdminResult.fail(problem);
        }
        return applied(server, alias, "Moved argument '" + name + "' of /" + alias + " to #" + index + ".");
    }

    /** Whether the argument may be left out. An optional one carries a default, even an empty one. */
    public static AdminResult setOptional(MinecraftServer server, String alias, String name, boolean optional) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        if (declared.optional == optional) {
            return AdminResult.ok("Argument '" + name + "' of /" + alias + " is already "
                    + (optional ? "optional" : "required") + ". No change.");
        }
        boolean before = declared.optional;
        declared.optional = optional;
        String problem = AliasParameters.orderProblem(editable(alias));
        if (problem != null) {
            declared.optional = before;
            return AdminResult.fail(problem);
        }
        if (!optional && declared.defaultValue != null) declared.defaultValue = null;
        return applied(server, alias, "Argument '" + name + "' of /" + alias + " is now "
                + (optional ? "optional" : "required") + ".");
    }

    /** The value a left-out argument is given. A null value clears it, leaving an empty substitution. */
    public static AdminResult setDefault(MinecraftServer server, String alias, String name, String value) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        if (value == null || value.isEmpty()) {
            declared.defaultValue = null;
            return applied(server, alias, "Argument '" + name + "' of /" + alias
                    + " has no default; left out, it substitutes nothing.");
        }
        String problem = AliasParameters.valueProblem(declared, value);
        if (problem != null) return AdminResult.fail(problem);
        if (AliasesConfig.TYPE_INTEGER.equals(declared.type)) {
            Integer parsed = number(value);
            if (parsed == null) return AdminResult.fail("Argument '" + name + "' is an integer: '" + value + "' is not.");
            if (declared.min != null && parsed < declared.min || declared.max != null && parsed > declared.max) {
                return AdminResult.fail("Default " + value + " is outside the range of '" + name + "'.");
            }
        }
        declared.defaultValue = value;
        // A default is only reachable when the argument may be left out.
        boolean wasRequired = !declared.optional;
        declared.optional = true;
        String problem2 = AliasParameters.orderProblem(editable(alias));
        if (problem2 != null) {
            declared.optional = !wasRequired;
            declared.defaultValue = null;
            return AdminResult.fail(problem2);
        }
        AdminResult result = applied(server, alias,
                "Argument '" + name + "' of /" + alias + " defaults to " + value + ".");
        return wasRequired ? result.note("  It may now be left out, a default being what makes that useful.") : result;
    }

    /** The bounds an integer argument accepts; a null bound is no bound. */
    public static AdminResult setRange(MinecraftServer server, String alias, String name, Integer min, Integer max) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        if (!AliasesConfig.TYPE_INTEGER.equals(declared.type)) {
            return AdminResult.fail("Only an integer argument takes a range; '" + name + "' is " + declared.type + ".");
        }
        if (min != null && max != null && min > max) return AdminResult.fail("The lowest value is above the highest.");
        declared.min = min;
        declared.max = max;
        if (declared.defaultValue != null) {
            Integer parsed = number(declared.defaultValue);
            if (parsed != null && (min != null && parsed < min || max != null && parsed > max)) {
                declared.defaultValue = null;
            }
        }
        return applied(server, alias, "Argument '" + name + "' of /" + alias + " accepts "
                + (min == null ? "any value" : "at least " + min)
                + (max == null ? "" : ", at most " + max) + ".");
    }

    /** What a word argument suggests, and the only values it then accepts. An empty list clears both. */
    public static AdminResult setChoices(MinecraftServer server, String alias, String name, List<String> choices) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        if (!AliasesConfig.TYPE_WORD.equals(declared.type)) {
            return AdminResult.fail("Only a word argument takes choices; '" + name + "' is " + declared.type + ".");
        }
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(choices.stream()
                .map(String::trim).filter(choice -> !choice.isEmpty()).toList()));
        if (clean.size() > AliasesConfig.MAX_CHOICES) {
            return AdminResult.fail("Too many choices (limit " + AliasesConfig.MAX_CHOICES + ").");
        }
        for (String choice : clean) {
            if (choice.indexOf(' ') >= 0) return AdminResult.fail("A word argument's choice cannot hold a space: '" + choice + "'.");
        }
        declared.choices = clean;
        if (declared.defaultValue != null && !clean.isEmpty() && !clean.contains(declared.defaultValue)) {
            declared.defaultValue = null;
        }
        return applied(server, alias, clean.isEmpty()
                ? "Argument '" + name + "' of /" + alias + " accepts any word again."
                : "Argument '" + name + "' of /" + alias + " accepts " + String.join(", ", clean) + ".");
    }

    /** Whether an entity selector is accepted in this argument's value. */
    public static AdminResult setSelectors(MinecraftServer server, String alias, String name, boolean allowed) {
        Parameter declared = find(alias, name);
        if (declared == null) return AdminResult.fail("/" + alias + " has no argument '" + name + "'.");
        if (!AliasesConfig.TYPE_TEXT.equals(declared.type)) {
            return AdminResult.fail("Only a text argument can carry a selector; '" + name + "' is "
                    + declared.type + ", which cannot hold one.");
        }
        declared.allowSelectors = allowed;
        AdminResult result = applied(server, alias, "Argument '" + name + "' of /" + alias
                + (allowed ? " accepts an entity selector." : " no longer accepts an entity selector."));
        return allowed
                ? result.warn("A step runs at op level 4, so a selector typed here reaches whatever it names.")
                : result;
    }

    private static Integer number(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Saves, re-registers the alias so the new argument chain is typeable at once, and reports. */
    private static AdminResult applied(MinecraftServer server, String alias, String message) {
        CustomPerm.configManager.getAliases().normalize();
        String warning = ConfigAdmin.persist();
        refresh(server, alias);
        AdminResult result = AdminResult.ok(message).warn(warning).warn(placeholderWarning(alias));
        List<Parameter> parameters = parameters(alias);
        return parameters.isEmpty() ? result
                : result.note("  Usage: /" + alias + " " + AliasParameters.usage(parameters));
    }

    /**
     * Arguments a step names but nothing declares, and arguments no step uses. Both are typos more
     * often than intent, and neither can be refused outright: the step and the argument are declared
     * in either order.
     */
    static String placeholderWarning(String alias) {
        Set<String> declared = new LinkedHashSet<>();
        for (Parameter parameter : parameters(alias)) declared.add(parameter.name);
        Set<String> used = new LinkedHashSet<>();
        for (String step : aliases().getOrDefault(alias, List.of())) used.addAll(AliasParameters.placeholders(step));

        List<String> unknown = used.stream().filter(name -> !declared.contains(name)).toList();
        List<String> unused = declared.stream().filter(name -> !used.contains(name)).toList();
        StringBuilder warning = new StringBuilder();
        if (!unknown.isEmpty()) {
            warning.append("Steps of /").append(alias).append(" use ${").append(String.join("}, ${", unknown))
                    .append("}, which /").append(alias).append(" does not take: left as typed.");
        }
        if (!unused.isEmpty()) {
            if (warning.length() > 0) warning.append(' ');
            warning.append("/").append(alias).append(" takes ").append(String.join(", ", unused))
                    .append(", which no step uses.");
        }
        return warning.length() == 0 ? null : warning.toString();
    }

    private static String shadowWarning(String name) {
        CustomPerm.LOGGER.warn("[CustomPerm] Alias '{}' shadows vanilla command '{}'", name, name);
        return "WARNING: /" + name + " shadows an existing command. Players will need `customperm.alias." + name
                + "` (not the command's own perm) to use it.";
    }

    /** Re-registers the alias live, restores a command it stopped shadowing, and resends command trees. */
    static void refresh(MinecraftServer server, String name) {
        if (server != null) {
            AliasManager.registerOrReplace(server.getCommands().getDispatcher(), name);
            // A removed alias may have restored a shadowed command, which must be wrapped again at
            // once or its customperm.command.* node stays inert until the next reload.
            CommandTreeRewriter.repair(server);
        }
        ConfigAdmin.resyncCommands(server);
    }
}
