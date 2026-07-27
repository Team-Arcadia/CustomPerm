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
 * Snapshot immutable de la configuration CustomPerm.
 * Obtenu via ConfigManager.getSnapshot() — jamais construit directement.
 * Les sous-configs (grades, aliases, commands) sont les mêmes objets GSON-désérialisés ;
 * ne pas les muter après construction du snapshot.
 */
public record ConfigSnapshot(
        GradesConfig grades,
        AliasesConfig aliases,
        CommandsConfig commands,
        SettingsConfig settings,
        RateLimitsConfig rateLimits
) {}
