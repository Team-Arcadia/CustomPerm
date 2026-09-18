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
import com.arcadia.customperm.admin.AliasAdmin;
import com.arcadia.customperm.admin.CommandAdmin;
import com.arcadia.customperm.gametest.CommandExposureGameTest.Exposure;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.CommandsData;
import com.arcadia.customperm.network.gui.GradesData;
import com.arcadia.customperm.network.gui.GuiAccess;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.perm.PermissionNodes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Operators are restrictable (backlog item 8): an explicit DENY applies to them, in both backends, while
 * a node that is not set keeps the vanilla operator behaviour. Tests that change a server-wide setting
 * (gateAllCommands, the default grade) run in their own batch, since tests of one batch run together.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class OperatorRestrictionGameTest {

    private static final String TEMPLATE = "empty_3x3";

    /** Security fix for the LuckPerms backend: exposure no longer lets op level 2 past an explicit false. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void deniedExposedCommandIsRefusedToOperators(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_o_cmd", 2);
             Exposure ignored = Exposure.of(helper.getLevel().getServer(), "gamemode")) {
            if (!op.canUse("gamemode")) fail("Setup: an operator without any value must keep an exposed command.");
            try (Grants deny = Grants.deny(op, "customperm.command.gamemode")) {
                if (op.canUse("gamemode")) fail("An operator denied customperm.command.gamemode could still use it.");
            }
            if (!op.canUse("gamemode")) fail("Removing the denial must give the operator the command back.");
        }
        helper.succeed();
    }

    /** The accidental op: operator level 4 alone gives no access to CustomPerm, the console always has it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void operatorsWithoutNodesCannotAdministerButTheConsoleCan(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer owner = TestPlayer.join(helper.getLevel(), "cp_o_admin", 4)) {
            if (owner.canUse("customperm")) fail("A level-4 operator without customperm.admin could see /customperm.");
            if (GuiAccess.canRead(owner.player())) fail("A level-4 operator without customperm.admin could read the interface.");
            owner.clearReceived();
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.RELOAD.name(), List.of(), "dashboard"),
                    owner.payloadContext());
            if (!owner.payloads(GuiActionResultPayload.class).isEmpty())
                fail("A level-4 operator without customperm.admin still got an interface reply.");
            if (!ServerCommands.contains(ServerCommands.run(server, "customperm status"), "CustomPerm Status"))
                fail("The console must always be able to run /customperm.");

            try (Grants entry = Grants.allow(owner, PermissionNodes.ADMIN)) {
                if (!owner.canUse("customperm")) fail("customperm.admin must open /customperm to an operator.");
                if (owner.exec("customperm status") != 1) fail("customperm.admin must allow /customperm status.");
                owner.clearReceived();
                owner.type("customperm alias add cp_o_noway say hi");
                if (CustomPerm.configManager.getAliases().aliases.containsKey("cp_o_noway"))
                    fail("customperm.admin alone must not change aliases.");
                try (Grants deny = Grants.deny(owner, PermissionNodes.ADMIN)) {
                    if (owner.canUse("customperm")) fail("An explicit DENY must beat the ALLOW at the same level.");
                }
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            fail("customperm.admin did not allow /customperm status: " + e.getMessage());
        } finally {
            AliasAdmin.remove(server, "cp_o_noway");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void deniedAliasIsRefusedToOperators(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_o_alias", 2)) {
            AliasAdmin.define(server, "cp_o_alias_cmd", List.of("say hi"));
            if (!op.canUse("cp_o_alias_cmd")) fail("Setup: an operator without any value must keep an alias.");
            try (Grants deny = Grants.deny(op, "customperm.alias.cp_o_alias_cmd")) {
                if (op.canUse("cp_o_alias_cmd")) fail("An operator denied the alias node could still use the alias.");
            }
        } finally {
            AliasAdmin.remove(server, "cp_o_alias_cmd");
        }
        helper.succeed();
    }

    /** Each area needs its own customperm.manage node, for the command and the interface alike, level 4 included. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void eachAreaNeedsItsManageNode(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer owner = TestPlayer.reader(helper.getLevel(), "cp_o_area", 4);
             Grants aliases = Grants.allow(owner, PermissionNodes.MANAGE_ALIASES)) {
            if (!GuiAccess.canEdit(owner.player(), GuiArea.ALIASES)) fail("customperm.manage.aliases must open the aliases area.");
            if (GuiAccess.canEdit(owner.player(), GuiArea.COMMANDS)) fail("Level 4 must not edit an area without its node.");
            owner.type("customperm alias add cp_o_area_alias say hi");
            if (!CustomPerm.configManager.getAliases().aliases.containsKey("cp_o_area_alias"))
                fail("customperm.manage.aliases must allow /customperm alias add.");
            owner.type("customperm command add weather");
            if (CustomPerm.configManager.getCommands().grantedCommands.contains("weather"))
                fail("Without customperm.manage.commands, /customperm command add must be refused.");
        } finally {
            AliasAdmin.remove(server, "cp_o_area_alias");
            CommandAdmin.hide(server, "weather");
        }
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_o_area2", 4);
             Grants deny = Grants.deny(owner, PermissionNodes.MANAGE_ALIASES)) {
            if (GuiAccess.canEdit(owner.player(), GuiArea.ALIASES)) fail("A denied area node must beat customperm.*.");
            if (!GuiAccess.canEdit(owner.player(), GuiArea.COMMANDS)) fail("Other areas must stay open with customperm.*.");
        }
        helper.succeed();
    }

    /** Audit retest R02 still holds: the nodes alone, even customperm.*, never open /customperm to a non-operator. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nodesWithoutOperatorLevelOpenNothing(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.admin(helper.getLevel(), "cp_o_nonop", 0)) {
            if (player.canUse("customperm") || GuiAccess.canRead(player.player()))
                fail("customperm.* opened CustomPerm administration to a non-operator.");
        }
        helper.succeed();
    }

    /**
     * A denied {@code *} blocks every command except explicit allows, operators included. The internal
     * backend needs gateAllCommands for unexposed commands; LuckPerms already checks every command.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_gate_star_deny")
    public static void deniedStarBlocksEverythingButExplicitAllows(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean internal = !CustomPerm.isLuckPermsActive();
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_o_star", 4);
             Exposure ignored = Exposure.of(server, "gamemode")) {
            if (internal) expect(CommandAdmin.setGateAll(server, true).success(), "Enabling gateAllCommands failed.");
            try (Grants allow = Grants.allow(op, "customperm.command.gamemode");
                 Grants deny = Grants.deny(op, "*")) {
                if (!op.canUse("gamemode")) fail("An explicit ALLOW must beat a denied *.");
                if (op.canUse("time")) fail("A denied * must block /time for an operator.");
                if (op.canUse("customperm")) fail("A denied * must also deny customperm.admin.");
            }
            if (!op.canUse("time")) fail("Without the denial the operator must get /time back.");
        } finally {
            if (internal) CommandAdmin.setGateAll(server, false);
        }
        helper.succeed();
    }

    /** An allowed {@code *} opens every command only once every command is gated, never /customperm (R02). */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_gate_star_allow")
    public static void allowedStarOpensEveryCommandOnlyWhenGatingAll(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_o_allstar", 0);
             Grants allow = Grants.allow(player, "*")) {
            if (player.canUse("time")) fail("Without gateAllCommands a granted * must not open unexposed commands.");
            expect(CommandAdmin.setGateAll(server, true).success(), "Enabling gateAllCommands failed.");
            if (!player.canUse("time")) fail("With gateAllCommands a granted * must open /time.");
            if (player.canUse("customperm")) fail("A granted * must never open /customperm to a non-operator.");
        } finally {
            CommandAdmin.setGateAll(server, false);
        }
        helper.succeed();
    }

    /** The scenario behind item 8: a player made operator by mistake, with no grade of their own. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_default_grade")
    public static void defaultGradeRestrictsAnAccidentalOperator(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try {
            ServerCommands.run(server, "customperm grade create cp_o_everyone");
            ServerCommands.run(server, "customperm grade adddeny cp_o_everyone *");
            ServerCommands.run(server, "customperm grade addperm cp_o_everyone customperm.command.list");
            ServerCommands.run(server, "customperm grade setdefault cp_o_everyone");
            ServerCommands.run(server, "customperm command gateall true");
            expect(ServerCommands.contains(ServerCommands.run(server, "customperm status"), "Default grade      : cp_o_everyone"),
                    "Status must show the default grade.");
            try (TestPlayer accident = TestPlayer.join(helper.getLevel(), "cp_o_accident", 4);
                 TestPlayer staff = TestPlayer.join(helper.getLevel(), "cp_o_staff", 4);
                 Grants trusted = Grants.allow(staff, "customperm.admin", "customperm.command.time")) {
                if (accident.canUse("give") || accident.canUse("time")) fail("The default grade's denied * must restrict an operator.");
                if (accident.canUse("customperm")) fail("The accidental operator must not administer CustomPerm.");
                if (!accident.canUse("list")) fail("A node the default grade allows must stay open.");
                if (!staff.canUse("customperm") || !staff.canUse("time"))
                    fail("A player's own grade must decide before the default grade.");
                if (staff.canUse("give")) fail("Nodes the own grade does not mention must fall through to the default grade.");
            }
            expect(ServerCommands.contains(ServerCommands.run(server, "customperm status"), "CustomPerm Status"),
                    "The console must never be locked out.");
        } finally {
            ServerCommands.run(server, "customperm command gateall false");
            ServerCommands.run(server, "customperm grade delete cp_o_everyone");
        }
        if (!CustomPerm.configManager.getSettings().defaultGrade.isEmpty())
            fail("Deleting the default grade must clear defaultGrade.");
        helper.succeed();
    }

    /** An admin cannot lock themselves out of /customperm in game, by command or through the interface. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_self_lockout")
    public static void selfLockoutIsRefused(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        var grades = CustomPerm.configManager.getGrades();
        try (TestPlayer admin = TestPlayer.join(helper.getLevel(), "cp_o_self", 4)) {
            ServerCommands.run(server, "customperm grade create cp_o_mine");
            ServerCommands.run(server, "customperm grade addperm cp_o_mine customperm.admin");
            ServerCommands.run(server, "customperm grade addperm cp_o_mine customperm.manage.grades");
            ServerCommands.run(server, "customperm grade assign cp_o_self cp_o_mine");
            if (!admin.canUse("customperm")) fail("Setup: the grade must let the admin in.");

            admin.clearReceived();
            admin.type("customperm grade adddeny cp_o_mine customperm.admin");
            if (!admin.chatContains("Refused")) fail("Denying customperm.admin to one's own grade must be refused, chat: " + admin.chat());
            if (grades.grades.get("cp_o_mine").deniedPermissions.contains("customperm.admin")) fail("The refused change was not undone.");

            admin.clearReceived();
            admin.type("customperm grade removeperm cp_o_mine customperm.manage.grades");
            if (!admin.chatContains("Refused")) fail("Removing one's own customperm.manage.grades must be refused, chat: " + admin.chat());
            if (!grades.grades.get("cp_o_mine").permissions.contains("customperm.manage.grades")) fail("The refused removal was kept.");

            admin.clearReceived();
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.GRADE_UNASSIGN.name(),
                    List.of(admin.uuid().toString(), "cp_o_mine", ""), GuiPage.GRADES.id()), admin.payloadContext());
            List<GuiActionResultPayload> results = admin.payloads(GuiActionResultPayload.class);
            if (results.size() != 1 || results.get(0).success() || !results.get(0).message().contains("Refused"))
                fail("Unassigning the admin's own access grade through the interface must be refused, got " + results);
            if (!admin.canUse("customperm")) fail("The admin lost /customperm despite the refusals.");

            ServerCommands.run(server, "customperm grade create cp_o_backup");
            ServerCommands.run(server, "customperm grade addperm cp_o_backup customperm.*");
            ServerCommands.run(server, "customperm grade assign cp_o_self cp_o_backup");
            admin.clearReceived();
            admin.type("customperm grade removeperm cp_o_mine customperm.manage.grades");
            if (admin.chatContains("Refused")) fail("With another grade keeping access, the removal must be accepted: " + admin.chat());
        } finally {
            ServerCommands.run(server, "customperm grade delete cp_o_mine");
            ServerCommands.run(server, "customperm grade delete cp_o_backup");
        }
        helper.succeed();
    }

    /** The two settings are reachable from the interface, and the gate-all switch refuses under LuckPerms. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_gate_interface")
    public static void interfaceSetsGateAllAndTheDefaultGrade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_o_gui", 4)) {
            owner.clearReceived();
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.COMMAND_GATE_ALL.name(), List.of("true"),
                    GuiPage.COMMANDS.id()), owner.payloadContext());
            List<GuiActionResultPayload> results = owner.payloads(GuiActionResultPayload.class);
            if (CustomPerm.isLuckPermsPresent()) {
                if (results.size() != 1 || results.get(0).success() || !results.get(0).message().contains("LuckPerms"))
                    fail("Gate all must be refused with LuckPerms installed, got " + results);
                helper.succeed();
                return;
            }
            if (results.size() != 1 || !results.get(0).success()) fail("Gate all was not applied: " + results);
            CommandsData commands = page(owner, CommandsData.class);
            if (!commands.gateAll() || !CustomPerm.configManager.getSettings().gateAllCommands)
                fail("The commands page and settings must show gate all on.");

            ServerCommands.run(server, "customperm grade create cp_o_guidefault");
            owner.clearReceived();
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.GRADE_DEFAULT.name(), List.of("cp_o_guidefault"),
                    GuiPage.GRADES.id()), owner.payloadContext());
            GradesData grades = page(owner, GradesData.class);
            if (!"cp_o_guidefault".equals(grades.defaultGrade()) || !grades.gateAll())
                fail("The grades page must show the new default grade, got " + grades.defaultGrade());
            owner.clearReceived();
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.GRADE_DEFAULT.name(), List.of(""),
                    GuiPage.GRADES.id()), owner.payloadContext());
            if (!CustomPerm.configManager.getSettings().defaultGrade.isEmpty()) fail("An empty name must clear the default grade.");
        } finally {
            if (!CustomPerm.isLuckPermsPresent()) {
                CommandAdmin.setGateAll(server, false);
                ServerCommands.run(server, "customperm grade delete cp_o_guidefault");
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** The in-place refresh a successful action pushed. */
    private static <T> T page(TestPlayer player, Class<T> type) {
        List<GuiPagePayload> pages = player.payloads(GuiPagePayload.class);
        if (pages.isEmpty() || !type.isInstance(pages.get(pages.size() - 1).data()))
            fail("Expected a refreshed " + type.getSimpleName() + ", got " + pages);
        return type.cast(pages.get(pages.size() - 1).data());
    }

    private static void expect(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
