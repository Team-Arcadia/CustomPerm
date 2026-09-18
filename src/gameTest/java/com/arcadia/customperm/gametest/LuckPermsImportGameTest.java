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
import com.arcadia.customperm.admin.ScopedGrant;
import com.arcadia.customperm.config.CommandsConfig;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.gametest.support.LuckPermsTestSupport;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.ImportData;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.perm.lp.LuckPermsImport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
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
    private static final String PAGE_GROUP = "cp_m_page";
    private static final String NETHER = "world=minecraft:the_nether";
    private static final String TRACK = "cp_m_ladder";
    private static final String END = "world=minecraft:the_end";

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
            apply(LpEditOp.GROUP_PERM_ADD, BASE, "cptest.probe.shut", "true", "", "0");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.kick", "true", "", "3600");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.seed", "true", "world=the_nether", "0");
            apply(LpEditOp.GROUP_PERM_ADD, VIP, "customperm.command.difficulty", "true", "server=lobby", "0");
            apply(LpEditOp.GROUP_PREFIX_SET, VIP, "10", "[VIP]", "");
            apply(LpEditOp.GROUP_PREFIX_SET, VIP, "5", "[Lesser]", "");
            apply(LpEditOp.GROUP_META_SET, VIP, "rank", "gold", "");
            apply(LpEditOp.GROUP_PARENT_ADD, VIP, BASE, "");
            apply(LpEditOp.GROUP_WEIGHT_SET, VIP, "42");
            apply(LpEditOp.USER_PARENT_ADD, USER.toString(), VIP, "", "0");
            apply(LpEditOp.USER_PARENT_ADD, USER.toString(), BASE, "", "7200");
            LuckPermsTestSupport.addTemporaryParent(BASE, "default", 3600);
            apply(LpEditOp.USER_PERM_ADD, USER.toString(), "minecraft.command.weather", "true", "", "0");
            apply(LpEditOp.USER_PERM_ADD, USER.toString(), "customperm.command.seed", "false", "world=the_end", "0");
            apply(LpEditOp.USER_PERM_ADD, USER.toString(), "cptest.probe.open", "false", "", "0");
            apply(LpEditOp.TRACK_CREATE, TRACK);
            apply(LpEditOp.TRACK_APPEND, TRACK, BASE);
            apply(LpEditOp.TRACK_APPEND, TRACK, VIP);

            ImportPlan plan = LuckPermsTestSupport.await(LuckPermsImport.read(true));

            ImportPlan.Grade base = grade(plan, BASE);
            if (!base.allow().contains("customperm.command.gamemode"))
                fail("minecraft.command.gamemode must arrive as customperm.command.gamemode: " + base);
            if (!base.allow().contains("customperm.command.time"))
                fail("A customperm node must carry over as it is: " + base);
            if (!base.deny().contains("customperm.command.ban"))
                fail("A node set to false must arrive as a denial: " + base);
            if (base.allow().contains("essentials.fly") || base.allow().contains("customperm.command.fly"))
                fail("A node another mod reads without declaring it must not be imported: " + base);
            if (!base.allow().contains("cptest.probe.shut"))
                fail("A node another mod declared to NeoForge must be imported as it is: " + base);

            ImportPlan.Grade vip = grade(plan, VIP);
            if (vip.weight() != 42) fail("The group weight must arrive as the grade weight: " + vip);
            if (!vip.parents().equals(List.of(BASE))) fail("The group parent must arrive as a grade parent: " + vip);
            if (!vip.allow().equals(java.util.Set.of("customperm.command.kick")))
                fail("A temporary node must arrive, and a contextual one not as a global node: " + vip);
            if (!vip.scoped().equals(List.of(new ScopedGrant(NETHER, ScopedGrant.ALLOW, "customperm.command.seed"))))
                fail("A node limited to one world must arrive with it, one limited to a server be left behind: "
                        + vip.scoped());
            long kickAt = vip.expiries().getOrDefault("allow:customperm.command.kick", 0L);
            if (Math.abs(kickAt - (com.arcadia.customperm.perm.Expiry.now() + 3600)) > 30)
                fail("The temporary node must keep its expiry: " + vip.expiries());
            if (!base.parents().isEmpty())
                fail("A temporary parent of a group must be left behind, a grade inheriting for good: " + base);
            if (!"[VIP]".equals(vip.prefix()))
                fail("The prefix LuckPerms shows first, the highest priority, must arrive: " + vip.prefix());

            if (!plan.exposeCommands().contains("gamemode") || !plan.exposeCommands().contains("weather"))
                fail("A translated node must expose its command, or it grants nothing: " + plan.exposeCommands());
            if (plan.exposeCommands().contains("difficulty"))
                fail("A node that was not imported must not expose anything: " + plan.exposeCommands());
            if (!plan.exposeCommands().contains("seed"))
                fail("A node allowed in one world must expose its command, or it grants nothing there: "
                        + plan.exposeCommands());

            ImportPlan.Player player = plan.players().stream()
                    .filter(p -> p.uuid().equals(USER.toString())).findFirst().orElse(null);
            if (player == null || !player.grades().contains(VIP)
                    || !player.allow().contains("customperm.command.weather"))
                fail("The user's group and own node must both arrive: " + player);
            if (!player.grades().contains(BASE) || !player.expiries().containsKey("grade:" + BASE)
                    || player.expiries().containsKey("grade:" + VIP))
                fail("A temporary group of a player must arrive with its expiry, a permanent one without: " + player);
            if (!player.deny().contains("cptest.probe.open"))
                fail("A player's node another mod declared must be searched for and imported: " + player);
            if (!player.scoped().equals(List.of(new ScopedGrant(END, ScopedGrant.DENY, "customperm.command.seed"))))
                fail("A player's node limited to a world must arrive with it: " + player.scoped());
            if (plan.counts().worlds() != 2) fail("Both entries limited to a world must be counted: " + plan.counts());
            if (!List.of(BASE, VIP).equals(plan.tracks().get(TRACK)))
                fail("A track must arrive with its groups in order: " + plan.tracks());

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
            if (!"[VIP]".equals(grades.grades.get(VIP).prefix)) fail("The prefix was not written.");
            if (!grades.grades.get(BASE).deniedPermissions.contains("customperm.command.ban"))
                fail("The denial was not written.");
            if (!grades.userGrades.getOrDefault(USER.toString(), List.of()).contains(VIP))
                fail("The player was not assigned.");
            if (!grades.grades.get(VIP).permissionExpiries.containsKey("customperm.command.kick"))
                fail("The temporary node was written without its expiry.");
            if (!grades.userGradeExpiries.getOrDefault(USER.toString(), java.util.Map.of()).containsKey(BASE))
                fail("The temporary grade was written without its expiry.");
            if (!List.of(BASE, VIP).equals(grades.tracks.get(TRACK))) fail("The track was not written: " + grades.tracks);
            if (!grades.grades.get(VIP).contexts.get(NETHER).permissions.contains("customperm.command.seed"))
                fail("The node limited to the Nether was not written there.");
            if (!grades.userContexts.get(USER.toString()).get(END).deniedPermissions.contains("customperm.command.seed"))
                fail("The player's node limited to the End was not written there.");
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
            grades.userGradeExpiries.keySet().retainAll(holdersBefore);
            grades.userContexts.remove(USER.toString());
            grades.tracks.remove(TRACK);
            LuckPermsTestSupport.cleanup(List.of(BASE, VIP), List.of(TRACK));
        }
        helper.succeed();
    }

    /** The import page: reading answers with the report and writes nothing, and only a preview can be applied. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void importPageReadsThenImports(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        GradesConfig grades = CustomPerm.configManager.getGrades();
        CommandsConfig commands = CustomPerm.configManager.getCommands();
        Set<String> gradesBefore = new HashSet<>(grades.grades.keySet());
        Set<String> commandsBefore = new HashSet<>(commands.grantedCommands);
        try (TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_m_owner", 4);
             TestPlayer reader = TestPlayer.reader(helper.getLevel(), "cp_m_reader", 2)) {
            apply(LpEditOp.GROUP_CREATE, PAGE_GROUP);
            apply(LpEditOp.GROUP_PERM_ADD, PAGE_GROUP, "minecraft.command.weather", "true", "", "0");

            // The area check answers first, then the composite one: importing asks for three nodes.
            reader.clearReceived();
            importAct(reader, GuiAction.IMPORT_PREVIEW, "true");
            expectResult(reader, "FAIL: You do not have customperm.manage.grades.");
            try (Grants oneArea = Grants.allow(reader, "customperm.manage.grades")) {
                reader.clearReceived();
                importAct(reader, GuiAction.IMPORT_PREVIEW, "true");
                expectResult(reader, "FAIL: Importing needs customperm.manage.grades, "
                        + "customperm.manage.commands and customperm.manage.luckperms.");
            }

            owner.clearReceived();
            importAct(owner, GuiAction.IMPORT_APPLY, "merge");
            expectResult(owner, "FAIL: Read LuckPerms first");
            if (grades.grades.containsKey(PAGE_GROUP)) fail("Applying without a preview wrote something.");

            // Reading answers at once and refreshes the page when LuckPerms comes back.
            owner.clearReceived();
            importAct(owner, GuiAction.IMPORT_PREVIEW, "true");
            expectResult(owner, "OK: Reading LuckPerms");
            ImportData page = awaitReport(helper.getLevel().getServer(), owner);
            if (!page.previewed() || page.report().isEmpty())
                fail("The refreshed page must carry the report: " + page);
            if (grades.grades.containsKey(PAGE_GROUP)) fail("Reading must not write anything.");

            owner.clearReceived();
            importAct(owner, GuiAction.IMPORT_APPLY, "sideways");
            expectResult(owner, "FAIL: Malformed request for IMPORT_APPLY.");

            owner.clearReceived();
            importAct(owner, GuiAction.IMPORT_APPLY, "merge");
            List<String> results = results(owner);
            if (results.size() != 1 || !results.get(0).startsWith("OK: Imported"))
                fail("Unexpected import result: " + results);
            if (!grades.grades.containsKey(PAGE_GROUP) || !commands.grantedCommands.contains("weather"))
                fail("The import did not write what it said it would.");

            // The preview is spent: confirming again asks for a new read rather than importing twice.
            owner.clearReceived();
            importAct(owner, GuiAction.IMPORT_APPLY, "merge");
            expectResult(owner, "FAIL: Read LuckPerms first");
        } finally {
            grades.grades.keySet().retainAll(gradesBefore);
            commands.grantedCommands.retainAll(commandsBefore);
            LuckPermsTestSupport.cleanup(List.of(PAGE_GROUP), List.of());
        }
        helper.succeed();
    }

    /**
     * The page the server pushes once LuckPerms has answered. The answer arrives on a LuckPerms thread and
     * is then scheduled on the server thread, which is the one running this test: sleeping on it would wait
     * for a task only it can run. {@code managedBlock} keeps draining that queue while waiting, and the
     * deadline is inside the condition so a silent LuckPerms ends the test instead of hanging the run.
     */
    private static ImportData awaitReport(MinecraftServer server, TestPlayer player) {
        long deadline = System.currentTimeMillis() + 5000;
        server.managedBlock(() -> report(player) != null || System.currentTimeMillis() > deadline);
        ImportData data = report(player);
        if (data == null) throw new GameTestAssertException("LuckPerms did not answer the preview within 5s");
        return data;
    }

    private static ImportData report(TestPlayer player) {
        List<GuiPagePayload> pages = player.payloads(GuiPagePayload.class);
        for (int i = pages.size() - 1; i >= 0; i--) {
            if (pages.get(i).data() instanceof ImportData data && data.previewed()) return data;
        }
        return null;
    }

    private static void importAct(TestPlayer player, GuiAction action, String... args) {
        GuiRequestHandler.handleAction(new GuiActionPayload(action.name(), List.of(args), GuiPage.IMPORT.id()),
                player.payloadContext());
    }

    private static void expectResult(TestPlayer player, String prefix) {
        List<String> results = results(player);
        if (results.size() != 1 || !results.get(0).startsWith(prefix))
            throw new GameTestAssertException("Expected one result starting with '" + prefix + "', got " + results);
    }

    private static List<String> results(TestPlayer player) {
        return player.payloads(GuiActionResultPayload.class).stream()
                .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message())
                .toList();
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
