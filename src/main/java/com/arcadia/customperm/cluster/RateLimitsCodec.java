/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.RateLimitsConfig;

import java.util.HashMap;
import java.util.Map;

/** {@code ratelimits.json} cut into rows, {@code rule:<command>} per rule. The counters are not in it. */
public final class RateLimitsCodec implements PartCodec<RateLimitsConfig> {

    public static final String PART = "ratelimits";
    public static final String RULE = "rule:";

    @Override
    public String part() {
        return PART;
    }

    @Override
    public Map<String, String> split(RateLimitsConfig config) {
        Map<String, String> rows = new HashMap<>();
        config.rules.forEach((name, rule) -> {
            if (rule != null) rows.put(RULE + name, CanonicalJson.GSON.toJson(rule));
        });
        return rows;
    }

    @Override
    public void patch(RateLimitsConfig config, String holder, String body) {
        if (!holder.startsWith(RULE)) return;
        String name = holder.substring(RULE.length());
        if (body == null) {
            config.rules.remove(name);
        } else {
            config.rules.put(name, CanonicalJson.GSON.fromJson(body, RateLimitsConfig.Rule.class));
        }
    }

    @Override
    public void afterPatch(RateLimitsConfig config) {
        config.normalize();
    }

    @Override
    public RateLimitsConfig empty() {
        RateLimitsConfig config = new RateLimitsConfig();
        config.normalize();
        return config;
    }
}
