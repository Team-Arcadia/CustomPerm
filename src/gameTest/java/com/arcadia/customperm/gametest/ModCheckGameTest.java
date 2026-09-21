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
import com.arcadia.customperm.admin.ModCheck;
import com.arcadia.customperm.gametest.support.ServerCommands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The mod check on the mods really loaded, in both modes: it reads a real mod file through the loader, and it
 * leaves out LuckPerms and CustomPerm, which name LuckPerms' API for good reasons.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ModCheckGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void theModCheckReadsRealModFilesAndSkipsLuckPermsItself(GameTestHelper helper) {
        // CustomPerm's own file, read the way every mod file is: it calls both APIs, so both must be seen.
        var own = ModList.get().getModFileById(CustomPerm.MODID).getFile().getSecureJar().getRootPath();
        ModCheck.Usage usage = ModCheck.scan(own);
        check(usage.luckPermsClasses() > 0 && usage.neoForgePermissions(),
                "a real mod file must be read through the loader: " + usage);

        ModCheck.Report report;
        try {
            report = ModCheck.report().get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new GameTestAssertException("The mod check did not finish: " + e);
        }
        check(report.findings().stream().noneMatch(f -> f.mods().contains("(luckperms)") || f.mods().contains("(customperm)")),
                "LuckPerms and CustomPerm must not be reported: " + report.findings());
        check(report.unreadable().isEmpty(), "every mod file must be readable: " + report.unreadable());

        // The report is ready, so the command answers within this tick. What it says depends on what else is
        // installed: alone it reports nobody, and in a run that carries a companion mod calling the API it
        // names it. Asserting one wording would make this test fail on the mod set rather than on the check.
        List<String> lines = ServerCommands.run(helper.getLevel().getServer(), "customperm modcheck");
        check(ServerCommands.contains(lines, "No installed mod calls LuckPerms' API directly")
                        || ServerCommands.contains(lines, "call LuckPerms' API directly"),
                "the command must give the report: " + lines);
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
