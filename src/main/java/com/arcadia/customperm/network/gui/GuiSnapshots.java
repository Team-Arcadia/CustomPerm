/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.notify.AdminNotifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds page data from the live server state. Server thread only: the config maps are mutated by
 * commands and actions on that thread without synchronisation.
 */
public final class GuiSnapshots {

    private GuiSnapshots() {
    }

    public static GuiContext context(ServerPlayer player) {
        return new GuiContext(CustomPerm.backendKind(), GuiAccess.editMask(player),
                AdminNotifier.activeAlerts().size());
    }

    public static GuiPageData page(GuiPage page, ServerPlayer player) {
        return switch (page) {
            case DASHBOARD -> dashboard(player.getServer());
            case COMMANDS -> commands(player.getServer());
        };
    }

    static CommandsData commands(MinecraftServer server) {
        ConfigManager config = CustomPerm.configManager;
        Set<String> exposed = config.getCommands().grantedCommands;
        Set<String> aliases = config.getAliases().aliases.keySet();
        var rules = config.getRateLimits().rules;

        Set<String> dispatcher = new TreeSet<>();
        if (server != null) {
            server.getCommands().getDispatcher().getRoot().getChildren().forEach(node -> dispatcher.add(node.getName()));
        }
        dispatcher.remove("customperm");
        Set<String> names = new TreeSet<>(dispatcher);
        names.addAll(exposed);

        List<CommandsData.Row> rows = new ArrayList<>();
        for (String name : names) {
            if (rows.size() == GuiCodecs.SERVER_LIST_MAX) break;
            var rule = rules.get(name);
            rows.add(new CommandsData.Row(name, exposed.contains(name),
                    config.getCommands().shouldPreserveOriginalRequires(name), aliases.contains(name),
                    rule != null && rule.enabled, !dispatcher.contains(name)));
        }
        return new CommandsData(rows, names.size() > rows.size());
    }

    static DashboardData dashboard(MinecraftServer server) {
        ConfigManager config = CustomPerm.configManager;
        int dispatcher = server == null ? 0 : server.getCommands().getDispatcher().getRoot().getChildren().size();
        var rules = config.getRateLimits().rules;
        int enabledRules = (int) rules.values().stream().filter(rule -> rule.enabled).count();

        List<DashboardData.Alert> alerts = new ArrayList<>();
        AdminNotifier.activeAlerts().forEach((key, message) -> {
            if (alerts.size() < GuiCodecs.ALERTS_MAX) alerts.add(new DashboardData.Alert(key.name(), message));
        });

        return new DashboardData(
                config.getSettings().luckPermsFallbackMode,
                CustomPerm.isLuckPermsPresent(),
                dispatcher,
                CustomPerm.isDirectCommandExposureEnabled() ? config.getCommands().grantedCommands.size() : 0,
                config.getAliases().aliases.size(),
                rules.size(),
                enabledRules,
                config.getGrades().grades.size(),
                config.getGrades().userGrades.size(),
                !config.isDiskWritable(),
                alerts);
    }
}
