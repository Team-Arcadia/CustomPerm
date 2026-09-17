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
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        AdminResult result = AdminResult.ok("Appended step #" + (steps.size() - 1) + " to /" + name + ": " + cmd).warn(warning);
        return shadows ? result.warn(shadowWarning(name)) : result;
    }

    public static AdminResult removeStep(MinecraftServer server, String name, int index) {
        List<String> steps = aliases().get(name);
        if (steps == null) return AdminResult.fail("No such alias: " + name);
        if (index < 0 || index >= steps.size()) {
            return AdminResult.fail("Index out of range (0.." + (steps.size() - 1) + ")");
        }
        String removed = steps.remove(index);
        if (steps.isEmpty()) aliases().remove(name);
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        return AdminResult.ok("Removed step #" + index + " from /" + name + ": " + removed).warn(warning);
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
        return AdminResult.ok("Replaced step #" + index + " of /" + name + ": " + cmd).warn(warning);
    }

    public static AdminResult remove(MinecraftServer server, String name) {
        if (aliases().remove(name) == null) return AdminResult.fail("No such alias: " + name);
        String warning = ConfigAdmin.persist();
        refresh(server, name);
        return AdminResult.ok("Removed alias /" + name).warn(warning);
    }

    private static String shadowWarning(String name) {
        CustomPerm.LOGGER.warn("[CustomPerm] Alias '{}' shadows vanilla command '{}'", name, name);
        return "WARNING: /" + name + " shadows an existing command. Players will need `customperm.alias." + name
                + "` (not the command's own perm) to use it.";
    }

    /** Re-registers the alias live, restores a command it stopped shadowing, and resends command trees. */
    private static void refresh(MinecraftServer server, String name) {
        if (server != null) {
            AliasManager.registerOrReplace(server.getCommands().getDispatcher(), name);
            // A removed alias may have restored a shadowed command, which must be wrapped again at
            // once or its customperm.command.* node stays inert until the next reload.
            CommandTreeRewriter.repair(server);
        }
        ConfigAdmin.resyncCommands(server);
    }
}
