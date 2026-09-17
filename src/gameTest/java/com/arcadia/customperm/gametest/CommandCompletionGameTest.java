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
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.perm.PermissionNodes;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab-completion of every {@code /customperm} argument, computed by the live dispatcher for a connected
 * player exactly as a client asks for it. Covers the literals added by the interface rework and every
 * custom suggestion provider, in both backends.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class CommandCompletionGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String COMMAND = "setworldspawn";
    private static final String ALIAS = "cp_s_alias";
    private static final String GRADE = "cp_s_grade";

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void everyArgumentSuggestsWhatItAccepts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var config = CustomPerm.configManager;
        try (TestPlayer op = TestPlayer.admin(helper.getLevel(), "cp_s_owner", 4)) {
            CommandAdmin.expose(server, COMMAND);
            AliasAdmin.define(server, ALIAS, List.of("say one", "say two", "say three"));
            RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
            config.getRateLimits().rules.put(ALIAS, rule);
            GradesConfig.Grade grade = new GradesConfig.Grade();
            grade.name = GRADE;
            grade.permissions.add("cp.s.allowed");
            grade.deniedPermissions.add("cp.s.denied");
            config.getGrades().grades.put(GRADE, grade);
            config.getGrades().userGrades.computeIfAbsent(op.uuid().toString(), k -> new ArrayList<>()).add(GRADE);

            List<String> problems = new ArrayList<>();
            // Literals, including the ones added by the interface rework.
            expect(problems, op, "customperm ", "gui", "log", "grade", "alias", "command", "ratelimit", "status", "reload", "scan", "test", "debug");
            expect(problems, op, "customperm log ", "admin", "players", "record", "mask");
            expect(problems, op, "customperm log record ", "true", "false");
            expect(problems, op, "customperm gui ", "dashboard", "commands", "aliases", "ratelimits", "grades", "logs", "luckperms");
            expect(problems, op, "customperm gui luckperms ", "groups", "players", "tracks");
            expect(problems, op, "customperm command ", "add", "remove", "preserve", "gateall", "list");
            expect(problems, op, "customperm command gateall ", "true", "false");
            expect(problems, op, "customperm alias ", "add", "addstep", "removestep", "movestep", "setstep", "steps", "remove", "list");
            expect(problems, op, "customperm grade ", "create", "delete", "addperm", "removeperm", "adddeny", "removedeny", "assign", "unassign",
                    "setdefault", "cleardefault", "list");
            expect(problems, op, "customperm grade setdefault ", GRADE);
            expect(problems, op, "customperm ratelimit ", "set", "persistence", "enable", "disable", "remove", "list");

            // Command exposure.
            expect(problems, op, "customperm command add weath", "weather");
            expectAbsent(problems, op, "customperm command add ", COMMAND, "customperm");
            expect(problems, op, "customperm command remove ", COMMAND);
            expect(problems, op, "customperm command preserve ", COMMAND);
            expect(problems, op, "customperm command preserve " + COMMAND + " ", "true", "false");

            // Aliases and their step indexes.
            for (String sub : List.of("addstep", "removestep", "movestep", "setstep", "steps", "remove")) {
                expect(problems, op, "customperm alias " + sub + " ", ALIAS);
            }
            expect(problems, op, "customperm alias removestep " + ALIAS + " ", "0", "1", "2");
            expect(problems, op, "customperm alias movestep " + ALIAS + " ", "0", "1", "2");
            expect(problems, op, "customperm alias movestep " + ALIAS + " 2 ", "0", "1", "2");
            expect(problems, op, "customperm alias setstep " + ALIAS + " ", "0", "1", "2");

            // Rate limits.
            expect(problems, op, "customperm ratelimit set ", COMMAND, ALIAS);
            for (String sub : List.of("persistence", "enable", "disable", "remove")) {
                expect(problems, op, "customperm ratelimit " + sub + " ", ALIAS);
            }
            expect(problems, op, "customperm ratelimit persistence " + ALIAS + " ", "world_save", "immediate");

            // Grades, nodes and players.
            for (String sub : List.of("delete", "addperm", "removeperm", "adddeny", "removedeny")) {
                expect(problems, op, "customperm grade " + sub + " ", GRADE);
            }
            expect(problems, op, "customperm grade removeperm " + GRADE + " ", "cp.s.allowed");
            expectAbsent(problems, op, "customperm grade removeperm " + GRADE + " ", "cp.s.denied");
            expect(problems, op, "customperm grade removedeny " + GRADE + " ", "cp.s.denied");
            expectAbsent(problems, op, "customperm grade removedeny " + GRADE + " ", "cp.s.allowed");
            expect(problems, op, "customperm grade addperm " + GRADE + " ", "customperm.command." + COMMAND,
                    "customperm.alias." + ALIAS, "cp.s.allowed", PermissionNodes.MANAGE_ALIASES, PermissionNodes.MANAGE_LUCKPERMS,
                    PermissionNodes.ADMIN);
            expect(problems, op, "customperm grade adddeny " + GRADE + " ", "cp.s.denied", PermissionNodes.MANAGE_GRADES);
            expect(problems, op, "customperm test cp_s_owner ", "customperm.command." + COMMAND, PermissionNodes.MANAGE_COMMANDS);
            expect(problems, op, "customperm grade assign ", "cp_s_owner");
            expect(problems, op, "customperm grade assign cp_s_owner ", GRADE);
            expect(problems, op, "customperm grade unassign cp_s_owner ", GRADE);
            expect(problems, op, "customperm debug ", "cp_s_owner");
            expect(problems, op, "customperm debug cp_s_owner ", COMMAND);

            // Typed prefixes narrow the list.
            expectAbsent(problems, op, "customperm grade delete zz", GRADE);

            if (!problems.isEmpty()) fail(problems.size() + " completion problem(s):\n  " + String.join("\n  ", problems));
        } finally {
            CommandAdmin.hide(server, COMMAND);
            AliasAdmin.remove(server, ALIAS);
            config.getRateLimits().rules.remove(ALIAS);
            config.getGrades().grades.remove(GRADE);
            config.getGrades().userGrades.values().forEach(list -> list.remove(GRADE));
            config.getGrades().userGrades.values().removeIf(List::isEmpty);
        }
        helper.succeed();
    }

    /** Suggestions reveal configuration: a player who cannot run /customperm must get none of it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nonOperatorsGetNoSuggestions(GameTestHelper helper) {
        var config = CustomPerm.configManager;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_s_player", 0)) {
            GradesConfig.Grade grade = new GradesConfig.Grade();
            grade.name = "cp_s_secret";
            config.getGrades().grades.put("cp_s_secret", grade);
            List<String> root = suggest(player, "customperm ");
            List<String> grades = suggest(player, "customperm grade delete ");
            if (!root.isEmpty() || !grades.isEmpty())
                fail("A non-operator received suggestions: " + root + " / " + grades);
        } finally {
            config.getGrades().grades.remove("cp_s_secret");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> suggest(TestPlayer player, String input) {
        var dispatcher = player.player().getServer().getCommands().getDispatcher();
        var parse = dispatcher.parse(input, player.source());
        return dispatcher.getCompletionSuggestions(parse).join().getList().stream().map(Suggestion::getText).toList();
    }

    private static void expect(List<String> problems, TestPlayer player, String input, String... wanted) {
        List<String> got = suggest(player, input);
        for (String w : wanted) {
            if (!got.contains(w)) problems.add("'" + input + "' does not suggest '" + w + "' (got " + got + ")");
        }
    }

    private static void expectAbsent(List<String> problems, TestPlayer player, String input, String... unwanted) {
        List<String> got = suggest(player, input);
        for (String u : unwanted) {
            if (got.contains(u)) problems.add("'" + input + "' wrongly suggests '" + u + "'");
        }
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
