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
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code aliases.json} cut into rows, {@code alias:<name>} per alias, its steps in order. */
public final class AliasesCodec implements PartCodec<AliasesConfig> {

    public static final String PART = "aliases";
    public static final String ALIAS = "alias:";

    @Override
    public String part() {
        return PART;
    }

    @Override
    public Map<String, String> split(AliasesConfig config) {
        Map<String, String> rows = new HashMap<>();
        config.aliases.forEach((name, steps) -> {
            if (steps != null && !steps.isEmpty()) rows.put(ALIAS + name, CanonicalJson.GSON.toJson(steps));
        });
        return rows;
    }

    @Override
    public void patch(AliasesConfig config, String holder, String body) {
        if (!holder.startsWith(ALIAS)) return;
        String name = holder.substring(ALIAS.length());
        if (body == null) {
            config.aliases.remove(name);
            return;
        }
        List<String> steps = new ArrayList<>();
        JsonParser.parseString(body).getAsJsonArray().forEach(step -> steps.add(step.getAsString()));
        config.aliases.put(name, steps);
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
