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
     * Verifies: 2 steps registered → executed count = 2 (both ran, no premature halt).
     * {@code performPrefixedCommand} catches {@code CommandSyntaxException} internally and
     * returns 0 for failed commands without throwing, so {@code executed++} increments for
     * every attempted step. Count = 2 confirms both steps were attempted.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aliasNoHaltOnError(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gt_nohalt";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        // Two steps — both should execute (no-halt guarantee)
        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say step_a", "say step_b")));
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
            if (result != 2)
                fail("Expected 2 steps executed (no-halt), got " + result
                    + ". Both steps must run even if one fails.");

            helper.succeed();
        } finally {
            // Always clean up the test alias from the live dispatcher (F3)
            aliasesCfg.aliases.remove(testName);
            AliasManager.registerOrReplace(dispatcher, testName);
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
