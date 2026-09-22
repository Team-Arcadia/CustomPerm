/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.util.AtomicFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes the persisted rate-limit history, a small JSON file stored in the world save:
 * <pre>{"formatVersion": 1, "history": {"gamemode": {"&lt;uuid&gt;": [1789000000000, ...]}},
 *  "windows": {"gamemode": {"3600000": 1789000000000}}}</pre>
 * Timestamps are Unix epoch milliseconds. {@code windows} holds, per command, each window uses were counted with
 * and when it was last used, so a grade's longer window survives a restart; a file without it reads as before. Pure Java, no Minecraft types, so it is unit-tested directly.
 */
public final class RateLimitStore {

    static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RateLimitStore() {
    }

    /**
     * Returns the history in {@code file}, or an empty map when the file does not exist. Entries that
     * cannot be understood (bad UUID, non-numeric timestamp) are skipped; a file that is not JSON at all
     * throws, so the caller can set it aside instead of silently overwriting it.
     */
    static Map<String, Map<UUID, List<Long>>> read(Path file) throws IOException {
        Map<String, Map<UUID, List<Long>>> history = new LinkedHashMap<>();
        if (!Files.exists(file)) return history;
        JsonObject root;
        try {
            root = GSON.fromJson(Files.readString(file), JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Unreadable rate-limit history " + file.getFileName() + ": " + e.getMessage(), e);
        }
        if (root == null || !root.has("history") || !root.get("history").isJsonObject()) return history;
        for (Map.Entry<String, JsonElement> command : root.getAsJsonObject("history").entrySet()) {
            if (!command.getValue().isJsonObject()) continue;
            Map<UUID, List<Long>> players = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> player : command.getValue().getAsJsonObject().entrySet()) {
                UUID uuid = parseUuid(player.getKey());
                if (uuid == null || !player.getValue().isJsonArray()) continue;
                List<Long> timestamps = new ArrayList<>();
                for (JsonElement element : player.getValue().getAsJsonArray()) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                        timestamps.add(element.getAsLong());
                    }
                }
                if (!timestamps.isEmpty()) players.put(uuid, timestamps);
            }
            if (!players.isEmpty()) history.put(command.getKey(), players);
        }
        return history;
    }

    /** The windows in use saved with the history, empty when the file has none. */
    static Map<String, Map<Long, Long>> readWindows(Path file) throws IOException {
        Map<String, Map<Long, Long>> windows = new LinkedHashMap<>();
        if (!Files.exists(file)) return windows;
        JsonObject root;
        try {
            root = GSON.fromJson(Files.readString(file), JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Unreadable rate-limit history " + file.getFileName() + ": " + e.getMessage(), e);
        }
        if (root == null || !root.has("windows") || !root.get("windows").isJsonObject()) return windows;
        for (Map.Entry<String, JsonElement> command : root.getAsJsonObject("windows").entrySet()) {
            if (!command.getValue().isJsonObject()) continue;
            Map<Long, Long> used = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : command.getValue().getAsJsonObject().entrySet()) {
                try {
                    long window = Long.parseLong(entry.getKey());
                    if (window > 0L && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                        used.put(window, entry.getValue().getAsLong());
                    }
                } catch (NumberFormatException e) {
                    // Not a window: skipped like any other entry this file cannot use.
                }
            }
            if (!used.isEmpty()) windows.put(command.getKey(), used);
        }
        return windows;
    }

    /** Replaces {@code file} atomically with {@code history}. */
    static void write(Path file, Map<String, Map<UUID, List<Long>>> history) throws IOException {
        write(file, history, Map.of());
    }

    /** Replaces {@code file} atomically with {@code history} and the windows in use. */
    static void write(Path file, Map<String, Map<UUID, List<Long>>> history, Map<String, Map<Long, Long>> windows)
            throws IOException {
        JsonObject commands = new JsonObject();
        history.forEach((command, players) -> {
            JsonObject byPlayer = new JsonObject();
            players.forEach((uuid, timestamps) -> {
                JsonArray array = new JsonArray();
                timestamps.forEach(array::add);
                byPlayer.add(uuid.toString(), array);
            });
            commands.add(command, byPlayer);
        });
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.add("history", commands);
        if (!windows.isEmpty()) {
            JsonObject byCommand = new JsonObject();
            windows.forEach((command, used) -> {
                JsonObject entries = new JsonObject();
                used.forEach((window, last) -> entries.addProperty(Long.toString(window), last));
                byCommand.add(command, entries);
            });
            root.add("windows", byCommand);
        }
        AtomicFiles.write(file, GSON.toJson(root));
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
