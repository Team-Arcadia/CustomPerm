/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

/**
 * Immutable snapshot of the CustomPerm configuration.
 * Obtained through ConfigManager.getSnapshot(), never built directly.
 * The sub-configs (grades, aliases, commands) are the GSON-deserialized objects themselves:
 * do not mutate them once the snapshot exists.
 */
public record ConfigSnapshot(
        GradesConfig grades,
        AliasesConfig aliases,
        CommandsConfig commands,
        SettingsConfig settings,
        RateLimitsConfig rateLimits
) {}
