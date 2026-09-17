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
 * <pre>{"formatVersion": 1, "history": {"gamemode": {"&lt;uuid&gt;": [1789000000000, ...]}}}</pre>
 * Timestamps are Unix epoch milliseconds. Pure Java, no Minecraft types, so it is unit-tested directly.
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

    /** Replaces {@code file} atomically with {@code history}. */
    static void write(Path file, Map<String, Map<UUID, List<Long>>> history) throws IOException {
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
