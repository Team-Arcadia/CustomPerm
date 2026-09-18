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
import com.arcadia.customperm.admin.ConfigAdmin;
import com.arcadia.customperm.admin.ExpirySweeper;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.network.gui.GradesData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.perm.Expiry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Temporary entries through the commands: durations read and refused, an entry that expires taking the
 * command away before anything sweeps it, and the sweep that tidies the file, records the removal and
 * sends the command tree again. Internal mode: with LuckPerms, LuckPerms keeps its own expiries.
 *
 * <p>The sweep runs every second on its own, so a test never counts on being the one that removes an
 * entry: it checks the state it leaves.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class TemporaryEntriesGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String GRADE = "cp_t_donor";
    private static final String COMMAND = "defaultgamemode";
    private static final String NODE = "customperm.command." + COMMAND;

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_temporary")
    public static void aTemporaryGrantExpiresAndIsSwept(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_t_player", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE);
            ServerCommands.run(server, "customperm grade create " + GRADE);

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 30x"),
                    "Invalid duration '30x'");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 30d extra"),
                    "Expected a node, then optionally a duration");
            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 30d"),
                    "Added " + NODE + " -> " + GRADE + " for 30d");
            long at = grades.grades.get(GRADE).permissionExpiries.getOrDefault(NODE, 0L);
            check(Math.abs(at - (Expiry.now() + 30L * 86400)) < 5, "30d must expire thirty days from now: " + at);

            expect(ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE),
                    NODE + " on " + GRADE + " is now permanent");
            check(!grades.grades.get(GRADE).permissionExpiries.containsKey(NODE),
                    "adding it again without a duration must make it permanent");
            ServerCommands.run(server, "customperm grade addperm " + GRADE + " " + NODE + " 1h");

            expect(ServerCommands.run(server, "customperm grade assign cp_t_player " + GRADE + " 7d"),
                    "Assigned " + GRADE + " -> cp_t_player for 7d");
            expect(ServerCommands.run(server, "customperm grade assign cp_t_player " + GRADE + " soon"),
                    "Invalid duration 'soon'");
            expectAny(ServerCommands.run(server, "customperm user list cp_t_player"), GRADE + " (7d left)", GRADE + " (6d 23h left)");
            check(player.canUse(COMMAND), "the temporary grade must grant the command while it lasts");

            // Time passes: the node is past its expiry. The resolver alone must stop granting it.
            grades.grades.get(GRADE).permissionExpiries.put(NODE, Expiry.now() - 1);
            check(!player.canUse(COMMAND), "an expired node must stop granting before any sweep");

            long trees = player.commandTreesReceived();
            ExpirySweeper.sweep(server);
            check(!grades.grades.get(GRADE).permissions.contains(NODE), "the sweep must remove the expired node");
            check(!grades.grades.get(GRADE).permissionExpiries.containsKey(NODE), "and its expiry");
            check(player.commandTreesReceived() > trees || !player.canUse(COMMAND),
                    "the players concerned must get their command tree again");
            check(ActivityLog.recent(LogKind.ADMIN, 50).stream().anyMatch(entry ->
                            entry.source().equals(LogEntry.SOURCE_EXPIRY)
                                    && entry.action().equals(GRADE + " no longer grants " + NODE)),
                    "the removal must be in the activity log");

            // The held grade expires the same way.
            grades.userGradeExpiries.get(uuid).put(GRADE, Expiry.now() - 1);
            ExpirySweeper.sweep(server);
            check(!grades.userGrades.containsKey(uuid), "an expired grade must no longer be held");
            check(!grades.userGradeExpiries.containsKey(uuid), "and its expiry must go with it");
        } finally {
            grades.grades.remove(GRADE);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userGradeExpiries.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /** A player's own temporary node and a temporary refusal, through the user commands. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_temporary")
    public static void aPlayersOwnEntriesTakeADurationToo(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_t_own", 0)) {
            uuid = player.uuid().toString();
            grades.grades.remove(GRADE + "_r");
            ServerCommands.run(server, "customperm grade create " + GRADE + "_r");

            expect(ServerCommands.run(server, "customperm user addperm cp_t_own customperm.command.seed 2h"),
                    "Added customperm.command.seed -> cp_t_own for 2h");
            expect(ServerCommands.run(server, "customperm user denygrade cp_t_own " + GRADE + "_r 1d"),
                    "cp_t_own now refuses " + GRADE + "_r for 1d");
            Map<String, Long> own = grades.userPermissionExpiries.get(uuid);
            check(own != null && own.containsKey("customperm.command.seed"), "the node's expiry must be stored");
            check(grades.userDeniedGradeExpiries.get(uuid).containsKey(GRADE + "_r"), "the refusal's too");
            List<String> listed = ServerCommands.run(server, "customperm user list cp_t_own");
            expectAny(listed, "customperm.command.seed (2h left)", "customperm.command.seed (1h 59m left)");
            expectAny(listed, GRADE + "_r (1d left)", GRADE + "_r (23h 59m left)");

            expect(ServerCommands.run(server, "customperm user removeperm cp_t_own customperm.command.seed"),
                    "Removed customperm.command.seed from cp_t_own");
            check(!grades.userPermissionExpiries.containsKey(uuid), "removing a node must forget its expiry");
        } finally {
            grades.grades.remove(GRADE + "_r");
            if (uuid != null) {
                grades.userPermissions.remove(uuid);
                grades.userPermissionExpiries.remove(uuid);
                grades.userDeniedGrades.remove(uuid);
                grades.userDeniedGradeExpiries.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /**
     * A grade inheriting another for a while, and refusing one for a while: the command, the listing, the
     * resolver letting go at expiry, the sweep, and the interface's duration box on the Parents tab.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_temporary")
    public static void aTemporaryParentExpiresAndIsSwept(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String base = GRADE + "_pbase";
        String staff = GRADE + "_pstaff";
        String child = GRADE + "_pchild";
        String uuid = null;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_t_powner", 4);
             TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_t_pplayer", 0);
             CommandExposureGameTest.Exposure ignored = CommandExposureGameTest.Exposure.of(server, COMMAND)) {
            uuid = player.uuid().toString();
            for (String name : List.of(base, staff, child)) {
                grades.grades.remove(name);
                ServerCommands.run(server, "customperm grade create " + name);
            }
            ServerCommands.run(server, "customperm grade addperm " + base + " " + NODE);
            ServerCommands.run(server, "customperm grade assign cp_t_pplayer " + child);

            expect(ServerCommands.run(server, "customperm grade parent add " + child + " " + base + " later"),
                    "Invalid duration 'later'");
            expect(ServerCommands.run(server, "customperm grade parent add " + child + " " + base + " 2h"),
                    child + " now inherits " + base + " for 2h");
            expect(ServerCommands.run(server, "customperm grade parent add " + child + " " + base),
                    child + " inheriting " + base + " is now permanent");
            ServerCommands.run(server, "customperm grade parent add " + child + " " + base + " 2h");
            expect(ServerCommands.run(server, "customperm grade parent adddeny " + child + " " + staff + " 1d"),
                    child + " now refuses " + staff + " for 1d");
            List<String> listed = ServerCommands.run(server, "customperm grade parent list " + child);
            expectAny(listed, base + " (2h left)", base + " (1h 59m left)");
            expectAny(listed, staff + " (1d left)", staff + " (23h 59m left)");
            check(player.canUse(COMMAND), "the temporary parent must be inherited while it lasts");

            grades.grades.get(child).parentExpiries.put(base, Expiry.now() - 1);
            check(!player.canUse(COMMAND), "an expired parent must stop being inherited before any sweep");
            grades.grades.get(child).deniedParentExpiries.put(staff, Expiry.now() - 1);
            ExpirySweeper.sweep(server);
            check(!grades.grades.get(child).parents.contains(base), "the sweep must remove the expired parent");
            check(!grades.grades.get(child).deniedParents.contains(staff), "and the expired refusal");
            check(grades.grades.get(child).parentExpiries.isEmpty() && grades.grades.get(child).deniedParentExpiries.isEmpty(),
                    "with their expiries");
            check(ActivityLog.recent(LogKind.ADMIN, 50).stream().anyMatch(entry ->
                            entry.source().equals(LogEntry.SOURCE_EXPIRY)
                                    && entry.action().equals(child + " no longer inherits " + base)),
                    "the removal must be in the activity log");

            act(owner, GuiAction.GRADE_PARENT_ADD, child, base, "never");
            result(owner, "FAIL: Invalid duration 'never'");
            act(owner, GuiAction.GRADE_PARENT_ADD, child, base, "7d");
            result(owner, "OK: " + child + " now inherits " + base + " for 7d");
            GradesData page = owner.payloads(GuiPagePayload.class).stream()
                    .map(GuiPagePayload::data).filter(GradesData.class::isInstance).map(GradesData.class::cast)
                    .reduce((first, second) -> second).orElseThrow(() -> new GameTestAssertException("No Grades page"));
            long left = page.grades().stream().filter(g -> g.name().equals(child)).findFirst().orElseThrow()
                    .remaining("parent", base);
            check(left > 7L * 86400 - 60 && left <= 7L * 86400, "the page must carry the parent's time left: " + left);
        } finally {
            for (String name : List.of(base, staff, child)) grades.grades.remove(name);
            if (uuid != null) grades.userGrades.remove(uuid);
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /** The interface: a duration box on a node and on an assignment, and the page saying what is left. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_temporary")
    public static void theInterfaceTakesAndShowsDurations(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String grade = GRADE + "_ui";
        String uuid = null;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_t_owner", 4);
             TestPlayer member = TestPlayer.join(helper.getLevel(), "cp_t_member", 0)) {
            uuid = member.uuid().toString();
            grades.grades.remove(grade);
            ServerCommands.run(helper.getLevel().getServer(), "customperm grade create " + grade);

            act(owner, GuiAction.GRADE_NODE_ADD, grade, "customperm.command.seed", "allow", "someday", "");
            result(owner, "FAIL: Invalid duration 'someday'");
            act(owner, GuiAction.GRADE_NODE_ADD, grade, "customperm.command.seed", "allow", "7d", "");
            result(owner, "OK: Added customperm.command.seed -> " + grade + " for 7d");
            act(owner, GuiAction.GRADE_ASSIGN, "cp_t_member", grade, "1d", "");
            result(owner, "OK: Assigned " + grade + " -> cp_t_member for 1d");

            GradesData page = owner.payloads(GuiPagePayload.class).stream()
                    .map(GuiPagePayload::data).filter(GradesData.class::isInstance).map(GradesData.class::cast)
                    .reduce((first, second) -> second).orElseThrow(() -> new GameTestAssertException("No Grades page"));
            GradesData.Grade shown = page.grades().stream().filter(g -> g.name().equals(grade)).findFirst().orElseThrow();
            long nodeLeft = shown.remaining("allow", "customperm.command.seed");
            check(nodeLeft > 7L * 86400 - 60 && nodeLeft <= 7L * 86400, "the page must carry the node's time left: " + nodeLeft);
            long memberLeft = shown.members().stream().filter(m -> m.name().equals("cp_t_member")).findFirst()
                    .orElseThrow().remaining();
            check(memberLeft > 86400 - 60 && memberLeft <= 86400, "and the assignment's: " + memberLeft);
        } finally {
            grades.grades.remove(grade);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userGradeExpiries.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    private static void act(TestPlayer player, GuiAction action, String... args) {
        player.clearReceived();
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.GRADES.id()),
                player.payloadContext());
    }

    private static void result(TestPlayer player, String prefix) {
        List<String> results = player.payloads(GuiActionResultPayload.class).stream()
                .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message()).toList();
        if (results.size() != 1 || !results.get(0).startsWith(prefix))
            throw new GameTestAssertException("Expected one result starting with '" + prefix + "', got " + results);
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) {
            throw new GameTestAssertException("Expected '" + fragment + "' in " + lines);
        }
    }

    /** A remaining time read within the same second as the grant, or the one after. */
    private static void expectAny(List<String> lines, String... fragments) {
        for (String fragment : fragments) {
            if (ServerCommands.contains(lines, fragment)) return;
        }
        throw new GameTestAssertException("Expected one of " + List.of(fragments) + " in " + lines);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
