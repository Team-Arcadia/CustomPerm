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
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.admin.NameAdmin;
import com.arcadia.customperm.admin.UserAdmin;
import com.arcadia.customperm.chat.LegacyText;
import com.arcadia.customperm.chat.NameDecoration;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.SettingsConfig;
import com.arcadia.customperm.gametest.support.LuckPermsTestSupport;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.lp.LpEditOp;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chat prefixes and suffixes around a player's name: the {@code &} codes, the format, and the name the
 * game really builds, which is the one vanilla binds to a chat message. The message itself is never
 * touched, so these tests look at the name and nothing else.
 *
 * <p>The live tests flip {@code decorateNames}, a server-wide setting: each has its own batch.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class NameDecorationGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final String VIP = "cp_n_vip";
    private static final String MEMBER = "cp_n_member";
    private static final String LP_GROUP = "cp_n_lpgroup";

    // ------------------------------------------------------------------ text

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void codesBecomeStylesAndNeverReachTheClientRaw(GameTestHelper helper) {
        equal("[VIP] ", LegacyText.plain("&6&l[VIP]&r "), "codes leave the text");
        List<Run> runs = runs(LegacyText.parse("&6&l[VIP]&r "));
        equal(TextColor.fromLegacyFormat(ChatFormatting.GOLD), runs.get(0).style().getColor(), "&6 is gold");
        check(runs.get(0).style().isBold(), "&l is bold");
        equal(Style.EMPTY, runs.get(1).style(), "&r resets everything");
        check(!runs(LegacyText.parse("&lA&cB")).get(1).style().isBold(), "a colour clears the formats before it");
        equal(TextColor.fromRgb(0xff8800), runs(LegacyText.parse("&#ff8800Admin")).get(0).style().getColor(),
                "&#RRGGBB is read");
        equal("Tom & Jerry &z &#12", LegacyText.plain("Tom & Jerry &z &#12"), "an & that is no code stays text");
        equal("acblc", LegacyText.plain("a§cb§lc"), "a section sign is dropped, never read as a code");
        check(LegacyText.problem("§c[VIP]") != null, "a section sign is refused when typed");
        check(LegacyText.problem("[VIP]\n") != null, "a line break is refused");
        check(LegacyText.problem("x".repeat(LegacyText.MAX_LENGTH + 1)) != null, "an over-long prefix is refused");
        check(LegacyText.problem("&6[VIP] ") == null, "an ordinary prefix is accepted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void theFormatPutsThePrefixAndSuffixAroundTheName(GameTestHelper helper) {
        Component steve = Component.literal("Steve").withStyle(ChatFormatting.RED);
        equal("[VIP] Steve*", NameDecoration.format("{prefix}{name}{suffix}", "[VIP] ", steve, "*").getString(),
                "the default format");
        equal("Steve", NameDecoration.format("{prefix}{name}{suffix}", null, steve, null).getString(), "nothing to add");
        equal("[VIP] | Steve", NameDecoration.format("{prefix}&7| {name}", "[VIP] ", steve, null).getString(),
                "text between the placeholders");
        equal("[VIP]Steve", NameDecoration.format("{prefix}", "[VIP]", steve, null).getString(),
                "a format hiding the name must never be used");
        Run name = runs(NameDecoration.format("{prefix}{name}", "&a[A] ", steve, null)).stream()
                .filter(r -> r.text().equals("Steve")).findFirst().orElseThrow();
        equal(TextColor.fromLegacyFormat(ChatFormatting.RED), name.style().getColor(), "the name keeps its own style");
        helper.succeed();
    }

    // ------------------------------------------------------------------ live, internal grades

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_names_internal")
    public static void gradesDecorateTheNameChatUses(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        GradesConfig grades = CustomPerm.configManager.getGrades();
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        boolean decorateBefore = settings.decorateNames;
        String formatBefore = settings.nameFormat;
        String uuid = null;
        try (TestPlayer steve = TestPlayer.join(helper.getLevel(), "cp_n_steve", 0)) {
            ServerPlayer player = steve.player();
            uuid = player.getUUID().toString();
            ok(GradeAdmin.create(VIP));
            ok(GradeAdmin.create(MEMBER));
            ok(GradeAdmin.setWeight(server, VIP, 10));
            ok(GradeAdmin.setChat(server, VIP, false, "&6[VIP] "));
            AdminResult member = GradeAdmin.setChat(server, MEMBER, false, "[Member] ");
            ok(member);
            check(member.notes().stream().anyMatch(n -> n.contains("/customperm names on")),
                    "a prefix set while names are not decorated must say so: " + member.notes());
            ok(GradeAdmin.setChat(server, MEMBER, true, " &7*"));
            grades.userGrades.put(player.getUUID().toString(), new ArrayList<>(List.of(MEMBER, VIP)));
            NameDecoration.refresh(player);
            equal("cp_n_steve", player.getDisplayName().getString(), "nothing is decorated while it is off");

            ok(NameAdmin.setEnabled(server, true));
            equal("[VIP] cp_n_steve *", player.getDisplayName().getString(),
                    "the heaviest grade's prefix, the only suffix");
            equal("[VIP] cp_n_steve *", ChatType.bind(ChatType.CHAT, player).name().getString(),
                    "the name vanilla binds to a chat message is the decorated one");
            Component tab = player.getTabListDisplayName();
            check(tab != null && tab.getString().equals("[VIP] cp_n_steve *"), "the tab list shows it too: " + tab);

            ok(UserAdmin.setChat(server, player.getUUID(), "cp_n_steve", false, "&d[Me] "));
            equal("[Me] cp_n_steve *", player.getDisplayName().getString(), "the player's own prefix wins, at once");

            ok(NameAdmin.setFormat(server, "{prefix}&8| {name}{suffix}"));
            equal("[Me] | cp_n_steve *", player.getDisplayName().getString(), "the format applies at once");
            check(!NameAdmin.setFormat(server, "{prefix}").success(), "a format without the name must be refused");

            ok(NameAdmin.setEnabled(server, false));
            equal("cp_n_steve", player.getDisplayName().getString(), "turning it off gives the plain name back");
            check(player.getTabListDisplayName() == null, "and the plain tab list entry");
        } finally {
            settings.decorateNames = decorateBefore;
            settings.nameFormat = formatBefore;
            grades.grades.remove(VIP);
            grades.grades.remove(MEMBER);
            if (uuid != null) {
                grades.userGrades.remove(uuid);
                grades.userPrefixes.remove(uuid);
                grades.userSuffixes.remove(uuid);
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ live, LuckPerms

    /**
     * LuckPerms stores prefixes and nothing on NeoForge shows them: with decoration on, the one its meta
     * stacking picks is on the name, and a change made in LuckPerms reaches the name without a reconnect.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "customperm_names_luckperms")
    public static void luckPermsPrefixesDecorateTheName(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        SettingsConfig settings = CustomPerm.configManager.getSettings();
        boolean decorateBefore = settings.decorateNames;
        try (TestPlayer alex = TestPlayer.join(helper.getLevel(), "cp_n_alex", 0)) {
            ServerPlayer player = alex.player();
            settings.decorateNames = true;
            LuckPermsTestSupport.apply(LpEditOp.GROUP_CREATE, LP_GROUP);
            LuckPermsTestSupport.apply(LpEditOp.GROUP_PREFIX_SET, LP_GROUP, "10", "&c[LP] ", "");
            LuckPermsTestSupport.apply(LpEditOp.USER_PARENT_ADD, player.getUUID().toString(), LP_GROUP, "", "0");
            awaitName(server, player, "[LP] cp_n_alex");
            check(GradeAdmin.setChat(server, "anything", false, "[X]").message().contains("/lp"),
                    "with LuckPerms, prefixes are set in LuckPerms");
        } finally {
            settings.decorateNames = decorateBefore;
            LuckPermsTestSupport.cleanup(List.of(LP_GROUP), List.of());
        }
        helper.succeed();
    }

    /**
     * The name is rebuilt when LuckPerms recalculates the user, off the server thread then scheduled on
     * it: the wait drains that queue rather than sleeping on the thread that has to run it.
     */
    private static void awaitName(MinecraftServer server, ServerPlayer player, String expected) {
        long deadline = System.currentTimeMillis() + 5000;
        server.managedBlock(() -> player.getDisplayName().getString().equals(expected)
                || System.currentTimeMillis() > deadline);
        equal(expected, player.getDisplayName().getString(), "the LuckPerms prefix must reach the name");
    }

    // ------------------------------------------------------------------ helpers

    private record Run(String text, Style style) {
    }

    private static List<Run> runs(Component component) {
        List<Run> runs = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) runs.add(new Run(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private static void ok(AdminResult result) {
        if (!result.success()) throw new GameTestAssertException("Refused: " + result.message());
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new GameTestAssertException(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
