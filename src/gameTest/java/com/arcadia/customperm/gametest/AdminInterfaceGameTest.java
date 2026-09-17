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
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.DashboardData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The native admin interface, server side, with connected players in both backends: opening pages,
 * who gets answered, the action boundary (unknown, malformed, refused) and the refresh after an action.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class AdminInterfaceGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void guiCommandOpensTheDashboard(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_i_open", 2)) {
            op.clearReceived();
            op.type("customperm gui");
            var pages = op.payloads(GuiPagePayload.class);
            if (pages.size() != 1) fail("Expected one page, got " + pages.size() + " and chat " + op.chat());
            GuiPagePayload page = pages.get(0);
            if (!page.open()) fail("A page requested by command must open on the client.");
            if (page.context().backend() != CustomPerm.backendKind()) fail("Wrong backend in the page context.");
            if (!(page.data() instanceof DashboardData dashboard)) {
                fail("/customperm gui must open the dashboard, got " + page.data().page());
                return;
            }
            var config = CustomPerm.configManager;
            if (dashboard.aliases() != config.getAliases().aliases.size()) fail("Alias count mismatch.");
            if (dashboard.exposedCommands() != config.getCommands().grantedCommands.size()) fail("Exposed count mismatch.");
            if (dashboard.rateLimits() != config.getRateLimits().rules.size()) fail("Rate limit count mismatch.");
            if (dashboard.luckPermsInstalled() != CustomPerm.isLuckPermsPresent()) fail("LuckPerms presence mismatch.");

            op.clearReceived();
            op.type("customperm gui dashboard");
            if (op.payloads(GuiPagePayload.class).size() != 1) fail("/customperm gui dashboard must open the dashboard too.");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void guiCommandExplainsAMissingClientMod(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "cp_i_vanilla");
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), profile, 2, false)) {
            op.clearReceived();
            op.type("customperm gui");
            if (!op.chatContains("needs CustomPerm installed on your client"))
                fail("A client without the mod must be told why nothing opens, got: " + op.chat());
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void nonOperatorsGetNoPageAndNoActionReply(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_i_intruder", 0)) {
            player.clearReceived();
            player.type("customperm gui");
            GuiRequestHandler.handleRequest(new GuiRequestPayload(GuiPage.DASHBOARD.id()), player.payloadContext());
            GuiRequestHandler.handleAction(action(GuiAction.RELOAD.name()), player.payloadContext());
            if (!player.payloads(GuiPagePayload.class).isEmpty()) fail("A non-operator received an admin page.");
            if (!player.payloads(GuiActionResultPayload.class).isEmpty()) fail("A non-operator's action was answered.");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void unknownAndMalformedActionsAreRejected(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_i_malformed", 4)) {
            op.clearReceived();
            GuiRequestHandler.handleAction(action("DROP_EVERYTHING"), op.payloadContext());
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.RELOAD.name(), List.of("extra"), "dashboard"),
                    op.payloadContext());
            GuiRequestHandler.handleRequest(new GuiRequestPayload("no_such_page"), op.payloadContext());
            List<String> results = results(op);
            if (!results.equals(List.of("FAIL: Unknown action.", "FAIL: Malformed request for RELOAD.")))
                fail("Unexpected results: " + results);
            if (!op.payloads(GuiPagePayload.class).isEmpty()) fail("A rejected request must not send a page.");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reloadActionReportsAndRefreshesInPlace(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_i_reload", 2)) {
            op.clearReceived();
            GuiRequestHandler.handleAction(action(GuiAction.RELOAD.name()), op.payloadContext());
            List<String> results = results(op);
            if (!results.equals(List.of("OK: Configuration reloaded successfully.")))
                fail("Reload through the interface must succeed like the command, got: " + results);
            var pages = op.payloads(GuiPagePayload.class);
            if (pages.size() != 1 || pages.get(0).open())
                fail("A successful action must push one in-place refresh (open = false), got " + pages);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void editMaskFollowsAreaNodesAndOwnerBypass(GameTestHelper helper) {
        try (TestPlayer reader = TestPlayer.join(helper.getLevel(), "cp_i_reader", 2);
             TestPlayer helperPlayer = TestPlayer.join(helper.getLevel(), "cp_i_aliashelp", 2);
             TestPlayer owner = TestPlayer.join(helper.getLevel(), "cp_i_owner", 4);
             Grants ignored = Grants.allow(helperPlayer, GuiArea.ALIASES.node())) {
            int readerMask = open(reader).context().editMask();
            int helperMask = open(helperPlayer).context().editMask();
            int ownerMask = open(owner).context().editMask();
            if (readerMask != 0) fail("Level 2 without nodes must be read-only everywhere, mask " + readerMask);
            if (helperMask != GuiArea.ALIASES.bit())
                fail("The aliases node must unlock the aliases area only, mask " + helperMask);
            int all = 0;
            for (GuiArea area : GuiArea.values()) all |= area.bit();
            if (ownerMask != all) fail("Level 4 must be able to edit every area, mask " + ownerMask);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pageRequestFloodsAreCapped(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_i_flood", 2)) {
            op.clearReceived();
            GuiRequestPayload request = new GuiRequestPayload(GuiPage.DASHBOARD.id());
            for (int i = 0; i < 60; i++) GuiRequestHandler.handleRequest(request, op.payloadContext());
            int answered = op.payloads(GuiPagePayload.class).size();
            if (answered >= 60 || answered == 0)
                fail("Page requests must be rate limited without blocking the first ones, answered " + answered);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static GuiActionPayload action(String name) {
        return new GuiActionPayload(name, List.of(), GuiPage.DASHBOARD.id());
    }

    private static GuiPagePayload open(TestPlayer player) {
        player.clearReceived();
        GuiRequestHandler.open(player.player(), GuiPage.DASHBOARD);
        var pages = player.payloads(GuiPagePayload.class);
        if (pages.size() != 1) fail("Expected one page for " + player.player().getGameProfile().getName());
        return pages.get(0);
    }

    private static List<String> results(TestPlayer player) {
        return player.payloads(GuiActionResultPayload.class).stream()
                .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message())
                .toList();
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
