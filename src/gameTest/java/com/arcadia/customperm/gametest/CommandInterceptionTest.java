package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
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
