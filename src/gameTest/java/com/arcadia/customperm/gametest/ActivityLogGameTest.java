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
import com.arcadia.customperm.admin.LogAdmin;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.log.LogFiles;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.LogsData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Activity log (backlog item 10): admin changes from commands, the interface and LuckPerms, player
 * commands when enabled, masking, files on disk, retention and the Logs page. Tests that switch the
 * player log or reload the log from disk run in their own batch.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ActivityLogGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void adminChangesAreRecordedFromCommandsAndTheInterface(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer owner = TestPlayer.join(helper.getLevel(), "cp_lg_owner", 4);
             TestPlayer moderator = TestPlayer.join(helper.getLevel(), "cp_lg_mod", 2)) {
            ServerCommands.run(server, "customperm alias add cp_lg_alias say hi");
            LogEntry typed = find(LogKind.ADMIN, e -> e.action().equals("/customperm alias add cp_lg_alias say hi"));
            if (!typed.success() || !LogEntry.SOURCE_COMMAND.equals(typed.source()) || typed.result().isEmpty())
                fail("The console command was recorded wrongly: " + typed);

            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.ALIAS_DELETE.name(), List.of("cp_lg_alias"),
                    GuiPage.ALIASES.id()), moderator.payloadContext());
            LogEntry refused = find(LogKind.ADMIN, e -> e.actor().equals("cp_lg_mod") && e.action().equals("ALIAS_DELETE cp_lg_alias"));
            if (refused.success() || !refused.result().contains("customperm.gui.aliases.edit"))
                fail("A refused interface action must be recorded as refused: " + refused);

            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.ALIAS_DELETE.name(), List.of("cp_lg_alias"),
                    GuiPage.ALIASES.id()), owner.payloadContext());
            LogEntry applied = find(LogKind.ADMIN, e -> e.actor().equals("cp_lg_owner") && e.action().equals("ALIAS_DELETE cp_lg_alias"));
            if (!applied.success() || !LogEntry.SOURCE_INTERFACE.equals(applied.source())
                    || !applied.actorId().equals(owner.uuid().toString()))
                fail("The interface action was recorded wrongly: " + applied);

            ActivityLog.flush();
            if (!fileContains(LogKind.ADMIN, "cp_lg_alias")) fail("The admin change was not written to today's file.");
        } finally {
            AliasAdmin.remove(server, "cp_lg_alias");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_log_players")
    public static void playerCommandsAreRecordedOnlyWhenOnAndMaskedByDefault(GameTestHelper helper) {
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_lg_player", 0)) {
            player.type("help cp_lg_off");
            if (any(LogKind.PLAYERS, e -> e.action().contains("cp_lg_off"))) fail("Player commands were recorded while the log is off.");

            expect(LogAdmin.setPlayerLog(true).success(), "Turning the player log on failed.");
            player.type("help cp_lg_on");
            LogEntry typed = find(LogKind.PLAYERS, e -> e.action().equals("/help cp_lg_on"));
            if (!typed.actor().equals("cp_lg_player") || !typed.actorId().equals(player.uuid().toString()))
                fail("The player command was recorded with the wrong player: " + typed);

            player.type("msg cp_lg_player cp_lg_secret");
            find(LogKind.PLAYERS, e -> e.action().equals("/msg " + LogFiles.MASK));
            if (any(LogKind.PLAYERS, e -> e.action().contains("cp_lg_secret"))) fail("A private message was recorded unmasked.");

            expect(LogAdmin.setMasking(false).success(), "Turning masking off failed.");
            player.type("msg cp_lg_player cp_lg_clear");
            find(LogKind.PLAYERS, e -> e.action().equals("/msg cp_lg_player cp_lg_clear"));

            ActivityLog.flush();
            if (fileContains(LogKind.PLAYERS, "cp_lg_secret")) fail("The masked argument reached the file.");
            if (!fileContains(LogKind.PLAYERS, "cp_lg_clear")) fail("The player command was not written to today's file.");
        } finally {
            LogAdmin.setMasking(true);
            LogAdmin.setPlayerLog(false);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_log_disk")
    public static void entriesSurviveAReloadAndRetentionDeletesOldFiles(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        ServerCommands.run(server, "customperm log admin 1");  // not a change: not recorded
        ServerCommands.run(server, "customperm reload");
        Path dir = ActivityLog.directory();
        if (dir == null) fail("The activity log has no directory while the server runs.");

        Path today = dir.resolve(LogFiles.fileName(LogKind.ADMIN, LocalDate.now()));
        ActivityLog.flush();
        Files.writeString(today, "not json\n", java.nio.file.StandardOpenOption.APPEND);
        ActivityLog.reloadFromDisk();
        find(LogKind.ADMIN, e -> e.action().equals("/customperm reload"));

        Path old = dir.resolve(LogFiles.fileName(LogKind.PLAYERS, LocalDate.now().minusDays(400)));
        Files.writeString(old, "{}\n");
        ActivityLog.purge(dir, LocalDate.now());
        if (Files.exists(old)) fail("A file past the 30-day retention was kept.");
        if (!Files.exists(today)) fail("Today's file was deleted by the retention.");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_log_page")
    public static void logsPageShowsEntriesAndItsSwitchesNeedTheLogsNode(GameTestHelper helper) {
        try (TestPlayer owner = TestPlayer.join(helper.getLevel(), "cp_lg_pageown", 4);
             TestPlayer moderator = TestPlayer.join(helper.getLevel(), "cp_lg_pagemod", 2)) {
            ServerCommands.run(helper.getLevel().getServer(), "customperm reload");
            LogsData page = page(owner, () -> GuiRequestHandler.open(owner.player(), GuiPage.LOGS));
            if (page.admin().stream().noneMatch(e -> e.action().equals("/customperm reload")))
                fail("The Logs page does not show the latest admin change.");
            if (page.playerLog()) fail("The player log must be off by default.");

            moderator.clearReceived();
            GuiRequestHandler.handleAction(new GuiActionPayload(GuiAction.LOG_PLAYERS.name(), List.of("true"),
                    GuiPage.LOGS.id()), moderator.payloadContext());
            if (CustomPerm.configManager.getSettings().playerCommandLog)
                fail("A level-2 operator without customperm.gui.logs.edit turned the player log on.");

            LogsData refreshed = page(owner, () -> GuiRequestHandler.handleAction(new GuiActionPayload(
                    GuiAction.LOG_PLAYERS.name(), List.of("true"), GuiPage.LOGS.id()), owner.payloadContext()));
            if (!refreshed.playerLog() || !CustomPerm.configManager.getSettings().playerCommandLog)
                fail("The owner could not turn the player log on from the page.");
        } finally {
            LogAdmin.setPlayerLog(false);
        }
        helper.succeed();
    }

    /** Changes made with /lp reach the admin tab through LuckPerms' own action log. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100000, batch = "customperm_log_luckperms")
    public static void luckPermsCommandsAreRecorded(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        // The real console: LuckPerms does not treat the capturing source of ServerCommands as the console.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "lp group default meta set cp_lg_meta 1");
        // LuckPerms runs the command and publishes its action on its own threads, and the GameTest server does
        // not wait between ticks: poll against the wall clock rather than a tick count.
        awaitLuckPermsEntry(helper, server, System.currentTimeMillis() + 10_000);
    }

    private static void awaitLuckPermsEntry(GameTestHelper helper, MinecraftServer server, long deadline) {
        boolean recorded = any(LogKind.ADMIN, e -> LogEntry.SOURCE_LUCKPERMS.equals(e.source()) && e.action().contains("cp_lg_meta"));
        if (!recorded && System.currentTimeMillis() < deadline) {
            helper.runAfterDelay(1, () -> awaitLuckPermsEntry(helper, server, deadline));
            return;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "lp group default meta unset cp_lg_meta");
        if (!recorded) fail("The /lp change was not recorded, latest admin entries " + ActivityLog.recent(LogKind.ADMIN, 3));
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static LogEntry find(LogKind kind, Predicate<LogEntry> match) {
        return ActivityLog.recent(kind, ActivityLog.MEMORY_MAX).stream().filter(match).findFirst()
                .orElseThrow(() -> new GameTestAssertException("No " + kind + " entry matched, latest: "
                        + ActivityLog.recent(kind, 5)));
    }

    private static boolean any(LogKind kind, Predicate<LogEntry> match) {
        return ActivityLog.recent(kind, ActivityLog.MEMORY_MAX).stream().anyMatch(match);
    }

    private static boolean fileContains(LogKind kind, String text) {
        try {
            Path file = ActivityLog.directory().resolve(LogFiles.fileName(kind, LocalDate.now()));
            return Files.exists(file) && Files.readString(file).contains(text);
        } catch (Exception e) {
            throw new GameTestAssertException("Could not read the log file: " + e);
        }
    }

    private static LogsData page(TestPlayer player, Runnable request) {
        player.clearReceived();
        request.run();
        List<GuiPagePayload> pages = player.payloads(GuiPagePayload.class);
        if (pages.isEmpty() || !(pages.get(pages.size() - 1).data() instanceof LogsData data)) {
            throw new GameTestAssertException("Expected the Logs page, got " + pages + " / "
                    + player.payloads(GuiActionResultPayload.class));
        }
        return data;
    }

    private static void expect(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
