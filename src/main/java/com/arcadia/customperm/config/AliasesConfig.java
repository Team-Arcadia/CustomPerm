/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AliasesConfig {
    /**
     * alias name -> ordered list of commands (without leading slash) to execute in sequence.
     * Example: "heal" -> ["effect give @s minecraft:instant_health 1 100", "effect give @s minecraft:saturation 1 100"].
     * A single-step alias is just a list of one element.
     */
    public Map<String, List<String>> aliases = new LinkedHashMap<>();

    public void normalize() {
        if (aliases == null) aliases = new LinkedHashMap<>();
    }
}
