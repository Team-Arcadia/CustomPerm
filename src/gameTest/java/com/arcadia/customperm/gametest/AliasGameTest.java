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
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Aliases run by a connected player, in both backends (procedure 1.0.5 A5 and B5.4, audit retest R03).
 * Steps use {@code tag @s add ...}: an op-only command whose effect lands on the test player alone, so a
 * non-operator seeing it applied proves both the execution and the op-4 elevation.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class AliasGameTest {

    private static final String TEMPLATE = "empty_3x3";

    /** A5.1 + A5.2 + R03: created live, run by a non-op holding the node, refused without it, removed live. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasRunsElevatedForHoldersOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer holder = TestPlayer.join(helper.getLevel(), "cp_a_holder", 0);
             TestPlayer other = TestPlayer.join(helper.getLevel(), "cp_a_other", 0);
             Grants ignored = Grants.allow(holder, "customperm.alias.cp_a_run")) {
            expect(ServerCommands.run(server, "customperm alias add cp_a_run tag @s add cp_a_one; tag @s add cp_a_two"),
                    "Alias /cp_a_run set with 2 step(s).");
            if (!holder.canUse("cp_a_run")) fail("A player holding customperm.alias.cp_a_run cannot see the alias.");
            holder.exec("cp_a_run");
            if (!holder.player().getTags().containsAll(List.of("cp_a_one", "cp_a_two")))
                fail("Both op-only steps must run for a non-operator holder (op-4 elevation), tags: " + holder.player().getTags());

            if (other.canUse("cp_a_run")) fail("A player without the alias node can see the alias.");
            if (other.player().getTags().contains("cp_a_one")) fail("The alias affected a player who did not run it.");

            expect(ServerCommands.run(server, "customperm alias remove cp_a_run"), "Removed alias /cp_a_run");
            if (holder.canUse("cp_a_run")) fail("A removed alias is still usable.");
        } catch (CommandSyntaxException e) {
            fail("Holder could not run the alias: " + e.getMessage());
        } finally {
            CustomPerm.configManager.getAliases().aliases.remove("cp_a_run");
        }
        helper.succeed();
    }

    /**
     * Security (INVARIANT-503): alias steps run at op level 4, but /customperm checks the real player's
     * level. A non-operator allowed to run an alias that calls /customperm must not change anything.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasCannotEscalateToCustompermAdmin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var exposed = CustomPerm.configManager.getCommands().grantedCommands;
        boolean wasExposed = exposed.contains("seed");
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_a_escalate", 0);
             Grants ignored = Grants.allow(player, "customperm.alias.cp_a_escalate")) {
            ServerCommands.run(server, "customperm alias add cp_a_escalate customperm command add seed");
            player.clearReceived();
            player.type("cp_a_escalate");
            if (exposed.contains("seed") && !wasExposed)
                fail("A non-operator used an alias to run /customperm command add.");
        } finally {
            ServerCommands.run(server, "customperm alias remove cp_a_escalate");
            if (!wasExposed && exposed.contains("seed")) ServerCommands.run(server, "customperm command remove seed");
        }
        helper.succeed();
    }

    /** A5.3: zero-based step editing, and an out-of-range index refused. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void stepEditingKeepsIndicesConsistent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            ServerCommands.run(server, "customperm alias add cp_a_steps say zero");
            expect(ServerCommands.run(server, "customperm alias addstep cp_a_steps say one"), "Appended step #1 to /cp_a_steps: say one");
            List<String> steps = ServerCommands.run(server, "customperm alias steps cp_a_steps");
            expect(steps, "#0: /say zero");
            expect(steps, "#1: /say one");
            expect(ServerCommands.run(server, "customperm alias removestep cp_a_steps 0"), "Removed step #0 from /cp_a_steps: say zero");
            expect(ServerCommands.run(server, "customperm alias steps cp_a_steps"), "#0: /say one");
            expect(ServerCommands.run(server, "customperm alias removestep cp_a_steps 5"), "Index out of range (0..0)");
        } finally {
            ServerCommands.run(server, "customperm alias remove cp_a_steps");
        }
        helper.succeed();
    }

    /** A5.4: reserved name, empty alias, recursion guard, and B5.4 shadowing warning. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void aliasGuardsHold(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        expect(ServerCommands.run(server, "customperm alias add customperm say nope"), "Reserved name.");
        expect(ServerCommands.run(server, "customperm alias add cp_a_empty ; ;"), "No commands provided.");
        if (CustomPerm.configManager.getAliases().aliases.containsKey("cp_a_empty")) fail("An empty alias was stored.");

        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_a_loop", 2)) {
            ServerCommands.run(server, "customperm alias add cp_a_ping cp_a_pong");
            ServerCommands.run(server, "customperm alias add cp_a_pong cp_a_ping");
            op.clearReceived();
            op.type("cp_a_ping");
            if (!op.chatContains("nested alias depth exceeds"))
                fail("A recursive alias chain must stop with the depth message, got: " + op.chat());
        } finally {
            ServerCommands.run(server, "customperm alias remove cp_a_ping");
            ServerCommands.run(server, "customperm alias remove cp_a_pong");
        }

        try {
            expect(ServerCommands.run(server, "customperm alias add seed say shadowed"), "WARNING: /seed shadows an existing command.");
        } finally {
            ServerCommands.run(server, "customperm alias remove seed");
        }
        if (Customperm.findRoot(server, "seed") == null) fail("Removing the shadowing alias must restore vanilla /seed.");
        helper.succeed();
    }

    /** A5.5: /customperm reload applies additions, removals and step edits made directly in aliases.json. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reloadAppliesAliasFileEdits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("customperm").resolve("aliases.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (TestPlayer op = TestPlayer.join(helper.getLevel(), "cp_a_reload", 2)) {
            ServerCommands.run(server, "customperm alias add cp_a_edit tag @s add cp_a_before");
            ServerCommands.run(server, "customperm alias add cp_a_drop say gone");

            JsonObject root = gson.fromJson(Files.readString(file), JsonObject.class);
            JsonObject aliases = root.getAsJsonObject("aliases");
            aliases.remove("cp_a_drop");
            JsonArray edited = new JsonArray();
            edited.add("tag @s add cp_a_after");
            aliases.add("cp_a_edit", edited);
            JsonArray added = new JsonArray();
            added.add("say new");
            aliases.add("cp_a_new", added);
            Files.writeString(file, gson.toJson(root));

            expect(ServerCommands.run(server, "customperm reload"), "Configuration reloaded successfully.");
            if (Customperm.findRoot(server, "cp_a_drop") != null) fail("An alias removed from the file survived the reload.");
            if (Customperm.findRoot(server, "cp_a_new") == null) fail("An alias added to the file was not registered.");
            op.exec("cp_a_edit");
            if (op.player().getTags().contains("cp_a_before") || !op.player().getTags().contains("cp_a_after"))
                fail("Edited steps were not applied on reload, tags: " + op.player().getTags());
        } catch (IOException | CommandSyntaxException e) {
            fail("Alias reload test failed: " + e.getMessage());
        } finally {
            for (String name : List.of("cp_a_edit", "cp_a_drop", "cp_a_new")) {
                if (CustomPerm.configManager.getAliases().aliases.containsKey(name)) {
                    ServerCommands.run(server, "customperm alias remove " + name);
                }
            }
        }
        helper.succeed();
    }

    private static void expect(List<String> lines, String fragment) {
        if (!ServerCommands.contains(lines, fragment)) fail("Expected output containing '" + fragment + "', got: " + lines);
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
