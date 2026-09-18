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
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestModNodes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.perm.ModPermissions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.server.permission.PermissionAPI;

/**
 * The permission checks other mods make through NeoForge: CustomPerm answers them from the grades when it
 * is the handler, which it makes itself without LuckPerms, and leaves them to LuckPerms when it is there.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ModPermissionsGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String GRADE = "cp_p_modder";

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_mod_permissions")
    public static void theGradesAnswerTheChecksOfOtherMods(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        check(ModPermissions.answering(), "without LuckPerms CustomPerm must be the handler: "
                + PermissionAPI.getActivePermissionHandler());
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_p_player", 0)) {
            uuid = player.uuid().toString();
            var p = player.player();
            check(PermissionAPI.getPermission(p, TestModNodes.OPEN), "nothing set must answer the node default (true)");
            check(!PermissionAPI.getPermission(p, TestModNodes.SHUT), "nothing set must answer the node default (false)");

            ServerCommands.run(server, "customperm grade create " + GRADE);
            ServerCommands.run(server, "customperm grade adddeny " + GRADE + " cptest.probe.open");
            ServerCommands.run(server, "customperm grade addperm " + GRADE + " cptest.probe.*");
            ServerCommands.run(server, "customperm grade assign cp_p_player " + GRADE);
            check(!PermissionAPI.getPermission(p, TestModNodes.OPEN), "an exact DENY must refuse the node");
            check(PermissionAPI.getPermission(p, TestModNodes.SHUT), "a wildcard ALLOW must grant the node");
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 7, "a number node without meta must answer its default");
            check(PermissionAPI.getOfflinePermission(player.uuid(), TestModNodes.SHUT),
                    "an offline check must be resolved from the grades too");
            check(ModPermissions.declaredNodes().contains("cptest.probe.open")
                            && !ModPermissions.declaredNodes().contains("cptest.probe.limit"),
                    "the declared boolean nodes must be listed, the number one not: " + ModPermissions.declaredNodes());
        } finally {
            grades.grades.remove(GRADE);
            if (uuid != null) grades.userGrades.remove(uuid);
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    /** A number or text node another mod declares is answered from the meta of the same name. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_mod_permissions")
    public static void metaAnswersTheNumberAndTextNodesOfOtherMods(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        String uuid = null;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_p_meta", 0);
             TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_p_metaowner", 4)) {
            uuid = player.uuid().toString();
            var p = player.player();
            grades.grades.remove(GRADE);
            ServerCommands.run(server, "customperm grade create " + GRADE);
            ServerCommands.run(server, "customperm grade assign cp_p_meta " + GRADE);
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 7, "no meta must answer the node default");
            check(ModPermissions.declaredMetaNodes().equals(java.util.List.of("cptest.probe.label", "cptest.probe.limit")),
                    "the declared number and text nodes must be listed: " + ModPermissions.declaredMetaNodes());

            expect(ServerCommands.run(server, "customperm grade meta " + GRADE + " set cptest.probe.limit 12"),
                    "Set cptest.probe.limit=12 on " + GRADE);
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 12, "a grade's meta must answer a number node");
            expect(ServerCommands.run(server, "customperm grade meta " + GRADE + " set cptest.probe.label \"Gold rank\""),
                    "Set cptest.probe.label=Gold rank on " + GRADE);
            check("Gold rank".equals(PermissionAPI.getPermission(p, TestModNodes.LABEL)), "and a text node");
            expect(ServerCommands.run(server, "customperm grade meta " + GRADE + " set cptest.probe.limit lots"),
                    "Replaced cptest.probe.limit=12 with cptest.probe.limit=lots");
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 7, "a value that is no number leaves the default");
            expect(ServerCommands.run(server, "customperm grade meta " + GRADE + " set \"Bad Key\" 1"), "Invalid meta key");

            expect(ServerCommands.run(server, "customperm user meta cp_p_meta set cptest.probe.limit 20 1h"),
                    "Set cptest.probe.limit=20 on cp_p_meta for 1h");
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 20, "the player's own value comes first");
            check(PermissionAPI.getOfflinePermission(player.uuid(), TestModNodes.LIMIT) == 20, "offline too");
            expect(ServerCommands.run(server, "customperm user meta cp_p_meta"), "cptest.probe.limit=20 (");
            grades.userMetaExpiries.get(uuid).put("cptest.probe.limit", com.arcadia.customperm.perm.Expiry.now() - 1);
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 7, "an expired value is gone before any sweep");
            com.arcadia.customperm.admin.ExpirySweeper.sweep(server);
            check(!grades.userMeta.containsKey(uuid), "and the sweep removes it");

            expect(ServerCommands.run(server, "customperm grade meta " + GRADE + " set cptest.probe.limit 9 world=the_end"),
                    "Set cptest.probe.limit=9 on " + GRADE + " in the_end");
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 7, "a value limited to the End does not apply here");
            expect(ServerCommands.run(server, "customperm grade meta " + GRADE), "cptest.probe.limit=9 in the_end");
            expect(ServerCommands.run(server, "customperm grade meta " + GRADE + " unset cptest.probe.limit world=the_end"),
                    "Removed cptest.probe.limit=9 from " + GRADE + " in the_end");

            owner.clearReceived();
            com.arcadia.customperm.network.gui.GuiRequestHandler.handleAction(
                    new com.arcadia.customperm.network.gui.GuiActionPayload(
                            com.arcadia.customperm.network.gui.GuiAction.GRADE_META_SET.name(),
                            java.util.List.of(GRADE, "cptest.probe.limit", "33", "", ""),
                            com.arcadia.customperm.network.gui.GuiPage.GRADES.id()),
                    owner.payloadContext());
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 33, "the Grades page sets meta too");
        } finally {
            grades.grades.remove(GRADE);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userMeta.remove(uuid);
                grades.userMetaExpiries.remove(uuid);
            }
            ConfigAdmin.persist();
        }
        helper.succeed();
    }

    private static void expect(java.util.List<String> lines, String fragment) {
        check(lines.stream().anyMatch(line -> line.contains(fragment)), "expected '" + fragment + "' in " + lines);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_mod_permissions")
    public static void luckPermsKeepsTheHandler(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        check(!ModPermissions.answering(), "with LuckPerms installed CustomPerm must never take the handler");
        check("luckperms".equals(PermissionAPI.getActivePermissionHandler().getNamespace()),
                "LuckPerms must be the handler: " + PermissionAPI.getActivePermissionHandler());
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
