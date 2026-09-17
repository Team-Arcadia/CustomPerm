/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A figure with a label: "Aliases / 12 / 3 rate limited". Clickable when it leads somewhere, in which
 * case it behaves as a button (focus, Enter, narration); otherwise a static panel that Tab skips.
 */
public class CpTile extends AbstractButton {

    private final Icon icon;
    private final String value;
    private final String detail;
    private final int valueColor;
    private final Runnable action;

    public CpTile(Icon icon, String label, String value, String detail, int valueColor, Runnable action) {
        super(0, 0, 0, 0, Component.literal(label));
        this.icon = icon;
        this.value = value;
        this.detail = detail;
        this.valueColor = valueColor;
        this.action = action;
        this.active = action != null;
    }

    public CpTile at(Rect r) {
        setRectangle(r.w(), r.h(), r.x(), r.y());
        return this;
    }

    @Override
    public void onPress() {
        if (action != null) action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        Rect r = new Rect(getX(), getY(), getWidth(), getHeight());
        boolean hot = action != null && isHovered();
        g.fill(r.x(), r.y(), r.right(), r.bottom(), hot ? Palette.BG3 : Palette.BG2);
        Skin.outline(g, r, hot ? Palette.LINE_STRONG : Palette.LINE);

        Rect inner = r.inset(6, 5);
        Skin.icon(g, icon, inner.x(), inner.y(), Palette.TEXT_MUTE);
        Skin.text(g, font, getMessage().getString(), inner.x() + Atlas.ICON_SIZE + 4, inner.y(),
                inner.w() - Atlas.ICON_SIZE - 4, Palette.TEXT_DIM);
        Skin.text(g, font, value, inner.x(), inner.y() + 12, inner.w(), valueColor);
        Skin.text(g, font, detail, inner.x(), inner.y() + 23, inner.w(), Palette.TEXT_MUTE);
        if (action != null && isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
            Skin.focusRing(g, r);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.literal(getMessage().getString() + ": " + value + ", " + detail));
    }
}
