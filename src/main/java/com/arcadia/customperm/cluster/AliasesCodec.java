/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.config.AliasesConfig.Parameter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code aliases.json} cut into rows, {@code alias:<name>} per alias, its steps in order and the
 * arguments it takes.
 *
 * <p>A row is an object, {@code {"steps":[...],"params":[...]}}. A bare array is read as steps with
 * no argument, which is what a server on a version from before the feature writes: a cluster whose
 * servers are not all upgraded keeps working, the older ones simply ignoring the arguments.
 */
public final class AliasesCodec implements PartCodec<AliasesConfig> {

    public static final String PART = "aliases";
    public static final String ALIAS = "alias:";

    private static final String STEPS = "steps";
    private static final String PARAMS = "params";

    @Override
    public String part() {
        return PART;
    }

    @Override
    public Map<String, String> split(AliasesConfig config) {
        Map<String, String> rows = new HashMap<>();
        config.aliases.forEach((name, steps) -> {
            if (steps == null || steps.isEmpty()) return;
            JsonObject row = new JsonObject();
            row.add(STEPS, CanonicalJson.GSON.toJsonTree(steps));
            List<Parameter> parameters = config.parameters(name);
            if (!parameters.isEmpty()) row.add(PARAMS, CanonicalJson.GSON.toJsonTree(parameters));
            List<String> servers = config.aliasServers.get(name);
            if (servers != null && !servers.isEmpty()) row.add(CommandsCodec.SERVERS, CanonicalJson.GSON.toJsonTree(servers));
            rows.put(ALIAS + name, CanonicalJson.GSON.toJson(row));
        });
        return rows;
    }

    @Override
    public void patch(AliasesConfig config, String holder, String body) {
        if (!holder.startsWith(ALIAS)) return;
        String name = holder.substring(ALIAS.length());
        config.aliasServers.remove(name);
        if (body == null) {
            config.aliases.remove(name);
            config.aliasParameters.remove(name);
            return;
        }
        JsonElement row = JsonParser.parseString(body);
        JsonArray rawSteps = row.isJsonArray() ? row.getAsJsonArray()
                : row.getAsJsonObject().getAsJsonArray(STEPS);
        List<String> steps = new ArrayList<>();
        if (rawSteps != null) rawSteps.forEach(step -> steps.add(step.getAsString()));
        config.aliases.put(name, steps);

        List<Parameter> parameters = new ArrayList<>();
        if (row.isJsonObject() && row.getAsJsonObject().has(PARAMS)) {
            row.getAsJsonObject().getAsJsonArray(PARAMS).forEach(parameter ->
                    parameters.add(CanonicalJson.GSON.fromJson(parameter, Parameter.class)));
        }
        if (parameters.isEmpty()) config.aliasParameters.remove(name);
        else config.aliasParameters.put(name, parameters);
        List<String> servers = row.isJsonObject() ? CommandsCodec.readServers(row.getAsJsonObject()) : null;
        if (servers != null) config.aliasServers.put(name, servers);
    }

    @Override
    public void afterPatch(AliasesConfig config) {
        config.normalize();
    }

    @Override
    public AliasesConfig empty() {
        AliasesConfig config = new AliasesConfig();
        config.normalize();
        return config;
    }
}
