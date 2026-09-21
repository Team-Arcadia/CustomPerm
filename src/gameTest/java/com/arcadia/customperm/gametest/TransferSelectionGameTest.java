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
import com.arcadia.customperm.admin.ChatGrant;
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.admin.ImportAdmin;
import com.arcadia.customperm.admin.ImportPlan;
import com.arcadia.customperm.admin.MetaGrant;
import com.arcadia.customperm.admin.TransferSelection;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.Modes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writing a selected import: replacing empties only the kinds chosen, what is not chosen stays as it was. */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class TransferSelectionGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String GRADE = "cp_ts_grade";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void replacingOnlyTheChosenKindsKeepsTheRest(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        var server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        try {
            GradeAdmin.create(GRADE);
            GradesConfig.Grade here = grades.grades.get(GRADE);
            here.permissions.add("customperm.command.home");
            here.prefixes.add(new GradesConfig.ChatEntry(10, "[Old] ", 0));
            here.meta.put("homes", "1");

            ImportPlan plan = new ImportPlan(List.of(new ImportPlan.Grade(GRADE, 5, List.of(), List.of(),
                    Set.of("customperm.command.fly"), Set.of(), List.of(new ChatGrant(false, 10, "[New] ", 0, "")),
                    Map.of(), List.of(), List.of(new MetaGrant("homes", "3", 0, "")), "")),
                    List.of(), Map.of(), Set.of(), List.of(), ImportPlan.Counts.NONE);

            var result = ImportAdmin.apply(server, plan, true, EnumSet.of(TransferSelection.Kind.NODES));
            if (!result.success()) fail("The import was refused: " + result.summary());
            here = grades.grades.get(GRADE);
            if (!here.permissions.equals(Set.of("customperm.command.fly"))) {
                fail("Replacing the nodes must empty them first, then write the imported ones: " + here.permissions);
            }
            if (here.prefixes.size() != 1 || !here.prefixes.get(0).text.equals("[Old] ")) {
                fail("A prefix must stay when prefixes are not chosen, replace or not: " + here.prefixes.size());
            }
            if (!"1".equals(here.meta.get("homes"))) fail("Meta must stay when it is not chosen: " + here.meta);

            ImportAdmin.apply(server, plan, true, TransferSelection.ALL.kinds());
            here = grades.grades.get(GRADE);
            if (here.prefixes.size() != 1 || !here.prefixes.get(0).text.equals("[New] ") || !"3".equals(here.meta.get("homes"))) {
                fail("Replacing every kind must take the imported prefix and meta.");
            }
        } finally {
            GradeAdmin.delete(server, GRADE);
        }
        helper.succeed();
    }

    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }
}
