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
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.AliasesData;
import com.arcadia.customperm.network.gui.CommandsData;
import com.arcadia.customperm.network.gui.DashboardData;
import com.arcadia.customperm.network.gui.GradesData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import com.arcadia.customperm.network.gui.LuckPermsData;
import com.arcadia.customperm.network.gui.PlayersData;
import com.arcadia.customperm.network.gui.RateLimitsData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
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
        try (TestPlayer op = TestPlayer.reader(helper.getLevel(), "cp_i_open", 2)) {
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
        try (TestPlayer op = TestPlayer.admin(helper.getLevel(), profile, 2, false)) {
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
        try (TestPlayer op = TestPlayer.admin(helper.getLevel(), "cp_i_malformed", 4)) {
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
        try (TestPlayer op = TestPlayer.admin(helper.getLevel(), "cp_i_reload", 2)) {
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
        try (TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_i_reader", 2);
             TestPlayer helperPlayer = TestPlayer.reader(helper.getLevel(), "cp_i_aliashelp", 2);
             TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_owner", 4);
             Grants ignored = Grants.allow(helperPlayer, GuiArea.ALIASES.node())) {
            int readerMask = open(reader).context().editMask();
            int helperMask = open(helperPlayer).context().editMask();
            int ownerMask = open(owner).context().editMask();
            if (readerMask != 0) fail("customperm.admin alone must be read-only everywhere, mask " + readerMask);
            if (helperMask != GuiArea.ALIASES.bit())
                fail("The aliases node must unlock the aliases area only, mask " + helperMask);
            int all = 0;
            for (GuiArea area : GuiArea.values()) all |= area.bit();
            if (ownerMask != all) fail("customperm.* must be able to edit every area, mask " + ownerMask);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void pageRequestFloodsAreCapped(GameTestHelper helper) {
        try (TestPlayer op = TestPlayer.reader(helper.getLevel(), "cp_i_flood", 2)) {
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
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_cmds", 4);
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
        try (TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_i_cmds_ro", 2)) {
            reader.clearReceived();
            act(reader, GuiAction.COMMAND_EXPOSE, "difficulty");
            expectResult(reader, "FAIL: You do not have customperm.manage.commands.");
            if (CustomPerm.configManager.getCommands().grantedCommands.contains("difficulty"))
                fail("A refused action exposed the command anyway.");
        } finally {
            CommandAdmin.hide(helper.getLevel().getServer(), "difficulty");
        }
        helper.succeed();
    }

    /** Area 4: the whole alias editing cycle through the interface, then the new text commands. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void aliasesPageEditsStepsEndToEnd(GameTestHelper helper) {
        String name = "cp_i_macro";
        var aliases = CustomPerm.configManager.getAliases().aliases;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_alias", 4)) {
            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_CREATE, name, "say one");
            expectResult(owner, "OK: Alias /cp_i_macro set with 1 step(s).");
            if (!aliases.get(name).equals(List.of("say one"))) fail("Unexpected steps after creation: " + aliases.get(name));
            if (!owner.canUse(name)) fail("A created alias must be registered live.");

            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_CREATE, name, "say again");
            expectResult(owner, "FAIL: Alias /cp_i_macro already exists.");

            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_STEP_ADD, name, "say two");
            aliasAct(owner, GuiAction.ALIAS_STEP_ADD, name, "say three");
            aliasAct(owner, GuiAction.ALIAS_STEP_MOVE, name, "2", "0");
            aliasAct(owner, GuiAction.ALIAS_STEP_SET, name, "1", "say uno");
            aliasAct(owner, GuiAction.ALIAS_STEP_REMOVE, name, "2");
            if (!aliases.get(name).equals(List.of("say three", "say uno")))
                fail("Add, move, set and remove produced " + aliases.get(name));
            var pages = owner.payloads(GuiPagePayload.class);
            if (pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof AliasesData data)
                    || data.aliases().stream().noneMatch(a -> a.name().equals(name) && a.steps().equals(List.of("say three", "say uno"))))
                fail("The refreshed aliases page must carry the edited steps.");

            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_STEP_MOVE, name, "-1", "0");
            aliasAct(owner, GuiAction.ALIAS_STEP_SET, name, "x", "say bad");
            aliasAct(owner, GuiAction.ALIAS_STEP_REMOVE, name, "9");
            List<String> results = results(owner);
            if (!results.equals(List.of("FAIL: Malformed request for ALIAS_STEP_MOVE.",
                    "FAIL: Malformed request for ALIAS_STEP_SET.", "FAIL: Index out of range (0..1)")))
                fail("Bad indexes must be refused, got " + results);

            owner.clearReceived();
            owner.type("customperm alias movestep cp_i_macro 1 0");
            owner.type("customperm alias setstep cp_i_macro 1 say last");
            if (!aliases.get(name).equals(List.of("say uno", "say last")))
                fail("movestep and setstep text commands produced " + aliases.get(name) + " / " + owner.chat());

            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_DELETE, name);
            expectResult(owner, "OK: Removed alias /cp_i_macro");
            if (aliases.containsKey(name) || owner.canUse(name)) fail("A deleted alias must be gone from config and dispatcher.");
        } finally {
            AliasAdmin.remove(helper.getLevel().getServer(), name);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasActionsNeedTheAliasesNodeAndAValidName(GameTestHelper helper) {
        try (TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_i_alias_ro", 2);
             TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_alias_ow", 4)) {
            reader.clearReceived();
            aliasAct(reader, GuiAction.ALIAS_CREATE, "cp_i_denied", "say no");
            expectResult(reader, "FAIL: You do not have customperm.manage.aliases.");
            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_CREATE, "bad name", "say no");
            expectResult(owner, "FAIL: Invalid alias name 'bad name'");
            owner.clearReceived();
            aliasAct(owner, GuiAction.ALIAS_CREATE, "customperm", "say no");
            expectResult(owner, "FAIL: Reserved name.");
            if (CustomPerm.configManager.getAliases().aliases.containsKey("cp_i_denied"))
                fail("A refused creation created the alias.");
        } finally {
            AliasAdmin.remove(helper.getLevel().getServer(), "cp_i_denied");
        }
        helper.succeed();
    }

    /** Area 5: the rate limit cycle through the interface, mirrored in config and in the page. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void rateLimitsPageEditsRules(GameTestHelper helper) {
        String alias = "cp_i_limited";
        var server = helper.getLevel().getServer();
        var rules = CustomPerm.configManager.getRateLimits().rules;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_limits", 4)) {
            AliasAdmin.define(server, alias, List.of("say limited"));
            RateLimitsData before = rateLimitsPage(owner);
            if (!before.unlimited().contains(alias)) fail("An alias without a rule must be offered as a target.");

            owner.clearReceived();
            limitAct(owner, GuiAction.RATELIMIT_SET, alias, "3", "60");
            expectResult(owner, "OK: Rate limit for /cp_i_limited set to 3 per 60s (enabled).");
            limitAct(owner, GuiAction.RATELIMIT_PERSISTENCE, alias, "immediate");
            limitAct(owner, GuiAction.RATELIMIT_SET, alias, "5", "120");
            if (!rules.get(alias).persistsImmediately() || rules.get(alias).maxExecutions != 5)
                fail("Redefining a rule must keep its persistence mode and apply the new numbers.");
            limitAct(owner, GuiAction.RATELIMIT_DISABLE, alias);
            if (rules.get(alias).enabled) fail("Disable did not apply.");
            limitAct(owner, GuiAction.RATELIMIT_ENABLE, alias);
            if (!rules.get(alias).enabled) fail("Enable did not apply.");

            RateLimitsData after = rateLimitsPage(owner);
            RateLimitsData.Rule row = after.rules().stream().filter(r -> r.name().equals(alias)).findFirst().orElse(null);
            if (row == null || row.max() != 5 || row.windowSeconds() != 120 || !row.enabled() || !row.immediate()
                    || row.target() != RateLimitsData.Target.ALIAS || after.unlimited().contains(alias))
                fail("The page does not reflect the rule: " + row);

            owner.clearReceived();
            limitAct(owner, GuiAction.RATELIMIT_SET, alias, "0", "60");
            limitAct(owner, GuiAction.RATELIMIT_SET, alias, "2", "-5");
            limitAct(owner, GuiAction.RATELIMIT_PERSISTENCE, alias, "sometimes");
            List<String> results = results(owner);
            if (!results.equals(List.of("FAIL: A rate limit needs at least 1 use per window of at least 1 second.",
                    "FAIL: Malformed request for RATELIMIT_SET.",
                    "FAIL: Unknown persistence mode 'sometimes'. Use world_save or immediate.")))
                fail("Invalid numbers and modes must be refused, got " + results);

            owner.clearReceived();
            limitAct(owner, GuiAction.RATELIMIT_REMOVE, alias);
            expectResult(owner, "OK: Rate limit for /cp_i_limited removed.");
            if (rules.containsKey(alias)) fail("Remove did not apply.");
        } finally {
            rules.remove(alias);
            AliasAdmin.remove(server, alias);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void rateLimitActionsNeedTheRateLimitsNode(GameTestHelper helper) {
        try (TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_i_limits_ro", 2)) {
            reader.clearReceived();
            limitAct(reader, GuiAction.RATELIMIT_SET, "cp_i_denied_rule", "1", "60");
            expectResult(reader, "FAIL: You do not have customperm.manage.ratelimits.");
            if (CustomPerm.configManager.getRateLimits().rules.containsKey("cp_i_denied_rule"))
                fail("A refused action created the rule.");
        } finally {
            CustomPerm.configManager.getRateLimits().rules.remove("cp_i_denied_rule");
        }
        helper.succeed();
    }

    /** Area 6: grades, ALLOW and DENY nodes, online and offline assignment, deletion. Internal backend only. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gradesPageEditsNodesAndPlayers(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        String grade = "cp_i_grade";
        var server = helper.getLevel().getServer();
        var config = CustomPerm.configManager.getGrades();
        // Unique name per run: the username cache outlives GameTest runs and has no removal, and a name
        // reused with another UUID is rightly refused as ambiguous.
        String offlineName = "cp_o" + Long.toHexString(System.nanoTime() & 0xFFFFFFFFFFFL);
        GameProfile offline = new GameProfile(UUID.randomUUID(), offlineName);
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_grades", 4);
             TestPlayer member = TestPlayer.join(helper.getLevel(), "cp_i_member", 0)) {
            // A player who joined once and left: known to the server, not online.
            TestPlayer.join(helper.getLevel(), offline, 0, true).close();

            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_CREATE, grade);
            gradeAct(owner, GuiAction.GRADE_NODE_ADD, grade, "customperm.command.weather", "allow", "", "");
            gradeAct(owner, GuiAction.GRADE_NODE_ADD, grade, "customperm.command.time", "deny", "", "");
            gradeAct(owner, GuiAction.GRADE_NODE_ADD, grade, "customperm.command.time", "maybe", "", "");
            gradeAct(owner, GuiAction.GRADE_ASSIGN, "CP_I_MEMBER", grade, "", "");
            gradeAct(owner, GuiAction.GRADE_ASSIGN, offlineName, grade, "", "");
            gradeAct(owner, GuiAction.GRADE_ASSIGN, "cp_i_nobody_here", grade, "", "");
            List<String> results = results(owner);
            if (!results.equals(List.of("OK: Created grade cp_i_grade",
                    "OK: Added customperm.command.weather -> cp_i_grade",
                    "OK: Denied customperm.command.time -> cp_i_grade",
                    "FAIL: Malformed request for GRADE_NODE_ADD.",
                    "OK: Assigned cp_i_grade -> cp_i_member",
                    "OK: Assigned cp_i_grade -> " + offlineName,
                    "FAIL: Unknown player 'cp_i_nobody_here': grades can be assigned to players online or who joined this server before.")))
                fail("Unexpected results: " + results);
            if (!config.grades.get(grade).deniedPermissions.contains("customperm.command.time"))
                fail("The DENY node was not stored as a denial.");
            if (!config.userGrades.getOrDefault(offline.getId().toString(), List.of()).contains(grade))
                fail("The offline player was not assigned by UUID.");

            var pages = owner.payloads(GuiPagePayload.class);
            GradesData.Grade row = pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof GradesData data) ? null
                    : data.grades().stream().filter(g -> g.name().equals(grade)).findFirst().orElse(null);
            if (row == null || row.members().size() != 2
                    || row.members().stream().noneMatch(m -> m.name().equals(offlineName) && !m.online())
                    || row.members().stream().noneMatch(m -> m.name().equals("cp_i_member") && m.online()))
                fail("The refreshed page must list both players with their state: " + row);

            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_DISPLAYNAME_SET, grade, " Grade Page ");
            expectResult(owner, "OK: cp_i_grade is now shown as Grade Page");
            pages = owner.payloads(GuiPagePayload.class);
            row = pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof GradesData named) ? null
                    : named.grades().stream().filter(g -> g.name().equals(grade)).findFirst().orElse(null);
            if (row == null || !row.header().displayName().equals("Grade Page") || !row.shown().equals("Grade Page"))
                fail("The refreshed page must carry the display name, the grade keeping its name: " + row);

            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_UNASSIGN, offline.getId().toString(), grade, "");
            gradeAct(owner, GuiAction.GRADE_UNASSIGN, "not-a-uuid", grade, "");
            owner.type("customperm grade adddeny cp_i_grade customperm.command.seed");
            owner.type("customperm grade assign " + offlineName + " cp_i_grade");
            results = results(owner);
            if (!results.equals(List.of("OK: Unassigned cp_i_grade from " + offlineName, "FAIL: Malformed request for GRADE_UNASSIGN.")))
                fail("Unexpected unassign results: " + results);
            if (!config.grades.get(grade).deniedPermissions.contains("customperm.command.seed")
                    || !config.userGrades.getOrDefault(offline.getId().toString(), List.of()).contains(grade))
                fail("adddeny and offline assign text commands did not apply: " + owner.chat());

            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_DELETE, grade);
            expectResult(owner, "OK: Deleted grade cp_i_grade");
            if (config.userGrades.values().stream().anyMatch(list -> list.contains(grade)))
                fail("Deleting a grade must unassign it from every player.");
        } finally {
            GradeAdmin.delete(server, grade);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void gradeActionsAreRefusedUnderLuckPermsOrWithoutTheNode(GameTestHelper helper) {
        try (TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_i_grades_ro", 2);
             TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_grades_ow", 4)) {
            reader.clearReceived();
            gradeAct(reader, GuiAction.GRADE_CREATE, "cp_i_denied_grade");
            expectResult(reader, "FAIL: You do not have customperm.manage.grades.");
            if (CustomPerm.isLuckPermsActive()) {
                owner.clearReceived();
                gradeAct(owner, GuiAction.GRADE_CREATE, "cp_i_denied_grade");
                expectResult(owner, "FAIL: [CustomPerm] Grade commands are disabled");
            }
            if (CustomPerm.configManager.getGrades().grades.containsKey("cp_i_denied_grade"))
                fail("A refused action created the grade.");
        } finally {
            CustomPerm.configManager.getGrades().grades.remove("cp_i_denied_grade");
        }
        helper.succeed();
    }

    /** Area 7: the LuckPerms editor page exists only when LuckPerms is installed, whichever way it is asked for. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void luckPermsEditorOpensOnlyWithLuckPerms(GameTestHelper helper) {
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_lpeditor", 4)) {
            owner.clearReceived();
            owner.type("customperm gui luckperms players");
            GuiRequestHandler.handleRequest(new GuiRequestPayload(GuiPage.LUCKPERMS.id()), owner.payloadContext());
            var pages = owner.payloads(GuiPagePayload.class);
            if (pages.stream().anyMatch(p -> p.context().luckPermsInstalled() != CustomPerm.isLuckPermsPresent()))
                fail("The page context must say whether LuckPerms is installed.");
            if (CustomPerm.isLuckPermsPresent()) {
                if (pages.size() != 2 || !(pages.get(0).data() instanceof LuckPermsData first)
                        || !first.section().equals(LuckPermsData.PLAYERS) || !pages.get(0).open())
                    fail("With LuckPerms active the command must open the editor on the players section, got " + pages);
                if (!(pages.get(1).data() instanceof LuckPermsData)) fail("The page request must answer with the editor.");
            } else {
                if (!pages.isEmpty()) fail("Without LuckPerms no editor page may be sent, got " + pages);
                if (!owner.chatContains("only available when LuckPerms is installed"))
                    fail("Without LuckPerms the command must explain why nothing opens, got " + owner.chat());
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The Players page: nodes carried by one player, added by name and removed by UUID, refreshed in place,
     * and refused for a player the server has never seen.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void playersPageEditsTheNodesOnePlayerCarries(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        var config = CustomPerm.configManager.getGrades();
        String member = null;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_players", 4);
             TestPlayer target = TestPlayer.join(helper.getLevel(), "cp_i_target", 0)) {
            member = target.uuid().toString();

            owner.clearReceived();
            userAct(owner, GuiAction.USER_NODE_ADD, "CP_I_TARGET", "customperm.command.weather", "allow", "", "");
            userAct(owner, GuiAction.USER_NODE_ADD, "cp_i_target", "customperm.command.time", "deny", "", "");
            userAct(owner, GuiAction.USER_NODE_ADD, "cp_i_target", "customperm.command.time", "maybe", "", "");
            userAct(owner, GuiAction.USER_NODE_ADD, "cp_i_nobody_here", "customperm.command.time", "allow", "", "");
            List<String> results = results(owner);
            if (!results.equals(List.of("OK: Added customperm.command.weather -> cp_i_target",
                    "OK: Denied customperm.command.time -> cp_i_target",
                    "FAIL: Malformed request for USER_NODE_ADD.",
                    "FAIL: Unknown player 'cp_i_nobody_here': grades can be assigned to players online or who joined this server before.")))
                fail("Unexpected results: " + results);
            if (!config.userPermissions.getOrDefault(member, Set.of()).contains("customperm.command.weather")
                    || !config.userDeniedPermissions.getOrDefault(member, Set.of()).contains("customperm.command.time"))
                fail("The nodes were not stored on the player.");

            var pages = owner.payloads(GuiPagePayload.class);
            PlayersData.Player row = pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof PlayersData data)
                    ? null
                    : data.players().stream().filter(p -> p.name().equals("cp_i_target")).findFirst().orElse(null);
            if (row == null || !row.online() || !row.allow().contains("customperm.command.weather")
                    || !row.deny().contains("customperm.command.time"))
                fail("The refreshed page must list the player with their own nodes: " + row);

            owner.clearReceived();
            userAct(owner, GuiAction.USER_NODE_REMOVE, member, "customperm.command.weather", "allow", "");
            userAct(owner, GuiAction.USER_NODE_REMOVE, "not-a-uuid", "customperm.command.time", "deny", "");
            results = results(owner);
            if (!results.equals(List.of("OK: Removed customperm.command.weather from cp_i_target",
                    "FAIL: Malformed request for USER_NODE_REMOVE.")))
                fail("Unexpected removal results: " + results);
            if (config.userPermissions.containsKey(member))
                fail("An entry left without a node must be dropped, not kept empty.");

            // Same node through the text command, on the same store.
            owner.clearReceived();
            owner.type("customperm user removedeny cp_i_target customperm.command.time");
            owner.type("customperm user list cp_i_target");
            if (config.userDeniedPermissions.containsKey(member))
                fail("The text command did not remove the denial: " + owner.chat());
            if (owner.chat().stream().noneMatch(line -> line.contains("own allow: none")))
                fail("user list must report what the player carries: " + owner.chat());
        } finally {
            if (member != null) {
                config.userPermissions.remove(member);
                config.userDeniedPermissions.remove(member);
            }
        }
        helper.succeed();
    }

    /** The Players page writes through the grades node, and the lockout guard covers it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void userNodeActionsNeedTheGradesNodeAndCannotLockTheAdminOut(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        var config = CustomPerm.configManager.getGrades();
        String owner = null;
        try (TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_i_users_ro", 2);
             TestPlayer admin = TestPlayer.admin(helper.getLevel(), "cp_i_users_ow", 4)) {
            owner = admin.uuid().toString();
            reader.clearReceived();
            userAct(reader, GuiAction.USER_NODE_ADD, "cp_i_users_ro", "customperm.command.weather", "allow", "", "");
            expectResult(reader, "FAIL: You do not have customperm.manage.grades.");
            if (config.userPermissions.containsKey(reader.uuid().toString()))
                fail("A refused action stored the node.");

            // Exactly the node that gates this page: the admin's own grade allows customperm.*, which a
            // denied * would not even beat, being less specific.
            admin.clearReceived();
            userAct(admin, GuiAction.USER_NODE_ADD, "cp_i_users_ow", "customperm.manage.grades", "deny", "", "");
            expectResult(admin, "FAIL: Refused: you would lose customperm.admin or customperm.manage.grades yourself.");
            if (config.userDeniedPermissions.containsKey(owner))
                fail("The refused change must be undone, not left in place.");
        } finally {
            config.userPermissions.remove(owner);
            config.userDeniedPermissions.remove(owner);
        }
        helper.succeed();
    }

    /** The Parents tab of the Grades page: inherit, refuse a cycle, stop inheriting, refreshed in place. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gradesPageEditsParents(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        var server = helper.getLevel().getServer();
        var config = CustomPerm.configManager.getGrades();
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_parents", 4)) {
            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_CREATE, "cp_i_p_base");
            gradeAct(owner, GuiAction.GRADE_CREATE, "cp_i_p_leaf");
            gradeAct(owner, GuiAction.GRADE_PARENT_ADD, "cp_i_p_leaf", "cp_i_p_base", "", "");
            gradeAct(owner, GuiAction.GRADE_PARENT_ADD, "cp_i_p_base", "cp_i_p_leaf", "", "");
            gradeAct(owner, GuiAction.GRADE_PARENT_ADD, "cp_i_p_leaf", "cp_i_p_missing", "", "");
            List<String> results = results(owner);
            if (!results.equals(List.of("OK: Created grade cp_i_p_base",
                    "OK: Created grade cp_i_p_leaf",
                    "OK: cp_i_p_leaf now inherits cp_i_p_base",
                    "FAIL: Refused: cp_i_p_leaf inherits cp_i_p_base, so cp_i_p_base cannot inherit cp_i_p_leaf.",
                    "FAIL: No such grade: cp_i_p_missing")))
                fail("Unexpected results: " + results);
            if (!config.grades.get("cp_i_p_base").parents.isEmpty())
                fail("The refused cycle must leave the parent list alone.");

            var pages = owner.payloads(GuiPagePayload.class);
            GradesData.Grade row = pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof GradesData data)
                    ? null
                    : data.grades().stream().filter(g -> g.name().equals("cp_i_p_leaf")).findFirst().orElse(null);
            if (row == null || !row.parents().equals(List.of("cp_i_p_base")))
                fail("The refreshed page must carry the parents: " + row);

            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_PARENT_REMOVE, "cp_i_p_leaf", "cp_i_p_base", "");
            gradeAct(owner, GuiAction.GRADE_PARENT_REMOVE, "cp_i_p_leaf", "cp_i_p_base", "");
            results = results(owner);
            if (!results.equals(List.of("OK: cp_i_p_leaf no longer inherits cp_i_p_base",
                    "OK: cp_i_p_leaf does not inherit cp_i_p_base — no change.")))
                fail("Unexpected removal results: " + results);
        } finally {
            GradeAdmin.delete(server, "cp_i_p_base");
            GradeAdmin.delete(server, "cp_i_p_leaf");
        }
        helper.succeed();
    }

    /** The refusal actions of the Grades page: a grade refusing a grade, and a player refusing one. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void gradesPageEditsRefusals(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        var server = helper.getLevel().getServer();
        var config = CustomPerm.configManager.getGrades();
        String target = null;
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_i_refuse", 4);
             TestPlayer member = TestPlayer.join(helper.getLevel(), "cp_i_refused", 0)) {
            target = member.uuid().toString();
            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_CREATE, "cp_i_f_base");
            gradeAct(owner, GuiAction.GRADE_CREATE, "cp_i_f_mid");
            gradeAct(owner, GuiAction.GRADE_CREATE, "cp_i_f_leaf");
            gradeAct(owner, GuiAction.GRADE_PARENT_ADD, "cp_i_f_mid", "cp_i_f_base", "", "");
            gradeAct(owner, GuiAction.GRADE_PARENT_ADD, "cp_i_f_leaf", "cp_i_f_mid", "", "");
            owner.clearReceived();

            gradeAct(owner, GuiAction.GRADE_PARENT_DENY, "cp_i_f_leaf", "cp_i_f_base", "", "");
            gradeAct(owner, GuiAction.GRADE_PARENT_DENY, "cp_i_f_leaf", "cp_i_f_mid", "", "");
            gradeAct(owner, GuiAction.GRADE_REFUSE, "CP_I_REFUSED", "cp_i_f_base", "", "");
            gradeAct(owner, GuiAction.GRADE_REFUSE, "cp_i_nobody_here", "cp_i_f_base", "", "");
            List<String> results = results(owner);
            if (!results.equals(List.of("OK: cp_i_f_leaf now refuses cp_i_f_base",
                    "FAIL: cp_i_f_leaf inherits cp_i_f_mid directly: remove that parent instead of refusing it.",
                    "OK: cp_i_refused now refuses cp_i_f_base",
                    "FAIL: Unknown player 'cp_i_nobody_here': grades can be assigned to players online or who joined this server before.")))
                fail("Unexpected results: " + results);
            if (!config.grades.get("cp_i_f_leaf").deniedParents.equals(List.of("cp_i_f_base"))
                    || !config.userDeniedGrades.getOrDefault(target, List.of()).equals(List.of("cp_i_f_base")))
                fail("The refusals were not stored where they belong.");

            var pages = owner.payloads(GuiPagePayload.class);
            GradesData.Grade row = pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof GradesData data)
                    ? null
                    : data.grades().stream().filter(g -> g.name().equals("cp_i_f_base")).findFirst().orElse(null);
            if (row == null || row.refusers().stream().noneMatch(m -> m.name().equals("cp_i_refused")))
                fail("The refreshed page must list who refuses the grade: " + row);

            owner.clearReceived();
            gradeAct(owner, GuiAction.GRADE_PARENT_ALLOW, "cp_i_f_leaf", "cp_i_f_base", "");
            gradeAct(owner, GuiAction.GRADE_ACCEPT, target, "cp_i_f_base", "");
            gradeAct(owner, GuiAction.GRADE_ACCEPT, "not-a-uuid", "cp_i_f_base", "");
            results = results(owner);
            if (!results.equals(List.of("OK: cp_i_f_leaf no longer refuses cp_i_f_base",
                    "OK: cp_i_refused no longer refuses cp_i_f_base",
                    "FAIL: Malformed request for GRADE_ACCEPT.")))
                fail("Unexpected results: " + results);
            if (config.userDeniedGrades.containsKey(target))
                fail("An entry left without a refusal must be dropped, not kept empty.");
        } finally {
            for (String name : List.of("cp_i_f_base", "cp_i_f_mid", "cp_i_f_leaf")) GradeAdmin.delete(server, name);
            if (target != null) config.userDeniedGrades.remove(target);
        }
        helper.succeed();
    }

    private static void gradeAct(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.GRADES.id()),
                player.payloadContext());
    }

    private static void userAct(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.PLAYERS.id()),
                player.payloadContext());
    }

    private static void limitAct(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.RATE_LIMITS.id()),
                player.payloadContext());
    }

    private static RateLimitsData rateLimitsPage(TestPlayer player) {
        player.clearReceived();
        GuiRequestHandler.open(player.player(), GuiPage.RATE_LIMITS);
        var pages = player.payloads(GuiPagePayload.class);
        if (pages.size() != 1 || !(pages.get(0).data() instanceof RateLimitsData data)) {
            fail("Expected the rate limits page, got " + pages);
            return null;
        }
        return data;
    }

    private static void aliasAct(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.ALIASES.id()),
                player.payloadContext());
    }

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
