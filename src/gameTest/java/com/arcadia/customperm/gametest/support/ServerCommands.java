/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Runs commands as the server console (permission level 4) and captures what they print. */
public final class ServerCommands {

    private ServerCommands() {
    }

    /** Runs {@code command}; a syntax or permission error fails the test. Returns every printed line. */
    public static List<String> run(MinecraftServer server, String command) {
        List<String> lines = new ArrayList<>();
        try {
            server.getCommands().getDispatcher().execute(command, capturing(server, lines));
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("Syntax error running /" + command + ": " + e.getMessage());
        }
        return lines;
    }

    /** Runs {@code command} and returns the syntax error it raised, or null if it parsed and ran. */
    public static CommandSyntaxException syntaxError(MinecraftServer server, String command) {
        try {
            server.getCommands().getDispatcher().execute(command, capturing(server, new ArrayList<>()));
            return null;
        } catch (CommandSyntaxException e) {
            return e;
        }
    }

    public static boolean contains(List<String> lines, String fragment) {
        return lines.stream().anyMatch(line -> line.contains(fragment));
    }

    private static CommandSourceStack capturing(MinecraftServer server, List<String> lines) {
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return server.createCommandSourceStack().withSource(capture);
    }
}
