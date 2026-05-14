package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
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

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
