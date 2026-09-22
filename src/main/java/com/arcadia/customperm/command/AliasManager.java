/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.AliasParameters;
import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.config.AliasesConfig.Parameter;
import com.arcadia.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers admin-defined aliases. Each alias is a top-level literal that delegates
 * its execution to the underlying command(s) via the server command dispatcher.
 *
 * Two entry points:
 *   - {@link #registerAll(CommandDispatcher)} : at server start / RegisterCommandsEvent,
 *     registers every alias from config.
 *   - {@link #registerOrReplace(CommandDispatcher, String)} : at runtime (admin uses
 *     /customperm alias add|addstep|removestep|remove), re-registers a single alias
 *     so the change takes effect without requiring /reload.
 *
 * Permission node for an alias {@code foo} is {@code customperm.alias.foo}.
 */
public class AliasManager {

    private static final Field CHILDREN_FIELD;
    private static final Field LITERALS_FIELD;
    private static final Field ARGUMENTS_FIELD;
    private static final Map<String, CommandNode<CommandSourceStack>> SHADOWED_ORIGINALS = new HashMap<>();
    private static final Set<String> REGISTERED_ALIASES = new HashSet<>();

    /**
     * Maximum nesting depth of aliases. Without this guard, an alias that calls itself (or a
     * cycle a to b to a) recurses forever on an op-4 elevated source until StackOverflowError.
     */
    static final int MAX_ALIAS_DEPTH = 8;
    private static final ThreadLocal<Integer> ALIAS_DEPTH = ThreadLocal.withInitial(() -> 0);

    static {
        try {
            CHILDREN_FIELD = CommandNode.class.getDeclaredField("children");
            CHILDREN_FIELD.setAccessible(true);
            LITERALS_FIELD = CommandNode.class.getDeclaredField("literals");
            LITERALS_FIELD.setAccessible(true);
            ARGUMENTS_FIELD = CommandNode.class.getDeclaredField("arguments");
            ARGUMENTS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Brigadier API changed: CommandNode internal maps not found", e);
        }
    }

    public static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        AliasesConfig cfg = CustomPerm.configManager.getAliases();
        for (String name : new HashSet<>(cfg.aliases.keySet())) {
            registerOrReplace(dispatcher, name);
        }
    }

    /**
     * Brings the live dispatcher in step with the current config on a hot-reload.
     * Covers the three cases a plain registerAll would miss after aliases.json was edited:
     * aliases added to the file, aliases removed (restoring the shadowed node), and edited
     * steps. The execution closure captures the step list at registration time, so a
     * re-register is what makes it point at the new steps.
     */
    public static void applyConfig(CommandDispatcher<CommandSourceStack> dispatcher) {
        Set<String> names = new HashSet<>(CustomPerm.configManager.getAliases().aliases.keySet());
        names.addAll(REGISTERED_ALIASES);
        for (String name : names) {
            registerOrReplace(dispatcher, name);
        }
    }

    /**
     * Clears the static state tied to the current dispatcher. Call it when the dispatcher is
     * replaced (RegisterCommandsEvent: a /reload, a restart) or destroyed (server stop):
     * otherwise REGISTERED_ALIASES and SHADOWED_ORIGINALS keep nodes of the old tree alive
     * (a memory leak) and deleting an alias would restore a stale node.
     */
    /** Whether the live alias of this name replaced a real command, which comes back when the alias is deleted. */
    public static boolean shadowsCommand(String aliasName) {
        return SHADOWED_ORIGINALS.containsKey(aliasName);
    }

    public static void clearServerState() {
        SHADOWED_ORIGINALS.clear();
        REGISTERED_ALIASES.clear();
    }

    /**
     * Re-registers a single alias on the live dispatcher: removes any existing node
     * with that name, then re-adds it from the current config (or skips if the alias
     * was just deleted from config).
     */
    public static void registerOrReplace(CommandDispatcher<CommandSourceStack> dispatcher, String aliasName) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        CommandNode<CommandSourceStack> existing = root.getChild(aliasName);

        List<String> steps = CustomPerm.configManager.getAliases().aliases.get(aliasName);
        // An alias limited to other cluster members is not registered here, and a command it shadowed comes back.
        if (CustomPerm.configManager.getAliases().activeHere(aliasName, com.arcadia.customperm.cluster.Cluster.identity())) {
            if (existing != null
                    && !REGISTERED_ALIASES.contains(aliasName)
                    && !SHADOWED_ORIGINALS.containsKey(aliasName)) {
                SHADOWED_ORIGINALS.put(aliasName, existing);
            }
            removeFromRoot(root, aliasName);
            registerOne(dispatcher, aliasName, steps);
            REGISTERED_ALIASES.add(aliasName);
        } else {
            if (REGISTERED_ALIASES.remove(aliasName)) {
                removeFromRoot(root, aliasName);
            }
            CommandNode<CommandSourceStack> original = SHADOWED_ORIGINALS.remove(aliasName);
            if (original != null) {
                putInRoot(root, aliasName, original);
            }
        }
    }

    private static void registerOne(CommandDispatcher<CommandSourceStack> dispatcher, String alias, List<String> steps) {
        if (steps == null || steps.isEmpty()) return;
        String permNode = "customperm.alias." + alias;
        List<Parameter> parameters = CustomPerm.configManager.getAliases().parameters(alias);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(alias)
                // Explicit node first, operators included; op level 2 only when the node is not set.
                .requires(src -> PermissionService.get().hasPermission(src, permNode));

        // Built from the last argument back, each node executing the alias when everything after it
        // may be left out. Optional arguments are a suffix of the list, so that is the next one.
        ArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = parameters.size() - 1; i >= 0; i--) {
            Parameter parameter = parameters.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> node =
                    Commands.argument(parameter.name, argumentType(parameter));
            if (!parameter.choices.isEmpty()) {
                List<String> choices = List.copyOf(parameter.choices);
                node.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(choices, builder));
            }
            if (i == parameters.size() - 1 || parameters.get(i + 1).optional) {
                node.executes(ctx -> run(ctx, alias, steps, parameters));
            }
            if (tail != null) node.then(tail);
            tail = node;
        }
        if (tail != null) root.then(tail);
        if (parameters.isEmpty() || parameters.get(0).optional) {
            root.executes(ctx -> run(ctx, alias, steps, parameters));
        }

        dispatcher.register(root);
    }

    private static ArgumentType<?> argumentType(Parameter parameter) {
        return switch (parameter.type) {
            case AliasesConfig.TYPE_PLAYER -> EntityArgument.player();
            case AliasesConfig.TYPE_INTEGER -> IntegerArgumentType.integer(
                    parameter.min == null ? Integer.MIN_VALUE : parameter.min,
                    parameter.max == null ? Integer.MAX_VALUE : parameter.max);
            case AliasesConfig.TYPE_TEXT -> StringArgumentType.greedyString();
            default -> StringArgumentType.word();
        };
    }

    /** Rate limit, then the argument values, then the steps. */
    private static int run(CommandContext<CommandSourceStack> ctx, String alias, List<String> steps,
                           List<Parameter> parameters) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!RateLimits.acquire(source, alias)) return 0;

        Map<String, String> values = new HashMap<>();
        for (Parameter parameter : parameters) {
            String value = read(ctx, parameter);
            String problem = AliasParameters.valueProblem(parameter, value);
            if (problem != null) {
                source.sendFailure(Component.literal("[CustomPerm] /" + alias + ": " + problem));
                return 0;
            }
            values.put(parameter.name, value);
        }
        return executeAlias(source, alias, steps, values);
    }

    /** The value typed for this argument, or what it falls back to when it was left out. */
    private static String read(CommandContext<CommandSourceStack> ctx, Parameter parameter)
            throws CommandSyntaxException {
        if (!provided(ctx, parameter.name)) return parameter.fallback();
        return switch (parameter.type) {
            // Resolved through the source, so what reaches a step is one online player's name and
            // never the selector that found them.
            case AliasesConfig.TYPE_PLAYER -> EntityArgument.getPlayer(ctx, parameter.name).getGameProfile().getName();
            case AliasesConfig.TYPE_INTEGER -> String.valueOf(IntegerArgumentType.getInteger(ctx, parameter.name));
            default -> StringArgumentType.getString(ctx, parameter.name);
        };
    }

    /** Whether the player typed this argument; the optional ones at the end may be missing. */
    private static boolean provided(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (name.equals(node.getNode().getName())) return true;
        }
        return false;
    }

    static int executeAlias(CommandSourceStack source, String alias, List<String> steps,
                            Map<String, String> values) {
        var server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias + " failed: no server context."));
            return 0;
        }

        int depth = ALIAS_DEPTH.get();
        if (depth >= MAX_ALIAS_DEPTH) {
            source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias
                + " aborted: nested alias depth exceeds " + MAX_ALIAS_DEPTH + " (recursive alias chain?)."));
            CustomPerm.LOGGER.warn("[CustomPerm] alias /{} aborted at depth {} — recursive alias chain detected.", alias, depth);
            return 0;
        }
        ALIAS_DEPTH.set(depth + 1);
        try {
            // Elevate to op-level 4 so steps that delegate to op-only commands work.
            var elevated = source.withPermission(4);
            int executed = 0;
            for (String step : steps) {
                // Substituted before the slash is stripped, so an argument may carry the whole step.
                String command = normalizeStep(AliasParameters.substitute(step, values));
                if (command.isEmpty()) continue;

                try {
                    if (!CommandTreeRewriter.executeOriginalCommand(elevated, command)) {
                        server.getCommands().getDispatcher().execute(command, elevated);
                    }
                    executed++;
                } catch (CommandSyntaxException e) {
                    source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias
                        + " step failed: " + command + " (" + e.getMessage() + ")"));
                    CustomPerm.LOGGER.warn("[CustomPerm] alias /{} step `{}` failed: {}", alias, command, e.getMessage());
                } catch (Throwable t) {
                    // D1: never swallow a fatal JVM error (OOM, SOE), the same rule as LuckPermsService.
                    if (t instanceof Error e) throw e;
                    source.sendFailure(Component.literal("[CustomPerm] Alias /" + alias
                        + " step threw: " + command + " (" + t.getClass().getSimpleName() + ")"));
                    CustomPerm.LOGGER.warn("[CustomPerm] alias /{} step `{}` threw", alias, command, t);
                }
            }
            return executed;
        } finally {
            ALIAS_DEPTH.set(depth);
        }
    }

    static String normalizeStep(String step) {
        if (step == null) return "";

        String command = step.strip();
        // One optional slash only: stripping more would break commands whose root literal
        // starts with "/", WorldEdit style (step "//wand" means the "/wand" literal).
        if (command.startsWith("/")) {
            command = command.substring(1).strip();
        }
        return command;
    }

    @SuppressWarnings("unchecked")
    private static void removeFromRoot(CommandNode<CommandSourceStack> root, String name) {
        try {
            Map<String, CommandNode<CommandSourceStack>> children =
                (Map<String, CommandNode<CommandSourceStack>>) CHILDREN_FIELD.get(root);
            Map<String, LiteralCommandNode<CommandSourceStack>> literals =
                (Map<String, LiteralCommandNode<CommandSourceStack>>) LITERALS_FIELD.get(root);
            Map<String, ArgumentCommandNode<CommandSourceStack, ?>> arguments =
                (Map<String, ArgumentCommandNode<CommandSourceStack, ?>>) ARGUMENTS_FIELD.get(root);

            children.remove(name);
            literals.remove(name);
            arguments.remove(name);
        } catch (IllegalAccessException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not remove alias /{} from dispatcher: {}", name, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void putInRoot(CommandNode<CommandSourceStack> root, String name, CommandNode<CommandSourceStack> node) {
        try {
            Map<String, CommandNode<CommandSourceStack>> children =
                (Map<String, CommandNode<CommandSourceStack>>) CHILDREN_FIELD.get(root);
            Map<String, LiteralCommandNode<CommandSourceStack>> literals =
                (Map<String, LiteralCommandNode<CommandSourceStack>>) LITERALS_FIELD.get(root);
            Map<String, ArgumentCommandNode<CommandSourceStack, ?>> arguments =
                (Map<String, ArgumentCommandNode<CommandSourceStack, ?>>) ARGUMENTS_FIELD.get(root);

            children.put(name, node);
            if (node instanceof LiteralCommandNode<CommandSourceStack> literal) {
                literals.put(name, literal);
                arguments.remove(name);
            } else if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
                arguments.put(name, argument);
                literals.remove(name);
            }
        } catch (IllegalAccessException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not restore shadowed command /{}: {}", name, e.getMessage());
        }
    }
}
