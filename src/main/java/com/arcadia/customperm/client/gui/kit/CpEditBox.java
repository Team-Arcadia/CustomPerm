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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Text input in the interface style. Vanilla {@link EditBox} keeps all its editing behaviour
 * (selection, clipboard, word jumps, narration); only the frame changes.
 *
 * <p>The vanilla frame is a sprite drawn inside {@code renderWidget} before the text, with no hook
 * in between. This widget therefore runs the vanilla box unbordered, positioned on the text line
 * only, and remembers the full frame separately: the frame is drawn first, and hit testing uses
 * the frame, so a click anywhere in the field focuses it rather than only a click on the 8-pixel
 * text line.
 */
public class CpEditBox extends EditBox {

    private static final int PAD_X = 4;

    private Rect frame = new Rect(0, 0, 0, Atlas.INPUT_HEIGHT);
    private Runnable onSubmit;
    private String hint = "";

    public CpEditBox(Component narration, int maxLength) {
        super(Minecraft.getInstance().font, 0, 0, 0, 8, narration);
        setBordered(false);
        setMaxLength(maxLength);
        setTextColor(Palette.TEXT);
        setTextColorUneditable(Palette.TEXT_MUTE);
        setTextShadow(false);
    }

    /** Places the field by its outer frame; the vanilla box is fitted to the text line inside it. */
    public CpEditBox at(Rect r) {
        this.frame = r;
        setRectangle(Math.max(0, r.w() - 2 * PAD_X), 8, r.x() + PAD_X, r.y() + (r.h() - 8) / 2);
        applyHint();
        return this;
    }

    /** Placeholder shown while empty and unfocused, cut to the field width (vanilla does not clip it). */
    public CpEditBox hint(Component hint) {
        this.hint = hint.getString();
        applyHint();
        return this;
    }

    private void applyHint() {
        String text = getWidth() > 0 ? Skin.ellipsize(Minecraft.getInstance().font, hint, getWidth()) : hint;
        setHint(Component.literal(text).withColor(Palette.TEXT_MUTE));
    }

    /** A value set by the page, not typed, shows from its start: a long one otherwise reads as its tail. */
    @Override
    public void setValue(String text) {
        super.setValue(text);
        if (!isFocused()) moveCursorToStart(false);
    }

    public CpEditBox onChange(Consumer<String> listener) {
        setResponder(listener);
        return this;
    }

    /** Runs when Enter is pressed while the field has focus. */
    public CpEditBox onSubmit(Runnable action) {
        this.onSubmit = action;
        return this;
    }

    public Rect frame() {
        return frame;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (onSubmit != null && isFocused() && (keyCode == 257 || keyCode == 335)) {
            onSubmit.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && active && frame.contains(mouseX, mouseY);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return visible && active && frame.contains(mouseX, mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Skin.input(g, frame, isFocused(), active);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
