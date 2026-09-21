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

import java.util.List;
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
 *
 * <p><strong>Completion.</strong> A field given a {@link Completer} shows the rest of the best
 * candidate after the cursor, and a list of candidates under the field while it is typed in. Tab
 * takes the highlighted candidate, Up and Down move through the list, Down opens it on an empty
 * field, Escape closes it. Enter still submits what is typed, unless the admin moved through the
 * list first: then it takes the highlighted candidate, like a click on it. The list is drawn and
 * clicked through {@link CpScreen}, above every other widget.
 */
public class CpEditBox extends EditBox {

    private static final int PAD_X = 4;
    private static final int KEY_TAB = 258;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;
    /** Candidates visible at once; the list scrolls past them. */
    private static final int VISIBLE = 6;
    private static final int ROW_H = 11;

    private Rect frame = new Rect(0, 0, 0, Atlas.INPUT_HEIGHT);
    private Runnable onSubmit;
    private Consumer<String> listener;
    private String hint = "";

    private Completer completer;
    private Completer.Proposal proposal = Completer.Proposal.NONE;
    private boolean open;
    /** Whether the admin moved through the list, which makes Enter take the highlighted candidate. */
    private boolean browsed;
    private int selected;
    private int scroll;
    /** Screen size at the last draw, to keep the list on screen and to hit test it. */
    private int screenW = Integer.MAX_VALUE;
    private int screenH = Integer.MAX_VALUE;

    public CpEditBox(Component narration, int maxLength) {
        super(Minecraft.getInstance().font, 0, 0, 0, 8, narration);
        setBordered(false);
        setMaxLength(maxLength);
        setTextColor(Palette.TEXT);
        setTextColorUneditable(Palette.TEXT_MUTE);
        setTextShadow(false);
        super.setResponder(this::changed);
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
        this.listener = listener;
        return this;
    }

    /** The listener runs after the candidates are updated, so it sees the same state as the admin. */
    @Override
    public void setResponder(Consumer<String> responder) {
        this.listener = responder;
    }

    /** Proposes candidates while this field is typed in; see the class comment. */
    public CpEditBox completes(Completer completer) {
        // Read lazily: a page may wire a source that reads state it has not built yet.
        this.completer = completer;
        this.proposal = Completer.Proposal.NONE;
        return this;
    }

    private void changed(String text) {
        propose(isFocused());
        if (listener != null) listener.accept(text);
    }

    /** Recomputes the candidates; {@code show} opens the list when there is something typed to complete. */
    private void propose(boolean show) {
        proposal = completer == null ? Completer.Proposal.NONE : completer.propose(getValue());
        selected = 0;
        scroll = 0;
        browsed = false;
        open = show && !proposal.isEmpty() && proposal.start() < getValue().length();
        ghost();
    }

    /** Shows after the cursor the rest of the highlighted candidate, when it continues what is typed. */
    private void ghost() {
        String typed = getValue().substring(Math.min(proposal.start(), getValue().length()));
        String candidate = open && !proposal.isEmpty() ? proposal.candidates().get(selected) : null;
        boolean continues = candidate != null && !typed.isEmpty()
                && candidate.regionMatches(true, 0, typed, 0, typed.length());
        if (!continues) {
            setSuggestion(null);
            return;
        }
        // Vanilla draws the suggestion unclipped, over the next field: keep it to the room left in this one.
        var font = Minecraft.getInstance().font;
        int room = getWidth() - font.width(getValue()) - 1;
        String rest = room > 0 ? font.plainSubstrByWidth(candidate.substring(typed.length()), room) : "";
        setSuggestion(rest.isEmpty() ? null : rest);
    }

    /** Whether the candidate list shows: open, with candidates, the cursor at the end of the text. */
    public boolean completionsOpen() {
        return open && canConsumeInput() && !proposal.isEmpty() && getCursorPosition() == getValue().length();
    }

    /** Opens the list again after a rebuild took the focus away and gave it back. */
    public void reopenCompletions() {
        open = !proposal.isEmpty();
        ghost();
    }

    /** Closes the candidate list; returns whether it was open, so Escape closes the list before the screen. */
    public boolean closeCompletions() {
        boolean was = completionsOpen();
        open = false;
        ghost();
        return was;
    }

    private void take(int index) {
        String candidate = proposal.candidates().get(index);
        setValue(proposal.apply(getValue(), candidate));
        open = false;
        ghost();
    }

    private void move(int delta) {
        int count = proposal.candidates().size();
        selected = Math.floorMod(selected + delta, count);
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + VISIBLE) scroll = selected - VISIBLE + 1;
        browsed = true;
        ghost();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            open = false;
            ghost();
        }
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
        if (completionsOpen()) {
            switch (keyCode) {
                case KEY_TAB -> {
                    take(selected);
                    return true;
                }
                case KEY_DOWN -> {
                    move(1);
                    return true;
                }
                case KEY_UP -> {
                    move(-1);
                    return true;
                }
                case KEY_ENTER, KEY_KP_ENTER -> {
                    if (browsed) {
                        take(selected);
                        return true;
                    }
                }
                default -> {
                }
            }
        } else if (keyCode == KEY_DOWN && canConsumeInput() && completer != null
                && getCursorPosition() == getValue().length()) {
            propose(false);
            open = !proposal.isEmpty();
            ghost();
            if (open) return true;
        }
        if (onSubmit != null && isFocused() && (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER)) {
            open = false;
            onSubmit.run();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ candidate list

    private Rect listRect() {
        var font = Minecraft.getInstance().font;
        int rows = Math.min(VISIBLE, proposal.candidates().size());
        int widest = 0;
        for (String candidate : proposal.candidates()) widest = Math.max(widest, font.width(candidate));
        int w = Math.min(Math.max(frame.w(), widest + 2 * PAD_X + 4), Math.max(frame.w(), screenW - 8));
        int h = rows * ROW_H + 2;
        int x = Math.max(4, Math.min(frame.x(), screenW - 4 - w));
        int below = frame.bottom() + 1;
        int y = below + h <= screenH - 2 || frame.y() - h - 1 < 2 ? below : frame.y() - h - 1;
        return new Rect(x, y, w, h);
    }

    /** Draws the candidate list over everything else; called by {@link CpScreen} for the focused field. */
    public void renderCompletions(GuiGraphics g, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.screenW = screenWidth;
        this.screenH = screenHeight;
        if (!completionsOpen()) return;
        var font = Minecraft.getInstance().font;
        Rect r = listRect();
        List<String> candidates = proposal.candidates();
        String typed = getValue().substring(Math.min(proposal.start(), getValue().length()));
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(r.x(), r.y(), r.right(), r.bottom(), Palette.BG2);
        Skin.outline(g, r, Palette.LINE_STRONG);
        int rows = Math.min(VISIBLE, candidates.size() - scroll);
        int textW = r.w() - 2 * PAD_X - 3;
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            int y = r.y() + 1 + i * ROW_H;
            boolean hovered = mouseX >= r.x() && mouseX < r.right() && mouseY >= y && mouseY < y + ROW_H;
            if (index == selected) {
                g.fill(r.x() + 1, y, r.right() - 1, y + ROW_H, Palette.mix(Palette.BG2, Palette.ACCENT, 0.45f));
            } else if (hovered) {
                g.fill(r.x() + 1, y, r.right() - 1, y + ROW_H, Palette.BG3);
            }
            String candidate = candidates.get(index);
            boolean prefix = !typed.isEmpty() && candidate.regionMatches(true, 0, typed, 0, typed.length());
            if (prefix && font.width(candidate) <= textW) {
                // The typed part in the accent colour, so the eye finds what matched.
                int x = g.drawString(font, candidate.substring(0, typed.length()), r.x() + PAD_X, y + 2, Palette.ACCENT_HI, false);
                g.drawString(font, candidate.substring(typed.length()), x, y + 2, Palette.TEXT, false);
            } else {
                Skin.text(g, font, candidate, r.x() + PAD_X, y + 2, textW, Palette.TEXT);
            }
        }
        if (candidates.size() > VISIBLE) {
            int trackH = r.h() - 2;
            int thumbH = Math.max(6, trackH * VISIBLE / candidates.size());
            int thumbY = r.y() + 1 + (trackH - thumbH) * scroll / (candidates.size() - VISIBLE);
            g.fill(r.right() - 3, thumbY, r.right() - 1, thumbY + thumbH, Palette.LINE_STRONG);
        }
        g.pose().popPose();
    }

    /** Whether the candidate list is open under the pointer, where the widgets behind it must not look hovered. */
    public boolean completionsContain(double mouseX, double mouseY) {
        return completionsOpen() && listRect().contains(mouseX, mouseY);
    }

    /** Takes the clicked candidate; true when the click landed on the list, which then keeps it from the page. */
    public boolean clickCompletions(double mouseX, double mouseY) {
        if (!completionsOpen()) return false;
        Rect r = listRect();
        if (!r.contains(mouseX, mouseY)) return false;
        int index = scroll + (int) ((mouseY - r.y() - 1) / ROW_H);
        if (index >= scroll && index < proposal.candidates().size()) take(index);
        return true;
    }

    /** Scrolls the list; true when the wheel was over it. */
    public boolean scrollCompletions(double mouseX, double mouseY, double amount) {
        if (!completionsOpen() || !listRect().contains(mouseX, mouseY)) return false;
        int max = Math.max(0, proposal.candidates().size() - VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
        return true;
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
