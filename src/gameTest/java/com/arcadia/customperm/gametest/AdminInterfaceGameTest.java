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
import com.arcadia.customperm.admin.CommandAdmin;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.CommandsData;
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

    /** Area 3: expose, keep-original toggle and hide through the interface, mirrored by the text command. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void commandsPageExposesTogglesAndHides(GameTestHelper helper) {
        String name = "weather";
        try (TestPlayer owner = TestPlayer.join(helper.getLevel(), "cp_i_cmds", 4);
             TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_i_cmds_pl", 0);
             Grants ignored = Grants.allow(player, "customperm.command." + name)) {
            CommandsData.Row row = commandRow(owner, name);
            if (row == null || row.exposed() || row.missing()) fail("/weather must be listed, not exposed: " + row);

            owner.clearReceived();
            act(owner, GuiAction.COMMAND_EXPOSE, name);
            expectResult(owner, "OK: Exposed /weather");
            CommandsData refreshed = (CommandsData) owner.payloads(GuiPagePayload.class).get(0).data();
            if (refreshed.rows().stream().noneMatch(r -> r.name().equals(name) && r.exposed()))
                fail("The refreshed page must show /weather exposed.");
            if (!player.canUse(name)) fail("Exposing through the interface must open /weather to a node holder.");

            owner.clearReceived();
            act(owner, GuiAction.COMMAND_KEEP_ORIGINAL, name, "true");
            expectResult(owner, "OK: /weather now requires both");
            if (!CustomPerm.configManager.getCommands().shouldPreserveOriginalRequires(name))
                fail("keep-original was not stored.");
            if (player.canUse(name)) fail("With keep-original, a non-op must also pass /weather's own requirement.");

            owner.clearReceived();
            act(owner, GuiAction.COMMAND_KEEP_ORIGINAL, name, "yes");
            expectResult(owner, "FAIL: Malformed request for COMMAND_KEEP_ORIGINAL.");
            owner.type("customperm command preserve weather false");
            if (!owner.chatContains("authorised by customperm.command.weather alone"))
                fail("The text command must toggle keep-original with the same message, got " + owner.chat());
            if (!player.canUse(name)) fail("Dropping keep-original must reopen /weather to the node holder.");

            owner.clearReceived();
            act(owner, GuiAction.COMMAND_HIDE, name);
            expectResult(owner, "OK: /weather is no longer exposed.");
            if (player.canUse(name)) fail("Hiding must close /weather again.");
            if (CustomPerm.configManager.getCommands().preserveOriginalRequires.containsKey(name))
                fail("Hiding must drop the keep-original entry.");
        } finally {
            CommandAdmin.hide(helper.getLevel().getServer(), name);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void commandActionsNeedTheCommandsNode(GameTestHelper helper) {
        try (TestPlayer reader = TestPlayer.join(helper.getLevel(), "cp_i_cmds_ro", 2)) {
            reader.clearReceived();
            act(reader, GuiAction.COMMAND_EXPOSE, "difficulty");
            expectResult(reader, "FAIL: You do not have customperm.gui.commands.edit.");
            if (CustomPerm.configManager.getCommands().grantedCommands.contains("difficulty"))
                fail("A refused action exposed the command anyway.");
        } finally {
            CommandAdmin.hide(helper.getLevel().getServer(), "difficulty");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static void act(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.COMMANDS.id()),
                player.payloadContext());
    }

    private static void expectResult(TestPlayer player, String prefix) {
        List<String> results = results(player);
        if (results.size() != 1 || !results.get(0).startsWith(prefix))
            fail("Expected one result starting with '" + prefix + "', got " + results);
    }

    private static CommandsData.Row commandRow(TestPlayer player, String name) {
        player.clearReceived();
        GuiRequestHandler.open(player.player(), GuiPage.COMMANDS);
        var pages = player.payloads(GuiPagePayload.class);
        if (pages.size() != 1 || !(pages.get(0).data() instanceof CommandsData data)) {
            fail("Expected the commands page, got " + pages);
            return null;
        }
        return data.rows().stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

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
