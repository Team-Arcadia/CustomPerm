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
            check(PermissionAPI.getPermission(p, TestModNodes.LIMIT) == 7, "a number node must answer its default");
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
