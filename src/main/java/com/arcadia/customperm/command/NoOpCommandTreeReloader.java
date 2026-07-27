/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.config.ConfigSnapshot;
import net.minecraft.server.MinecraftServer;

/**
 * Stub no-op de {@link ICommandTreeReloader} utilisé jusqu'à ce qu'É2.6
 * fournisse l'implémentation complète dans {@code CommandTreeRewriter}.
 *
 * <p>Fonctionnellement correct pour H1.3 : les predicats de CommandTreeRewriter
 * étant dynamiques (ils relisent {@code configManager.getCommands()} à l'évaluation),
 * aucune action structurelle n'est nécessaire après reload pour maintenir
 * la cohérence des permissions.</p>
 */
public class NoOpCommandTreeReloader implements ICommandTreeReloader {

    @Override
    public void onConfigReload(ConfigSnapshot snapshot, MinecraftServer server) {
        // Intentionnellement vide — implémentation concrète fournie par É2.6
    }
}
