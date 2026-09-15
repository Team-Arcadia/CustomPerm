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
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for command exposure and dispatcher integration (AC2 — story 6-2).
 *
 * Note: Tests that require a real non-OP player (ServerPlayer mock) are impossible
 * in MC 1.21.1 because {@code GameTestHelper.makeMockPlayer} returns an anonymous
 * {@code Player} subclass, not a {@code ServerPlayer}. Data-layer tests only.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class CommandInterceptionTest {

    private static final String TEMPLATE = "empty_3x3";

    /**
     * AC: adding a command to grantedCommands makes it present in the exposed list.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void exposedCommandInGrantedList(GameTestHelper helper) {
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        boolean had = exposed.contains("gamemode");
        if (had) exposed.remove("gamemode");

        exposed.add("gamemode");
        if (!exposed.contains("gamemode"))
            fail("gamemode was not found in grantedCommands after add.");

        // Restore
        exposed.remove("gamemode");
        if (had) exposed.add("gamemode");
        helper.succeed();
    }

    /**
     * AC: a command not in grantedCommands is absent from the exposed list.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nonExposedCommandAbsentFromGrantedList(GameTestHelper helper) {
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        // "_gametest_cmd" is a synthetic name that should never be in the config
        if (exposed.contains("_gametest_cmd"))
            fail("_gametest_cmd must not be in grantedCommands — test isolation broken.");
        helper.succeed();
    }

    /**
     * AC: live add/remove from grantedCommands is reflected immediately (data-layer).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void liveRetraitExposition(GameTestHelper helper) {
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        String testCmd = "_gt_expose_test";
        exposed.remove(testCmd);
        if (exposed.contains(testCmd))
            fail("Cleanup failed — test command already present before test.");

        // Add
        exposed.add(testCmd);
        if (!exposed.contains(testCmd))
            fail("Add to grantedCommands did not take effect.");

        // Remove
        exposed.remove(testCmd);
        if (exposed.contains(testCmd))
            fail("Remove from grantedCommands did not take effect.");

        helper.succeed();
    }

    /**
     * AC: after exposing "gamemode", the vanilla node is present in the live dispatcher.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void wrappedNodeExistsForExposedCommand(GameTestHelper helper) {
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        boolean had = exposed.contains("gamemode");
        if (!had) exposed.add("gamemode");

        var server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> node = Customperm.findRoot(server, "gamemode");
        if (node == null)
            fail("/gamemode node missing from dispatcher after exposition.");

        if (!had) exposed.remove("gamemode");
        helper.succeed();
    }

    /**
     * Runtime repair wraps newly registered roots regardless of backend.
     *
     * Direct command exposure is always active (the customperm.command.&lt;name&gt; node is
     * resolved by LuckPerms when present, by the internal grades otherwise), so a freshly
     * registered root must be wrapped, and an unexposed command must stay denied to non-op.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void runtimeCommandRepairMatchesBackendPolicy(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        String testName = "_gt_runtime_repair";

        dispatcher.register(Commands.literal(testName)
            .requires(src -> false)
            .executes(ctx -> 1));

        CommandSourceStack source = server.createCommandSourceStack();
        CommandNode<CommandSourceStack> original = Customperm.findRoot(server, testName);
        if (original == null)
            fail("Setup failed — synthetic runtime command was not registered.");

        int repaired = CommandTreeRewriter.repair(server);
        CommandNode<CommandSourceStack> afterRepair = Customperm.findRoot(server, testName);

        if (repaired < 1)
            fail("Repair did not wrap the synthetic runtime command.");
        if (afterRepair == original)
            fail("Repair did not replace the synthetic runtime command node.");
        if (afterRepair.canUse(source))
            fail("Repair changed the unexposed command requirement unexpectedly.");

        helper.succeed();
    }

    /**
     * A shortcut root that redirects to another root (vanilla /tp -> /teleport) must reach a
     * subtree wrapped under the SHORTCUT's name. Before the fix the clone kept a pointer to the
     * original, unwrapped target, so an exposed /tp let any source through its arguments.
     *
     * Uses a level-0 source with no entity: PermissionService grants it nothing, so an exposed
     * command must deny it while an unexposed one keeps the original (always-true) requirement.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void shortcutRedirectIsGatedUnderItsOwnName(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        String target = "_gt_redirect_target";
        String shortcut = "_gt_redirect_short";

        LiteralCommandNode<CommandSourceStack> targetNode = dispatcher.register(Commands.literal(target)
            .then(Commands.literal("go").executes(ctx -> 1)));
        dispatcher.register(Commands.literal(shortcut).redirect(targetNode));
        CommandTreeRewriter.repair(server);

        CommandSourceStack nobody = server.createCommandSourceStack().withPermission(0);
        exposed.add(shortcut);
        try {
            CommandNode<CommandSourceStack> shortRoot = Customperm.findRoot(server, shortcut);
            CommandNode<CommandSourceStack> liveTarget = Customperm.findRoot(server, target);
            if (shortRoot == null || liveTarget == null)
                fail("Setup failed — synthetic commands missing from the dispatcher.");
            CommandNode<CommandSourceStack> redirect = shortRoot.getRedirect();
            if (redirect == null)
                fail("Wrapped shortcut lost its redirect.");
            if (redirect == targetNode)
                fail("Shortcut still redirects to the original, unwrapped target node.");
            if (redirect == liveTarget)
                fail("Shortcut must get its own clone of the target, not the target's wrapped root.");

            CommandNode<CommandSourceStack> goViaShortcut = redirect.getChild("go");
            if (goViaShortcut == null)
                fail("Cloned target subtree is missing its child.");
            if (goViaShortcut.canUse(nobody))
                fail("Exposed shortcut let an ungranted source through the redirected sub-command.");
            if (!liveTarget.getChild("go").canUse(nobody))
                fail("Exposing the shortcut must not change the target command's own gating.");
        } finally {
            exposed.remove(shortcut);
        }
        helper.succeed();
    }

    /** Same guarantee on the real vanilla shortcut: exposing /tp gates the /teleport arguments reached through it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void vanillaTpRedirectIsGatedAsTp(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        boolean had = exposed.contains("tp");
        CommandSourceStack nobody = server.createCommandSourceStack().withPermission(0);
        exposed.add("tp");
        try {
            CommandNode<CommandSourceStack> tp = Customperm.findRoot(server, "tp");
            CommandNode<CommandSourceStack> teleport = Customperm.findRoot(server, "teleport");
            if (tp == null || teleport == null || tp.getRedirect() == null)
                fail("Vanilla /tp or /teleport missing, or /tp no longer a redirect.");
            if (tp.getRedirect() == teleport)
                fail("/tp must redirect to its own wrapped clone of /teleport.");
            CommandNode<CommandSourceStack> location = tp.getRedirect().getChild("location");
            if (location == null)
                fail("Cloned /teleport subtree is missing its 'location' argument.");
            if (location.canUse(nobody))
                fail("Exposed /tp let an ungranted source reach /teleport <location>.");
        } finally {
            if (!had) exposed.remove("tp");
        }
        helper.succeed();
    }

    /**
     * Redirect cycles must not recurse forever while cloning: a root redirecting into its own
     * node (vanilla /execute as ...), and a shortcut root whose target redirects back to the
     * shortcut, where the shortcut's clone does not exist yet when the back-reference is met.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void redirectCyclesWrapWithoutRecursing(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();

        LiteralCommandNode<CommandSourceStack> self = dispatcher.register(Commands.literal("_gt_cycle_self")
            .executes(ctx -> 1));
        self.addChild(Commands.<CommandSourceStack>literal("again").redirect(self).build());

        LiteralCommandNode<CommandSourceStack> target = dispatcher.register(Commands.literal("_gt_cycle_target")
            .executes(ctx -> 1));
        LiteralCommandNode<CommandSourceStack> shortcut = dispatcher.register(Commands.literal("_gt_cycle_short")
            .redirect(target));
        target.addChild(Commands.<CommandSourceStack>literal("back").redirect(shortcut).build());

        try {
            CommandTreeRewriter.repair(server);
        } catch (StackOverflowError e) {
            fail("Wrapping a redirect cycle recursed without bound.");
        }

        CommandNode<CommandSourceStack> selfRoot = Customperm.findRoot(server, "_gt_cycle_self");
        if (selfRoot == self)
            fail("Self-redirecting root was not wrapped.");
        if (selfRoot.getChild("again").getRedirect() != selfRoot)
            fail("A root redirecting into itself must point at its own wrapped clone.");
        CommandNode<CommandSourceStack> shortRoot = Customperm.findRoot(server, "_gt_cycle_short");
        if (shortRoot == shortcut || Customperm.findRoot(server, "_gt_cycle_target") == target)
            fail("Roots in a redirect cycle were not wrapped.");
        if (shortRoot.getRedirect() == target)
            fail("Shortcut in a cycle must still get a wrapped clone of its target.");
        helper.succeed();
    }

    /**
     * With an active LuckPerms backend, CustomPerm still exposes direct commands — the
     * permission node customperm.command.&lt;name&gt; is simply resolved through LuckPerms
     * instead of the internal grades. /customperm command add must therefore be accepted.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void luckPermsDelegatesDirectCommandExposure(GameTestHelper helper) {
        if (!CustomPerm.isLuckPermsActive()) {
            // CI intentionally runs without the optional LuckPerms mod. The internal
            // policy is covered by runtimeCommandRepairMatchesBackendPolicy.
            helper.succeed();
            return;
        }

        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        boolean had = exposed.contains("gamemode");
        exposed.remove("gamemode");

        try {
            int result = helper.getLevel().getServer().getCommands().getDispatcher().execute(
                "customperm command add gamemode",
                helper.getLevel().getServer().createCommandSourceStack());
            if (result != 1)
                fail("/customperm command add must be accepted while LuckPerms is active.");
            if (!exposed.contains("gamemode"))
                fail("LuckPerms mode must add gamemode to grantedCommands (node resolved by LuckPerms).");
        } catch (CommandSyntaxException e) {
            fail("Command syntax error while checking LuckPerms direct-command policy: " + e.getMessage());
        } finally {
            exposed.remove("gamemode");
            if (had) exposed.add("gamemode");
        }

        helper.succeed();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
