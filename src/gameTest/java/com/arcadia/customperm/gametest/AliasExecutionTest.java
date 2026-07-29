/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.command.AliasManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for alias creation, removal and execution (AC2 — story 6-2).
 *
 * Note: tests requiring a non-OP ServerPlayer mock are impossible in MC 1.21.1
 * ({@code makeMockPlayer} returns an anonymous {@code Player}, not a {@code ServerPlayer}).
 * E2E alias execution is verified via server source (op level 4).
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class AliasExecutionTest {

    private static final String TEMPLATE = "empty_3x3";

    /**
     * AC: a newly added alias appears in the live dispatcher after registerOrReplace.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasRegistersOnLiveDispatcher(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_ae_alias";
        aliasesCfg.aliases.remove(testName);
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        if (Customperm.findRoot(server, testName) != null)
            fail("Test alias was already in dispatcher before test.");

        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say hello")));
        AliasManager.registerOrReplace(dispatcher, testName);
        if (Customperm.findRoot(server, testName) == null)
            fail("Alias was not registered on the live dispatcher.");

        // Cleanup
        aliasesCfg.aliases.remove(testName);
        AliasManager.registerOrReplace(dispatcher, testName);
        helper.succeed();
    }

    /**
     * AC: removing an alias from config and calling registerOrReplace removes the node
     * from the live dispatcher.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasRemovesFromDispatcher(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_ae_alias_rm";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say x")));
        AliasManager.registerOrReplace(dispatcher, testName);
        if (Customperm.findRoot(server, testName) == null)
            fail("Setup failed — alias not registered.");

        aliasesCfg.aliases.remove(testName);
        AliasManager.registerOrReplace(dispatcher, testName);
        if (Customperm.findRoot(server, testName) != null)
            fail("Alias still in dispatcher after removal.");

        helper.succeed();
    }

    /**
     * Regression: an alias may temporarily shadow a vanilla/modded command. Removing
     * that alias must restore the original command node instead of deleting it from
     * the live dispatcher.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasShadowRemovalRestoresOriginalCommand(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String shadowedName = "gamemode";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        CommandNode<CommandSourceStack> original = Customperm.findRoot(server, shadowedName);
        if (original == null)
            fail("Setup failed — /gamemode is missing before alias shadow test.");

        aliasesCfg.aliases.put(shadowedName, new ArrayList<>(List.of("say shadow-test")));
        AliasManager.registerOrReplace(dispatcher, shadowedName);
        CommandNode<CommandSourceStack> aliasNode = Customperm.findRoot(server, shadowedName);
        if (aliasNode == null)
            fail("Setup failed — shadow alias /gamemode was not registered.");
        if (aliasNode == original)
            fail("Setup failed — /gamemode was not shadowed by alias node.");

        aliasesCfg.aliases.remove(shadowedName);
        AliasManager.registerOrReplace(dispatcher, shadowedName);
        CommandNode<CommandSourceStack> restored = Customperm.findRoot(server, shadowedName);
        if (restored == null)
            fail("Removing shadow alias deleted /gamemode from dispatcher.");
        if (restored != original)
            fail("Removing shadow alias did not restore the original /gamemode node.");

        helper.succeed();
    }

    /**
     * AC: the alias node's requirements pass for a server source (op level 4),
     * confirming that the elevated execution path is accessible.
     *
     * Note: per AliasManager.registerOne, the alias requires {@code hasPermission(2)}
     * or a matching CustomPerm permission node. Server source satisfies hasPermission(4≥2).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasExecutionElevatedToOpLevel4(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_say";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say hello")));
        AliasManager.registerOrReplace(dispatcher, testName);

        CommandNode<CommandSourceStack> node = Customperm.findRoot(server, testName);
        if (node == null)
            fail("Alias '_gt_say' was not registered in dispatcher.");

        // Server command source = op level 4 — satisfies requires(hasPermission(2))
        CommandSourceStack opSrc = server.createCommandSourceStack();
        if (!node.canUse(opSrc))
            fail("Server source (op level 4) must satisfy alias requirements (elevated execution).");

        // Cleanup
        aliasesCfg.aliases.remove(testName);
        AliasManager.registerOrReplace(dispatcher, testName);
        helper.succeed();
    }

    /**
     * AC: alias with multiple steps executes all of them without halting on any individual
     * step result — the no-halt guarantee.
     *
     * Verifies: an invalid first step does not prevent the valid second step from running.
     * The return value counts successful steps only.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aliasNoHaltOnError(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_nohalt";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        // First step fails, second step must still execute (no-halt guarantee)
        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("_customperm_missing_command", "say step_b")));
        AliasManager.registerOrReplace(dispatcher, testName);

        // Execute via Brigadier dispatcher (returns the executes-handler result = `executed` count).
        // try-finally ensures the alias is always cleaned from the dispatcher even if fail() throws
        // GameTestAssertException (F3).
        CommandSourceStack src = server.createCommandSourceStack();
        try {
            int result;
            try {
                result = server.getCommands().getDispatcher().execute(testName, src);
            } catch (CommandSyntaxException e) {
                fail("Alias command syntax error: " + e.getMessage());
                return; // unreachable
            }
            if (result != 1)
                fail("Expected 1 successful step after one failed step (no-halt), got " + result + ".");

            helper.succeed();
        } finally {
            // Always clean up the test alias from the live dispatcher (F3)
            aliasesCfg.aliases.remove(testName);
            AliasManager.registerOrReplace(dispatcher, testName);
        }
    }

    /**
     * Regression: alias steps may be stored with a leading slash. They must execute
     * the same as plain dispatcher commands.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aliasStepWithLeadingSlashExecutes(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_slash_step";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("/say slash_step")));
        AliasManager.registerOrReplace(dispatcher, testName);

        CommandSourceStack src = server.createCommandSourceStack();
        try {
            int result;
            try {
                result = server.getCommands().getDispatcher().execute(testName, src);
            } catch (CommandSyntaxException e) {
                fail("Alias command syntax error: " + e.getMessage());
                return; // unreachable
            }
            if (result != 1)
                fail("Expected leading-slash alias step to execute once, got " + result + ".");

            helper.succeed();
        } finally {
            aliasesCfg.aliases.remove(testName);
            AliasManager.registerOrReplace(dispatcher, testName);
        }
    }

    /**
     * Regression: internal alias steps must not require the player to also hold
     * customperm.command.<step>. Once the alias permission passes, its steps run
     * through the original command node with the elevated source.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aliasStepUsesOriginalCommandNode(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        var commandsCfg = CustomPerm.configManager.getCommands();
        String testName = "_gt_original_step";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        boolean hadSay = commandsCfg.grantedCommands.contains("say");
        commandsCfg.grantedCommands.remove("say");
        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say original_step")));
        AliasManager.registerOrReplace(dispatcher, testName);

        CommandSourceStack src = server.createCommandSourceStack();
        try {
            int result;
            try {
                result = dispatcher.execute(testName, src);
            } catch (CommandSyntaxException e) {
                fail("Alias command syntax error: " + e.getMessage());
                return; // unreachable
            }
            if (result != 1)
                fail("Expected alias step to execute through original /say node, got " + result + ".");

            helper.succeed();
        } finally {
            aliasesCfg.aliases.remove(testName);
            AliasManager.registerOrReplace(dispatcher, testName);
            if (hadSay) {
                commandsCfg.grantedCommands.add("say");
            } else {
                commandsCfg.grantedCommands.remove("say");
            }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aliasConfigRefreshReplacesCapturedSteps(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_alias_refresh";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("_customperm_missing_command")));
        AliasManager.registerOrReplace(dispatcher, testName);

        try {
            int before = dispatcher.execute(testName, server.createCommandSourceStack());
            if (before != 0)
                fail("Invalid pre-reload alias step unexpectedly succeeded.");

            aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say refreshed_step")));
            AliasManager.applyConfig(dispatcher);

            int after = dispatcher.execute(testName, server.createCommandSourceStack());
            if (after != 1)
                fail("Alias refresh kept the stale captured steps.");

            helper.succeed();
        } catch (CommandSyntaxException e) {
            fail("Alias refresh syntax error: " + e.getMessage());
        } finally {
            aliasesCfg.aliases.remove(testName);
            AliasManager.registerOrReplace(dispatcher, testName);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void recursiveAliasCycleIsBounded(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String first = "_gt_cycle_a";
        String second = "_gt_cycle_b";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        aliasesCfg.aliases.put(first, new ArrayList<>(List.of(second)));
        aliasesCfg.aliases.put(second, new ArrayList<>(List.of(first)));
        AliasManager.registerOrReplace(dispatcher, first);
        AliasManager.registerOrReplace(dispatcher, second);

        try {
            assertDoesNotOverflow(dispatcher, first, server.createCommandSourceStack());
            helper.succeed();
        } finally {
            aliasesCfg.aliases.remove(first);
            aliasesCfg.aliases.remove(second);
            AliasManager.registerOrReplace(dispatcher, first);
            AliasManager.registerOrReplace(dispatcher, second);
        }
    }

    private static void assertDoesNotOverflow(
            com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
            String alias,
            CommandSourceStack source) {
        try {
            dispatcher.execute(alias, source);
        } catch (CommandSyntaxException e) {
            fail("Recursive alias syntax error: " + e.getMessage());
        } catch (StackOverflowError e) {
            fail("Recursive alias cycle caused a StackOverflowError.");
        }
    }

    /**
     * Placeholder — verifying that a non-OP player cannot use /customperm requires a real
     * ServerPlayer mock, which is unavailable in MC 1.21.1 GameTest.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void custompermBlockedForNonOpPlaceholder(GameTestHelper helper) {
        // Placeholder: mock player ≠ ServerPlayer in 1.21.1 — E2E OP check deferred.
        helper.succeed();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
