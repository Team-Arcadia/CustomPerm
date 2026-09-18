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
import com.arcadia.customperm.admin.NickAdmin;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Nicknames, in both modes: a nickname is not a permission, so it must show the same whoever decides them.
 * Set by an admin, by the command and the Players page, and by a player for themselves with a node; shown in
 * place of the name, with decoration off too; refused when it would pass for another player.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class NicknameGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "customperm_names_nick")
    public static void nicknamesShowAndCannotImpersonate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        boolean decorateBefore = settings.decorateNames;
        settings.decorateNames = false;
        try (TestPlayer steve = TestPlayer.join(helper.getLevel(), "cp_k_steve", 0);
             TestPlayer guard = TestPlayer.join(helper.getLevel(), "cp_k_guard", 0);
             TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_k_owner", 4)) {
            String steveId = steve.uuid().toString();
            try {
                grades.userNicknames.remove(steveId);
                grades.userNicknames.remove(guard.uuid().toString());
                steve.player().refreshDisplayName();

                // An admin, by command: shown in place of the name, decoration off, codes read.
                says(server, "customperm user nick cp_k_steve set &bStevie", "cp_k_steve is now shown as &bStevie");
                equal("Stevie", steve.player().getDisplayName().getString(), "the nickname replaces the name");
                says(server, "customperm user nick cp_k_steve", "shown as &bStevie");
                says(server, "customperm user nick cp_k_steve set &bStevie", "no change");

                // Passing for someone: their name, whatever the case, codes or spaces; or their nickname.
                says(server, "customperm user nick cp_k_steve set &cCP_K_Guard", "reads as cp_k_guard");
                says(server, "customperm user nick cp_k_steve set cp k guard", "reads as cp_k_guard");
                says(server, "customperm user nick cp_k_steve set " + "x".repeat(17), "at most 16");
                says(server, "customperm user nick cp_k_guard set Sentinel", "now shown as Sentinel");
                says(server, "customperm user nick cp_k_steve set sentinel", "cp_k_guard's nickname");
                says(server, "customperm user nick cp_k_steve set cp_k_steve", "now shown as cp_k_steve");

                // The player themselves: only with the node, codes only with the colour node.
                check(!steve.canUse("nick"), "/nick must need customperm.nick");
                try (Grants nick = Grants.allow(steve, "customperm.nick")) {
                    check(steve.canUse("nick"), "customperm.nick must open /nick");
                    check(run(steve, "nick Bobby") == 1, "a player sets their own nickname");
                    equal("Bobby", steve.player().getDisplayName().getString(), "and it shows at once");
                    check(run(steve, "nick &cBobby") == 0, "codes need customperm.nick.color");
                    check(run(steve, "nick SENTINEL") == 0, "another player's nickname is refused to a player too");
                    equal("Bobby", NickAdmin.nickname(steve.uuid()), "a refused nickname changes nothing");
                    try (Grants color = Grants.allow(steve, "customperm.nick.color")) {
                        check(run(steve, "nick &cBobby") == 1, "customperm.nick.color allows codes");
                    }
                    check(run(steve, "nick clear") == 1, "clearing");
                    equal("cp_k_steve", steve.player().getDisplayName().getString(), "the real name comes back");
                }

                // The Players page, on either backend.
                owner.clearReceived();
                GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.USER_NICK_SET.name(),
                        List.of(steveId, "Page"), GuiPage.PLAYERS.id()), owner.payloadContext());
                List<String> results = owner.payloads(GuiActionResultPayload.class).stream()
                        .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message()).toList();
                check(results.equals(List.of("OK: cp_k_steve is now shown as Page")), "the page sets it: " + results);
                equal("Page", steve.player().getDisplayName().getString(), "set from the page, shown at once");
            } finally {
                grades.userNicknames.remove(steveId);
                grades.userNicknames.remove(guard.uuid().toString());
                settings.decorateNames = decorateBefore;
                ConfigAdmin.persist();
                steve.player().refreshDisplayName();
            }
        }
        helper.succeed();
    }

    /** The rules alone: what a nickname may be, and when it would pass for another player. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void nicknameRules(GameTestHelper helper) {
        java.util.UUID steve = java.util.UUID.fromString("00000000-0000-0000-0000-00000000000a");
        java.util.UUID alex = java.util.UUID.fromString("00000000-0000-0000-0000-00000000000b");
        java.util.Map<java.util.UUID, String> names = java.util.Map.of(steve, "Steve", alex, "Alex");
        check(NickAdmin.problem("Stevie", false) == null, "an ordinary nickname");
        check(NickAdmin.problem("x".repeat(NickAdmin.PLAIN_MAX), false) == null, "sixteen characters");
        check(NickAdmin.problem("x".repeat(NickAdmin.PLAIN_MAX + 1), false) != null, "seventeen are refused");
        check(NickAdmin.problem("&b" + "x".repeat(NickAdmin.PLAIN_MAX), true) == null, "codes do not count");
        check(NickAdmin.problem("&b&l", true) != null, "nothing visible is refused");
        check(NickAdmin.problem("two\nlines", true) != null, "a line break is refused");
        check(NickAdmin.problem("§cRed", true) != null, "the section sign is refused, like in a prefix");
        check(NickAdmin.problem("&cStevie", false) != null, "codes need customperm.nick.color");
        check(NickAdmin.problem("Tom & Jerry", false) == null, "an & that is no code is text");
        equal("Alex", NickAdmin.clash("&cA l E x", steve, names, java.util.Map.of()),
                "another player's name, whatever its case, codes or spaces");
        check(NickAdmin.clash("Steve", steve, names, java.util.Map.of()) == null, "one's own name is no impersonation");
        equal("Alex", NickAdmin.clash("a_l-e.x", steve, names, java.util.Map.of()), "separators do not tell names apart");
        java.util.Map<String, String> nicknames = java.util.Map.of(alex.toString(), "&6Guard", steve.toString(), "Stevie");
        equal("Alex's nickname", NickAdmin.clash("guard", steve, names, nicknames), "another player's nickname");
        check(NickAdmin.clash("stevie", steve, names, nicknames) == null, "one's own nickname again");
        helper.succeed();
    }

    private static int run(TestPlayer player, String command) {
        try {
            return player.exec(command);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " did not parse: " + e.getMessage());
        }
    }

    private static void says(MinecraftServer server, String command, String fragment) {
        List<String> lines = ServerCommands.run(server, command);
        if (!ServerCommands.contains(lines, fragment))
            throw new GameTestAssertException("Expected '" + fragment + "' from /" + command + ", got " + lines);
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new GameTestAssertException(message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
