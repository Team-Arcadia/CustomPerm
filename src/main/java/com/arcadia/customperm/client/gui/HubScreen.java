/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui;

import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Landing menu for the CustomPerm TesseraUI panel, opened by {@code /customperm gui} (no
 * argument). Purely client-side navigation — three buttons that open the Grades, Aliases and
 * Status screens. Rendered from the {@code customperm:ui/hub} HTML template; it carries no
 * server data, so unlike {@link AbstractSyncedScreen} it does not request a sync.
 */
public final class HubScreen extends TesseraScreen {

    private static final int MAX_W = 240;
    private static final int MAX_H = 190;

    private TesseraPanel root;

    public HubScreen() {
        super(Component.literal("CustomPerm - Admin"));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_W);
        int h = Math.min(this.height - 16, MAX_H);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        Map<String, Runnable> handlers = new HashMap<>();
        handlers.put("openGrades", () -> Minecraft.getInstance().setScreen(new GradesScreen()));
        handlers.put("openAliases", () -> Minecraft.getInstance().setScreen(new AliasesScreen()));
        handlers.put("openStatus", () -> Minecraft.getInstance().setScreen(new StatusScreen()));

        TesseraTemplate tpl = TesseraTemplate.load("customperm:ui/hub");
        root = TesseraTemplateRenderer.build(tpl, TesseraModel.EMPTY, handlers, x, y, w, h);
        root.layout();
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }

    // TesseraScreen provides neither render() nor mouseClicked() — the concrete screen must draw
    // its panel and forward clicks, or it opens blank and unresponsive.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (root != null) {
            root.render(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root != null && root.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
