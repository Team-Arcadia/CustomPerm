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
 * Point d'extension pour notifier le CommandTree d'un reload de configuration.
 *
 * <p>Implémentation courante (H1.3) : {@link NoOpCommandTreeReloader} — no-op.
 * Les predicats de {@code CommandTreeRewriter} sont dynamiques et lisent
 * {@code configManager.getCommands()} à l'évaluation ; aucune action structurelle
 * n'est nécessaire pour la cohérence des permissions.</p>
 *
 * <p>Implémentation É2.6 : {@code CommandTreeRewriter} implémentera cette interface
 * et réenregistrera les nœuds si la structure de l'arbre doit évoluer.</p>
 */
public interface ICommandTreeReloader {
    /**
     * Appelée après chaque hot-reload réussi de la configuration.
     *
     * @param snapshot Le nouveau snapshot de configuration (déjà appliqué dans ConfigManager)
     * @param server   Le serveur Minecraft — peut être null si aucun serveur actif
     */
    void onConfigReload(ConfigSnapshot snapshot, MinecraftServer server);
}
