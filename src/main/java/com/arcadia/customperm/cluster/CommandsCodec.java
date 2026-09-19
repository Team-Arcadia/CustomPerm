/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.CommandsConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** {@code commands.json} cut into rows, {@code command:<name>} per exposed command or preserve-original choice. */
public final class CommandsCodec implements PartCodec<CommandsConfig> {

    public static final String PART = "commands";
    public static final String COMMAND = "command:";

    @Override
    public String part() {
        return PART;
    }

    @Override
    public Map<String, String> split(CommandsConfig config) {
        Set<String> names = new TreeSet<>(config.grantedCommands);
        names.addAll(config.preserveOriginalRequires.keySet());
        Map<String, String> rows = new HashMap<>();
        for (String name : names) {
            JsonObject body = new JsonObject();
            body.addProperty("exposed", config.grantedCommands.contains(name));
            Boolean preserve = config.preserveOriginalRequires.get(name);
            if (preserve != null) body.addProperty("preserveOriginal", preserve);
            rows.put(COMMAND + name, CanonicalJson.GSON.toJson(body));
        }
        return rows;
    }

    @Override
    public void patch(CommandsConfig config, String holder, String body) {
        if (!holder.startsWith(COMMAND)) return;
        String name = holder.substring(COMMAND.length());
        config.grantedCommands.remove(name);
        config.preserveOriginalRequires.remove(name);
        if (body == null) return;
        JsonObject object = JsonParser.parseString(body).getAsJsonObject();
        if (object.has("exposed") && object.get("exposed").getAsBoolean()) config.grantedCommands.add(name);
        JsonElement preserve = object.get("preserveOriginal");
        if (preserve != null && !preserve.isJsonNull()) config.preserveOriginalRequires.put(name, preserve.getAsBoolean());
    }

    @Override
    public void afterPatch(CommandsConfig config) {
        config.normalize();
    }

    @Override
    public CommandsConfig empty() {
        CommandsConfig config = new CommandsConfig();
        config.normalize();
        return config;
    }
}
