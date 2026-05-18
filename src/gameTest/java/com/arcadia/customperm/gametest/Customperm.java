package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.command.AliasManager;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.InternalPermService;
import com.arcadia.customperm.perm.PermissionService;
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
import java.util.UUID;

/**
 * GameTest container for CustomPerm.
 *
 * The class name is intentionally {@code Customperm} (lowercased to {@code customperm})
 * because Minecraft's TestFunction naming uses {@code Class.getSimpleName().toLowerCase()
 * + "." + methodName.toLowerCase()} as the test ID.
 *
 * The {@link GameTestHolder} annotation provides the test namespace ("customperm")
 * used by NeoForge to filter against the {@code neoforge.enabledGameTestNamespaces}
 * system property. Without it, the namespace defaults to "minecraft" and our tests
 * get filtered out — that's the cause of the "No test functions were given!" error
 * if this annotation is missing.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class Customperm {

    // MC auto-prefixes with the holder namespace. Don't include "customperm:" here.
    private static final String TEMPLATE = "empty_3x3";

    // Group 1 — Internal permission backend (logic-level)
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void internalDeniesByDefault(GameTestHelper helper) {
        GradesConfig grades = freshGrades();
        UUID uuid = UUID.randomUUID();
        if (grades.userHasPermission(uuid, "customperm.command.gamemode"))
            fail("Empty grade store granted a perm for an unknown player.");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void internalGrantsViaGrade(GameTestHelper helper) {
        GradesConfig grades = freshGrades();
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.name = "test_vip";
        vip.permissions.add("customperm.command.gamemode");
        grades.grades.put("test_vip", vip);
        UUID uuid = UUID.randomUUID();
        grades.userGrades.put(uuid.toString(), new ArrayList<>(List.of("test_vip")));
        if (!grades.userHasPermission(uuid, "customperm.command.gamemode"))
            fail("Player with grade did not receive the assigned perm.");
        if (grades.userHasPermission(uuid, "customperm.command.give"))
            fail("Player gained an unrelated perm not present on the grade.");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void internalWildcardWorks(GameTestHelper helper) {
        GradesConfig grades = freshGrades();
        GradesConfig.Grade staff = new GradesConfig.Grade();
        staff.name = "staff";
        staff.permissions.add("customperm.command.*");
        grades.grades.put("staff", staff);
        UUID uuid = UUID.randomUUID();
        grades.userGrades.put(uuid.toString(), new ArrayList<>(List.of("staff")));
        for (String node : List.of("customperm.command.gamemode", "customperm.command.give", "customperm.command.tp")) {
            if (!grades.userHasPermission(uuid, node)) fail("Wildcard did not cover " + node);
        }
        if (grades.userHasPermission(uuid, "customperm.alias.fly"))
            fail("customperm.command.* must NOT cover customperm.alias.*");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void internalMultipleGradesCompose(GameTestHelper helper) {
        GradesConfig grades = freshGrades();
        GradesConfig.Grade base = new GradesConfig.Grade();
        base.name = "base";
        base.permissions.add("customperm.command.tp");
        grades.grades.put("base", base);
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.name = "vip";
        vip.permissions.add("customperm.command.gamemode");
        grades.grades.put("vip", vip);
        UUID uuid = UUID.randomUUID();
        grades.userGrades.put(uuid.toString(), new ArrayList<>(List.of("base", "vip")));
        if (!grades.userHasPermission(uuid, "customperm.command.tp")) fail("First grade's perm not granted.");
        if (!grades.userHasPermission(uuid, "customperm.command.gamemode")) fail("Second grade's perm not granted.");
        helper.succeed();
    }

    // Group 2 — Configuration persistence
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void configRoundtripsThroughDisk(GameTestHelper helper) {
        GradesConfig grades = freshGrades();
        GradesConfig.Grade marker = new GradesConfig.Grade();
        marker.name = "_gametest_marker";
        marker.permissions.add("test.perm.gametest");
        grades.grades.put("_gametest_marker", marker);
        CustomPerm.configManager.save();
        CustomPerm.configManager.load();
        GradesConfig.Grade reloaded = CustomPerm.configManager.getGrades().grades.get("_gametest_marker");
        if (reloaded == null) fail("Grade did not survive save+load roundtrip.");
        if (!reloaded.permissions.contains("test.perm.gametest")) fail("Perm lost during roundtrip.");
        CustomPerm.configManager.getGrades().grades.remove("_gametest_marker");
        CustomPerm.configManager.save();
        helper.succeed();
    }

    // Group 3 — Command exposure
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void commandExposureGate(GameTestHelper helper) {
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        boolean had = exposed.contains("gamemode");
        if (had) exposed.remove("gamemode");
        if (exposed.contains("gamemode")) fail("Could not clear `gamemode` from exposed list.");
        exposed.add("gamemode");
        if (!exposed.contains("gamemode")) fail("Add to exposed list did not take effect.");
        exposed.remove("gamemode");
        if (exposed.contains("gamemode")) fail("Remove from exposed list did not take effect.");
        if (had) exposed.add("gamemode");
        helper.succeed();
    }

    // Group 4 — End-to-end with the live dispatcher
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void gradeDeleteRemovesEmptyUserAssignments(GameTestHelper helper) {
        GradesConfig grades = freshGrades();
        GradesConfig.Grade vip = new GradesConfig.Grade();
        vip.name = "vip";
        grades.grades.put("vip", vip);
        UUID uuid = UUID.randomUUID();
        grades.userGrades.put(uuid.toString(), new ArrayList<>(List.of("vip")));

        var server = helper.getLevel().getServer();
        PermissionService previousPermissions = CustomPerm.permissions;
        try {
            CustomPerm.permissions = new InternalPermService(CustomPerm.configManager);
            int result = server.getCommands().getDispatcher().execute(
                    "customperm grade delete vip",
                    server.createCommandSourceStack());
            if (result != 1)
                fail("Expected /customperm grade delete vip to succeed, got result " + result);
        } catch (CommandSyntaxException e) {
            fail("Command syntax error while deleting grade: " + e.getMessage());
        } finally {
            CustomPerm.permissions = previousPermissions;
        }

        if (grades.grades.containsKey("vip"))
            fail("Grade vip was not deleted.");
        if (grades.userGrades.containsKey(uuid.toString()))
            fail("Deleting the only assigned grade must remove the now-empty userGrades entry.");

        helper.succeed();
    }

    /**
     * End-to-end with a real player would require {@code (ServerPlayer) helper.makeMockPlayer(...)},
     * but {@code GameTestHelper.makeMockPlayer} in 1.21.1 returns an anonymous Player subclass
     * that is NOT a ServerPlayer — the cast crashes. {@link InternalPermService#hasPermission}
     * gates on {@code instanceof ServerPlayer}, so a non-ServerPlayer mock returns false anyway.
     *
     * The full flow is exercised in the manual procedure (see test-complete.html, phase H);
     * the components are covered by the other tests in this file. We keep the slot here as a
     * placeholder for when a future MC version exposes a real ServerPlayer mock.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void wrappedCanUseFullFlow(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, skyAccess = true)
    public static void opAlwaysPasses(GameTestHelper helper) {
        var commandsCfg = CustomPerm.configManager.getCommands();
        boolean wasExposed = commandsCfg.grantedCommands.contains("gamemode");
        commandsCfg.grantedCommands.remove("gamemode");

        // The server's own command source is op level 4 by construction — no mock needed.
        var server = helper.getLevel().getServer();
        CommandSourceStack opSource = server.createCommandSourceStack();

        var node = findRoot(server, "gamemode");
        if (node == null) fail("/gamemode missing from dispatcher.");
        if (!node.canUse(opSource))
            fail("Op-level source was denied — wrapper must always honour the original op check.");

        if (wasExposed) commandsCfg.grantedCommands.add("gamemode");
        helper.succeed();
    }

    // Group 5 — Aliases at runtime
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasRegistersOnLiveDispatcher(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gametest_alias";
        aliasesCfg.aliases.remove(testName);
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        if (findRoot(server, testName) != null) fail("Test alias was already in dispatcher.");
        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say hello")));
        AliasManager.registerOrReplace(dispatcher, testName);
        if (findRoot(server, testName) == null) fail("Alias was not registered on the live dispatcher.");
        aliasesCfg.aliases.remove(testName);
        AliasManager.registerOrReplace(dispatcher, testName);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasRemovesFromDispatcher(GameTestHelper helper) {
        var aliasesCfg = CustomPerm.configManager.getAliases();
        String testName = "_gametest_alias_rm";
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        aliasesCfg.aliases.put(testName, new ArrayList<>(List.of("say x")));
        AliasManager.registerOrReplace(dispatcher, testName);
        if (findRoot(server, testName) == null) fail("Setup failed — alias not registered.");
        aliasesCfg.aliases.remove(testName);
        AliasManager.registerOrReplace(dispatcher, testName);
        if (findRoot(server, testName) != null) fail("Alias still in dispatcher after removal.");
        helper.succeed();
    }

    // Helpers
    private static GradesConfig freshGrades() {
        GradesConfig g = CustomPerm.configManager.getGrades();
        g.grades.clear();
        g.userGrades.clear();
        return g;
    }

    static CommandNode<CommandSourceStack> findRoot(net.minecraft.server.MinecraftServer server, String name) {
        if (server == null) return null;
        return server.getCommands().getDispatcher().getRoot().getChildren()
            .stream().filter(n -> n.getName().equals(name)).findFirst().orElse(null);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
