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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** {@code commands.json} cut into rows, {@code command:<name>} per exposed command or preserve-original choice. */
public final class CommandsCodec implements PartCodec<CommandsConfig> {

    public static final String PART = "commands";
    public static final String COMMAND = "command:";
    static final String SERVERS = "servers";

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
            // Written only when set, so a row without a list keeps the body it had before lists existed.
            List<String> servers = config.commandServers.get(name);
            if (servers != null && !servers.isEmpty()) body.add(SERVERS, CanonicalJson.GSON.toJsonTree(servers));
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
        config.commandServers.remove(name);
        if (body == null) return;
        JsonObject object = JsonParser.parseString(body).getAsJsonObject();
        if (object.has("exposed") && object.get("exposed").getAsBoolean()) config.grantedCommands.add(name);
        JsonElement preserve = object.get("preserveOriginal");
        if (preserve != null && !preserve.isJsonNull()) config.preserveOriginalRequires.put(name, preserve.getAsBoolean());
        List<String> servers = readServers(object);
        if (servers != null) config.commandServers.put(name, servers);
    }

    /** The {@code servers} array of a row body, or null when it has none. */
    static List<String> readServers(JsonObject object) {
        JsonElement raw = object.get(SERVERS);
        if (raw == null || !raw.isJsonArray()) return null;
        List<String> servers = new ArrayList<>();
        raw.getAsJsonArray().forEach(name -> servers.add(name.getAsString()));
        return com.arcadia.customperm.config.ServerScope.normalize(servers);
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
