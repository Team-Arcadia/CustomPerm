/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TesseraUI counterpart of {@code /customperm alias ...}, rendered from the
 * {@code customperm:ui/aliases} HTML template. Actions reuse the exact same server-side command
 * (via a prefilled {@link ChatScreen}) instead of duplicating alias CRUD logic client-side.
 */
public final class AliasesScreen extends AbstractSyncedScreen {

    public AliasesScreen() {
        super(Component.literal("CustomPerm - Aliases"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        Map<String, String> data = new LinkedHashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        data.put("noAliases", String.valueOf(payload.aliases().isEmpty()));
        handlers.put("back", this::openHub);
        handlers.put("refresh", this::requestRefresh);
        handlers.put("createAlias", () -> openChat("/customperm alias add "));

        // v-for count key, then one indexed entry per field (alias.<field>.<i>).
        data.put("aliases", String.valueOf(payload.aliases().size()));
        int i = 0;
        for (Map.Entry<String, Integer> entry : payload.aliases().entrySet()) {
            String name = entry.getKey();
            int stepCount = entry.getValue();
            data.put("alias.name." + i, name);
            data.put("alias.stepCount." + i, String.valueOf(stepCount));

            String addKey = "alias.addstep." + i;
            String stepsKey = "alias.steps." + i;
            String remKey = "alias.remove." + i;
            data.put("alias.addStepAction." + i, addKey);
            data.put("alias.stepsAction." + i, stepsKey);
            data.put("alias.removeAction." + i, remKey);
            handlers.put(addKey, () -> openChat("/customperm alias addstep " + name + " "));
            handlers.put(stepsKey, () -> openChat("/customperm alias steps " + name));
            handlers.put(remKey, () -> openChat("/customperm alias remove " + name));
            i++;
        }

        TesseraTemplate tpl = TesseraTemplate.load("customperm:ui/aliases");
        return TesseraTemplateRenderer.build(tpl, TesseraModel.of(data), handlers,
                originX(), originY(), panelW(), panelH());
    }

    private static void openChat(String prefilled) {
        Minecraft.getInstance().setScreen(new ChatScreen(prefilled));
    }
}
