/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.client.gui.kit.CpScreen;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.lp.LuckPermsScreen;
import com.arcadia.customperm.network.gui.AliasesData;
import com.arcadia.customperm.network.gui.CommandsData;
import com.arcadia.customperm.network.gui.DashboardData;
import com.arcadia.customperm.network.gui.GradesData;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.LogsData;
import com.arcadia.customperm.network.gui.PlayersData;
import com.arcadia.customperm.network.gui.LuckPermsData;
import com.arcadia.customperm.network.gui.RateLimitsData;
import com.arcadia.customperm.network.lp.LpDto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * Client entry point of the admin interface: routes server payloads to the open screen, opens pages,
 * and decides the navigation for the active backend.
 */
public final class AdminScreens {

    /** One sidebar entry. */
    public record NavEntry(GuiPage page, String label, Icon icon) {
    }

    /** Implemented by screens that display LuckPerms editor snapshots. */
    public interface LpSnapshotConsumer {
        void onLpSync(LpDto.Snapshot snapshot);
    }

    private AdminScreens() {
    }

    /** The sidebar for this backend. Entries that cannot apply are left out rather than shown as dead ends. */
    public static List<NavEntry> navigation(GuiContext context) {
        List<NavEntry> entries = new ArrayList<>();
        entries.add(new NavEntry(GuiPage.DASHBOARD, "Dashboard", Icon.HOME));
        entries.add(new NavEntry(GuiPage.COMMANDS, "Commands", Icon.COMMAND));
        entries.add(new NavEntry(GuiPage.ALIASES, "Aliases", Icon.ALIAS));
        entries.add(new NavEntry(GuiPage.RATE_LIMITS, "Rate limits", Icon.CLOCK));
        // Grades are always reachable, even when they decide nothing: an admin must be able to read the
        // fallback while LuckPerms is down. The page says whether it is active.
        entries.add(new NavEntry(GuiPage.GRADES, "Grades", Icon.SHIELD));
        // Nodes carried by a player themselves live next to the grades that hold the rest.
        entries.add(new NavEntry(GuiPage.PLAYERS, "Players", Icon.USER));
        // The LuckPerms editor needs the LuckPerms mod; installed but not running, it opens on a banner.
        if (context.luckPermsInstalled()) entries.add(new NavEntry(GuiPage.LUCKPERMS, "LuckPerms", Icon.LOCK));
        entries.add(new NavEntry(GuiPage.LOGS, "Logs", Icon.LOG));
        return entries;
    }

    /** Applies a page payload: refresh in place when that page is showing, open it when asked to. */
    public static void deliver(GuiPagePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        GuiPageData data = payload.data();
        if (mc.screen instanceof AdminScreen current && current.page() == data.page()) {
            current.refresh(payload.context(), data);
            return;
        }
        if (payload.open()) {
            mc.setScreen(create(payload.context(), data));
        }
    }

    /** Shows an action outcome on the open admin screen, if any. */
    public static void deliverResult(boolean success, String message) {
        if (Minecraft.getInstance().screen instanceof CpScreen screen) {
            screen.status(message, success);
        }
    }

    public static void deliverLpSync(LpDto.Snapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof LpSnapshotConsumer consumer) {
            consumer.onLpSync(snapshot);
        }
    }

    private static Screen create(GuiContext context, GuiPageData data) {
        return switch (data) {
            case DashboardData d -> new DashboardScreen(context, d);
            case CommandsData d -> new CommandsScreen(context, d);
            case AliasesData d -> new AliasesScreen(context, d);
            case RateLimitsData d -> new RateLimitsScreen(context, d);
            case GradesData d -> new GradesScreen(context, d);
            case PlayersData d -> new PlayersScreen(context, d);
            case LuckPermsData d -> new LuckPermsScreen(context, d);
            case LogsData d -> new LogsScreen(context, d);
        };
    }
}
