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
import com.arcadia.customperm.admin.AdminResult;
import com.arcadia.customperm.admin.ImportAdmin;
import com.arcadia.customperm.admin.ImportPlan;
import com.arcadia.customperm.config.CommandsConfig;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.LuckPermsTestSupport;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.perm.lp.LuckPermsImport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.arcadia.customperm.gametest.support.LuckPermsTestSupport.apply;

/**
 * The import, against a real LuckPerms: what it brings over, what it leaves behind and why, and what
 * lands in the configuration once it is applied. LuckPerms mode only, it being what is read.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class LuckPermsImportGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String BASE = "cp_m_base";
    private static final String VIP = "cp_m_vip";
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void importBringsWhatItCanAndSaysWhatItCannot(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        GradesConfig grades = CustomPerm.configManager.getGrades();
        CommandsConfig commands = CustomPerm.configManager.getCommands();
        Set<String> gradesBefore = new HashSet<>(grades.grades.keySet());
        Set<String> commandsBefore = new HashSet<>(commands.grantedCommands);
        Set<String> holdersBefore = new HashSet<>(grades.userGrades.keySet());
        Set<String> nodeHoldersBefore = new HashSet<>(grades.userPermissions.keySet());
        try {
            // A source with one of everything: what carries over, and one of each reason to leave a node.
            apply(LpEditOp.GROUP_CREATE, BASE);
            apply(LpEditOp.GROUP_CREATE, VIP);
            apply(LpEditOp.GROUP_PERM_ADD, BASE, "minecraft.command.gamemode", "true", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, BASE, "customperm.command.time", "true", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, BASE, "customperm.command.ban", "false", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, BASE, "essentials.fly", "true", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.kick", "true", "", "3600");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.seed", "true", "world=nether", "0");
            apply(LpEditOp.GROUP_PREFIX_SET, VIP, "10", "[VIP]", "");
            apply(LpEditOp.GROUP_PARENT_ADD, VIP, BASE, "");
            apply(LpEditOp.GROUP_WEIGHT_SET, VIP, "42");
            apply(LpEditOp.USER_PARENT_ADD, USER.toString(), VIP, "", "0");
            apply(LpEditOp.USER_PERM_ADD, USER.toString(), "minecraft.command.weather", "true", "", "0");

            ImportPlan plan = LuckPermsTestSupport.await(LuckPermsImport.read(true));

            ImportPlan.Grade base = grade(plan, BASE);
            if (!base.allow().contains("customperm.command.gamemode"))
                fail("minecraft.command.gamemode must arrive as customperm.command.gamemode: " + base);
            if (!base.allow().contains("customperm.command.time"))
                fail("A customperm node must carry over as it is: " + base);
            if (!base.deny().contains("customperm.command.ban"))
                fail("A node set to false must arrive as a denial: " + base);
            if (base.allow().contains("essentials.fly") || base.allow().contains("customperm.command.fly"))
                fail("A node another mod reads must not be imported: " + base);

            ImportPlan.Grade vip = grade(plan, VIP);
            if (vip.weight() != 42) fail("The group weight must arrive as the grade weight: " + vip);
            if (!vip.parents().equals(List.of(BASE))) fail("The group parent must arrive as a grade parent: " + vip);
            if (!vip.allow().isEmpty())
                fail("A temporary and a contextual node must both be left behind: " + vip);

            if (!plan.exposeCommands().contains("gamemode") || !plan.exposeCommands().contains("weather"))
                fail("A translated node must expose its command, or it grants nothing: " + plan.exposeCommands());
            if (plan.exposeCommands().contains("seed") || plan.exposeCommands().contains("kick"))
                fail("A node that was not imported must not expose anything: " + plan.exposeCommands());

            ImportPlan.Player player = plan.players().stream()
                    .filter(p -> p.uuid().equals(USER.toString())).findFirst().orElse(null);
            if (player == null || !player.grades().contains(VIP)
                    || !player.allow().contains("customperm.command.weather"))
                fail("The user's group and own node must both arrive: " + player);

            if (plan.counts().temporary() < 1 || plan.counts().contextual() < 1
                    || plan.counts().foreign() < 1 || plan.counts().other() < 1)
                fail("Each reason to leave a node behind must be counted: " + plan.counts());
            String report = String.join(" | ", plan.report());
            if (!report.contains("left behind")) fail("The report must say what it leaves behind: " + report);

            // Nothing is written until it is applied.
            if (grades.grades.containsKey(BASE)) fail("Reading must not write anything.");

            AdminResult result = ImportAdmin.apply(helper.getLevel().getServer(), plan, false);
            if (!result.success()) fail("Applying the plan failed: " + result.message());
            if (!grades.grades.containsKey(BASE) || !grades.grades.containsKey(VIP))
                fail("The grades were not written.");
            if (grades.grades.get(VIP).weight != 42 || !grades.grades.get(VIP).parents.contains(BASE))
                fail("The weight and the parent were not written.");
            if (!grades.grades.get(BASE).deniedPermissions.contains("customperm.command.ban"))
                fail("The denial was not written.");
            if (!grades.userGrades.getOrDefault(USER.toString(), List.of()).contains(VIP))
                fail("The player was not assigned.");
            if (!commands.grantedCommands.contains("gamemode") || !commands.grantedCommands.contains("weather"))
                fail("The commands the imported nodes need were not exposed.");
            if (result.notes().stream().noneMatch(note -> note.contains("LuckPerms still decides")))
                fail("The result must say the imported grades decide nothing yet: " + result.notes());
        } finally {
            grades.grades.keySet().retainAll(gradesBefore);
            commands.grantedCommands.retainAll(commandsBefore);
            grades.userGrades.keySet().retainAll(holdersBefore);
            grades.userDeniedGrades.keySet().retainAll(holdersBefore);
            grades.userPermissions.keySet().retainAll(nodeHoldersBefore);
            grades.userDeniedPermissions.keySet().retainAll(nodeHoldersBefore);
            LuckPermsTestSupport.cleanup(List.of(BASE, VIP), List.of());
        }
        helper.succeed();
    }

    private static ImportPlan.Grade grade(ImportPlan plan, String name) {
        return plan.grades().stream().filter(g -> g.name().equals(name)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("The plan has no grade " + name + ". It has "
                        + plan.grades().stream().map(ImportPlan.Grade::name).toList()
                        + " and says: " + String.join(" | ", plan.report())));
    }

    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }
}
