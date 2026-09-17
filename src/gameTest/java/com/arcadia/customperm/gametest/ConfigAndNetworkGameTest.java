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
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.lp.LpDto;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.LpEditPayload;
import com.arcadia.customperm.network.lp.LpEditResultPayload;
import com.arcadia.customperm.network.lp.LpRequestHandler;
import com.arcadia.customperm.network.lp.LpSyncPayload;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Config transactions and the network boundary, with connected players (procedure 1.0.5 A8.1, A8.3,
 * B6.1; audit retest C03, C04; LuckPerms editor procedure N04, N06).
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ConfigAndNetworkGameTest {

    private static final String TEMPLATE = "empty_3x3";
    private static final Path CONFIG = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("customperm");

    /** A8.1: a valid edit in one file is not applied when another file of the same reload is invalid. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reloadIsAllOrNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (FileBackup ignored = FileBackup.of(server, "grades.json", "aliases.json")) {
            Gson gson = new Gson();
            JsonObject grades = gson.fromJson(Files.readString(CONFIG.resolve("grades.json")), JsonObject.class);
            JsonObject grade = new JsonObject();
            grade.add("permissions", gson.toJsonTree(List.of("cp.txn")));
            grades.getAsJsonObject("grades").add("cp_c_txn", grade);
            Files.writeString(CONFIG.resolve("grades.json"), gson.toJson(grades));
            Files.writeString(CONFIG.resolve("aliases.json"), "{ broken");

            expect(ServerCommands.run(server, "customperm reload"), "Reload failed");
            if (CustomPerm.configManager.getGrades().grades.containsKey("cp_c_txn"))
                fail("A valid grades.json edit was applied although aliases.json made the reload fail.");
        } catch (IOException e) {
            fail("IO error: " + e.getMessage());
        }
        helper.succeed();
    }

    /** A8.3: a reload requested while another is in progress is refused, not run concurrently. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void concurrentReloadIsRefused(GameTestHelper helper) {
        AtomicBoolean reloading = reloadingFlag();
        if (!reloading.compareAndSet(false, true)) fail("Setup: a reload is unexpectedly in progress.");
        try {
            expect(ServerCommands.run(helper.getLevel().getServer(), "customperm reload"), "Reload already in progress");
        } finally {
            reloading.set(false);
        }
        helper.succeed();
    }

    /** C03: after a failed reload, an admin change is kept in memory and the admin is told it was not saved. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void changeAfterFailedReloadIsNotSaved(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (FileBackup ignored = FileBackup.of(server, "ratelimits.json", "aliases.json");
             TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_c_saves", 2)) {
            Files.writeString(CONFIG.resolve("aliases.json"), "{ broken");
            ServerCommands.run(server, "customperm reload");
            String before = Files.readString(CONFIG.resolve("ratelimits.json"));
            op.clearReceived();
            op.type("customperm ratelimit set cp_c_unsaved 1 60");
            if (!op.chatContains("NOT saved")) fail("The admin was not told the change was not saved: " + op.chat());
            if (!Files.readString(CONFIG.resolve("ratelimits.json")).equals(before))
                fail("ratelimits.json was written while saves are suspended.");
            if (!Files.readString(CONFIG.resolve("aliases.json")).equals("{ broken"))
                fail("The invalid aliases.json was overwritten.");
        } catch (IOException e) {
            fail("IO error: " + e.getMessage());
        } finally {
            CustomPerm.configManager.getRateLimits().rules.remove("cp_c_unsaved");
        }
        helper.succeed();
    }

    /** C04: null entries written by hand load cleanly, and the affected player still joins normally. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void nullEntriesDoNotBreakJoiningPlayers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "cp_c_nulls");
        try (FileBackup ignored = FileBackup.of(server, "grades.json", "aliases.json")) {
            Files.writeString(CONFIG.resolve("grades.json"),
                    "{\"grades\":{\"ghost\":null},\"userGrades\":{\"" + profile.getId() + "\":null}}");
            Files.writeString(CONFIG.resolve("aliases.json"), "{\"aliases\":{\"cp_c_broken\":null,\"cp_c_half\":[null,\"say hi\"]}}");
            expect(ServerCommands.run(server, "customperm reload"), "Configuration reloaded successfully.");
            try (TestPlayer player = TestPlayer.join(helper.getLevel(), profile, 0, true)) {
                if (player.commandTreesReceived() < 1) fail("The player received no command tree.");
                if (player.canUse("gamemode")) fail("A null grade assignment granted something.");
            }
            expect(ServerCommands.run(server, "customperm alias list"), "/cp_c_half  (1 step)");
        } catch (IOException e) {
            fail("IO error: " + e.getMessage());
        }
        helper.succeed();
    }

    /** B6.1 / N04: a non-operator gets nothing back from either editor packet, in both backends. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void editorPacketsFromNonOperatorsGetNothing(GameTestHelper helper) {
        // Not try-with-resources: closing the player releases its unread packets, and the check below
        // runs later, so an early close would make this test pass whatever the server sent.
        TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_c_intruder", 0);
        player.clearReceived();
        LpRequestHandler.handleSync(RequestLpSyncPayload.of(RequestLpSyncPayload.SCOPE_GROUPS), player.payloadContext());
        LpRequestHandler.handleEdit(new LpEditPayload(LpEditOp.GROUP_CREATE.name(), List.of("cp_c_hacked"), "", ""),
                player.payloadContext());
        helper.runAfterDelay(5, () -> {
            try {
                if (!player.payloads(LpSyncPayload.class).isEmpty()) fail("A non-operator received editor data.");
                if (!player.payloads(LpEditResultPayload.class).isEmpty()) fail("A non-operator's edit was answered.");
                helper.succeed();
            } finally {
                player.close();
            }
        });
    }

    /** N06: without LuckPerms the editor answers an operator with an empty snapshot and refuses edits. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void editorWithoutLuckPermsIsEmptyAndReadOnly(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_c_nolp", 4)) {
            op.clearReceived();
            LpRequestHandler.handleSync(RequestLpSyncPayload.of(RequestLpSyncPayload.SCOPE_GROUPS), op.payloadContext());
            var snapshots = op.payloads(LpSyncPayload.class);
            if (snapshots.size() != 1 || snapshots.get(0).snapshot() != LpDto.Snapshot.EMPTY)
                fail("Expected exactly one empty snapshot, got " + snapshots);
            LpRequestHandler.handleEdit(new LpEditPayload(LpEditOp.GROUP_CREATE.name(), List.of("cp_c_nolp"), "", ""),
                    op.payloadContext());
            var results = op.payloads(LpEditResultPayload.class);
            if (results.size() != 1 || results.get(0).success()
                    || !results.get(0).message().equals("LuckPerms is not active on this server."))
                fail("Expected the 'LuckPerms is not active' refusal, got " + results);
        }
        helper.succeed();
    }

    private static AtomicBoolean reloadingFlag() {
        try {
            Field field = ConfigManager.class.getDeclaredField("reloading");
            field.setAccessible(true);
            return (AtomicBoolean) field.get(CustomPerm.configManager);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("Cannot reach ConfigManager.reloading: " + e);
        }
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) fail("Expected output containing '" + fragment + "', got: " + lines);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }

    /** Restores config files byte for byte and reloads them, whatever the test did. */
    private static final class FileBackup implements AutoCloseable {
        private final MinecraftServer server;
        private final List<String> names;
        private final List<String> contents;

        private FileBackup(MinecraftServer server, List<String> names, List<String> contents) {
            this.server = server;
            this.names = names;
            this.contents = contents;
        }

        static FileBackup of(MinecraftServer server, String... names) throws IOException {
            CustomPerm.configManager.save();
            List<String> contents = new java.util.ArrayList<>();
            for (String name : names) contents.add(Files.readString(CONFIG.resolve(name)));
            return new FileBackup(server, List.of(names), contents);
        }

        @Override
        public void close() throws IOException {
            for (int i = 0; i < names.size(); i++) Files.writeString(CONFIG.resolve(names.get(i)), contents.get(i));
            ServerCommands.run(server, "customperm reload");
            if (!CustomPerm.configManager.isDiskWritable())
                throw new GameTestAssertException("Config did not recover after restoring " + names);
        }
    }
}
