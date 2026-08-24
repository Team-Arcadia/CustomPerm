/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.client.gui.lp.LpGroupsScreen;
import com.arcadia.customperm.client.gui.lp.LpTracksScreen;
import com.arcadia.customperm.client.gui.lp.LpUsersScreen;
import com.arcadia.customperm.network.GuiSyncPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Landing menu for the CustomPerm TesseraUI panel, opened by {@code /customperm gui} (no
 * argument). Rendered from the {@code customperm:ui/hub} HTML template.
 *
 * <p>The menu is backend-dependent, which is why it now requests a sync like every other screen
 * instead of being pure client-side navigation: with LuckPerms active it leads to the LuckPerms
 * editor (groups, players, tracks) and hides the internal grade screen, which is read-only in
 * that mode; without LuckPerms it leads to the internal grades. Offering both at once would
 * mean half the menu opens a screen that can only tell the admin it does not apply here.
 */
public final class HubScreen extends AbstractSyncedScreen {

    public HubScreen() {
        super(Component.literal("CustomPerm - Admin"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        Map<String, String> data = new LinkedHashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        boolean lp = payload.luckPermsActive();
        data.put("luckPermsActive", String.valueOf(lp));
        data.put("internalMode", String.valueOf(!lp));
        data.put("backendLabel", payload.backendLabel());

        handlers.put("openGrades", () -> open(new GradesScreen()));
        handlers.put("openAliases", () -> open(new AliasesScreen()));
        handlers.put("openStatus", () -> open(new StatusScreen()));
        handlers.put("openLpGroups", () -> open(new LpGroupsScreen()));
        handlers.put("openLpUsers", () -> open(new LpUsersScreen()));
        handlers.put("openLpTracks", () -> open(new LpTracksScreen()));

        TesseraTemplate tpl = TesseraTemplate.load("customperm:ui/hub");
        return TesseraTemplateRenderer.build(tpl, TesseraModel.of(data), handlers,
                originX(), originY(), panelW(), panelH());
    }

    private static void open(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
