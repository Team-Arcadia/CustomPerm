/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.kit;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Scrollable, selectable list of fixed-height rows. Replaces pagination: the whole list is in the
 * payload, and the admin scrolls with the wheel, the scrollbar or the keyboard.
 *
 * <p>Interaction: click selects, double-click or Enter activates, Up/Down/Page Up/Page Down/Home/End
 * move the selection and keep it in view. A row can host inline controls (a toggle, a delete
 * cross) through {@link #onRowClick}, which receives the click in row-local coordinates before
 * selection happens; row layouts compute the same {@link Rect}s for drawing and hit testing so the
 * two never disagree.
 *
 * <p>{@link #setItems} keeps the selection on the same item across a server refresh when an
 * identity function is set, so an edit that reorders or refills the list does not move the
 * admin's cursor.
 */
public class CpList<T> extends net.minecraft.client.gui.components.AbstractWidget {

    /** Draws one row's content; the row background is already painted. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(GuiGraphics g, Font font, T item, Rect row, boolean hovered, boolean selected);
    }

    /** Inline control hit test. Return true to consume the click (selection is then left alone). */
    @FunctionalInterface
    public interface RowClick<T> {
        boolean click(T item, Rect row, double mouseX, double mouseY);
    }

    private static final long DOUBLE_CLICK_MS = 300L;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;

    private final int rowHeight;
    private List<T> items = List.of();
    private int selected = -1;
    private double scroll;
    private boolean draggingThumb;
    private double dragOffset;
    private long lastClickAt;
    private int lastClickIndex = -1;

    private RowRenderer<T> renderer = (g, font, item, row, hovered, selected) ->
            Skin.textIn(g, font, String.valueOf(item), row, 6, Palette.TEXT);
    private Function<T, String> label = String::valueOf;
    private Function<T, ?> identity;
    private Consumer<T> onSelect = item -> { };
    private Consumer<T> onActivate = item -> { };
    private RowClick<T> onRowClick;
    private String emptyText = "";

    public CpList(Component narration, int rowHeight) {
        super(0, 0, 0, 0, narration);
        this.rowHeight = rowHeight;
    }

    // ------------------------------------------------------------------ configuration

    public CpList<T> at(Rect r) {
        setRectangle(r.w(), r.h(), r.x(), r.y());
        clampScroll();
        return this;
    }

    public CpList<T> renderer(RowRenderer<T> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** Plain-text label of an item, used for narration. */
    public CpList<T> label(Function<T, String> label) {
        this.label = label;
        return this;
    }

    /** Key that identifies an item across refreshes (a name, a UUID). */
    public CpList<T> identity(Function<T, ?> identity) {
        this.identity = identity;
        return this;
    }

    public CpList<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public CpList<T> onActivate(Consumer<T> onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    public CpList<T> onRowClick(RowClick<T> onRowClick) {
        this.onRowClick = onRowClick;
        return this;
    }

    public CpList<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    // ------------------------------------------------------------------ content

    /**
     * Replaces the rows. The selection follows the previously selected item when an identity is
     * set and the item is still present; otherwise it is cleared. The scroll position is kept.
     */
    public void setItems(List<T> newItems) {
        T previous = getSelected();
        this.items = List.copyOf(newItems);
        this.selected = -1;
        if (previous != null && identity != null) {
            Object key = identity.apply(previous);
            for (int i = 0; i < items.size(); i++) {
                if (Objects.equals(identity.apply(items.get(i)), key)) {
                    selected = i;
                    break;
                }
            }
        }
        clampScroll();
    }

    public List<T> items() {
        return items;
    }

    public T getSelected() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public int selectedIndex() {
        return selected;
    }

    /** Selects the item whose identity equals {@code key}, scrolling it into view. */
    public boolean selectByKey(Object key) {
        if (identity == null) return false;
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(identity.apply(items.get(i)), key)) {
                select(i, false);
                return true;
            }
        }
        return false;
    }

    public void clearSelection() {
        selected = -1;
    }

    // ------------------------------------------------------------------ geometry

    private int contentHeight() {
        return items.size() * rowHeight;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private boolean scrollbarVisible() {
        return contentHeight() > height;
    }

    private Rect rowsArea() {
        int sb = scrollbarVisible() ? Atlas.SCROLLBAR_WIDTH : 0;
        return new Rect(getX(), getY(), width - sb, height);
    }

    private Rect track() {
        return new Rect(getRight() - Atlas.SCROLLBAR_WIDTH, getY(), Atlas.SCROLLBAR_WIDTH, height);
    }

    private int thumbHeight() {
        int content = Math.max(1, contentHeight());
        return Math.max(12, Math.min(height, height * height / content));
    }

    private int thumbY() {
        int max = maxScroll();
        if (max == 0) return getY();
        return getY() + (int) Math.round(scroll / max * (height - thumbHeight()));
    }

    private Rect rowRect(int index) {
        Rect area = rowsArea();
        return new Rect(area.x(), area.y() + index * rowHeight - (int) Math.round(scroll), area.w(), rowHeight);
    }

    private int indexAt(double mouseY) {
        int index = (int) Math.floor((mouseY - getY() + scroll) / rowHeight);
        return index >= 0 && index < items.size() ? index : -1;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private void ensureVisible(int index) {
        int top = index * rowHeight;
        if (top < scroll) {
            scroll = top;
        } else if (top + rowHeight > scroll + height) {
            scroll = top + rowHeight - height;
        }
        clampScroll();
    }

    private void select(int index, boolean fromUser) {
        if (items.isEmpty()) return;
        int clamped = Math.max(0, Math.min(items.size() - 1, index));
        boolean changed = clamped != selected;
        selected = clamped;
        ensureVisible(clamped);
        if (changed || fromUser) onSelect.accept(items.get(clamped));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        Rect frame = new Rect(getX(), getY(), width, height);
        g.fill(frame.x(), frame.y(), frame.right(), frame.bottom(), Palette.BG1);

        if (items.isEmpty()) {
            if (!emptyText.isEmpty()) Skin.centeredText(g, font, emptyText, frame, Palette.TEXT_MUTE);
        } else {
            Rect area = rowsArea();
            g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            int first = Math.max(0, (int) Math.floor(scroll / rowHeight));
            int last = Math.min(items.size() - 1, (int) Math.ceil((scroll + height) / rowHeight));
            boolean mouseInRows = area.contains(mouseX, mouseY) && !draggingThumb;
            for (int i = first; i <= last; i++) {
                Rect row = rowRect(i);
                boolean hovered = mouseInRows && row.contains(mouseX, mouseY);
                Skin.row(g, row, i, hovered, i == selected);
                renderer.render(g, font, items.get(i), row, hovered, i == selected);
            }
            g.disableScissor();
        }

        if (scrollbarVisible()) {
            Rect track = track();
            Skin.scrollbar(g, track, thumbY(), thumbHeight(), draggingThumb || track.contains(mouseX, mouseY));
        }
        Skin.outline(g, frame, isFocused() ? Palette.LINE_STRONG : Palette.LINE);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        if (scrollbarVisible() && track().contains(mouseX, mouseY)) {
            int thumbTop = thumbY();
            if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight()) {
                draggingThumb = true;
                dragOffset = mouseY - thumbTop;
            } else {
                // Page towards the click, like a native scrollbar.
                scroll += mouseY < thumbTop ? -height : height;
                clampScroll();
            }
            return true;
        }

        int index = indexAt(mouseY);
        if (index < 0) return true;
        T item = items.get(index);
        if (onRowClick != null && onRowClick.click(item, rowRect(index), mouseX, mouseY)) return true;

        long now = Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClickAt = now;
        lastClickIndex = index;
        select(index, true);
        if (doubleClick) onActivate.accept(item);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingThumb) return false;
        int travel = height - thumbHeight();
        if (travel > 0) {
            scroll = (mouseY - dragOffset - getY()) / travel * maxScroll();
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = draggingThumb;
        draggingThumb = false;
        return was;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !isMouseOver(mouseX, mouseY) || !scrollbarVisible()) return false;
        scroll -= scrollY * rowHeight * 2;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || items.isEmpty()) return false;
        int page = Math.max(1, height / rowHeight - 1);
        switch (keyCode) {
            case KEY_UP -> select(selected < 0 ? 0 : selected - 1, true);
            case KEY_DOWN -> select(selected < 0 ? 0 : selected + 1, true);
            case KEY_PAGE_UP -> select(selected - page, true);
            case KEY_PAGE_DOWN -> select(selected + page, true);
            case KEY_HOME -> select(0, true);
            case KEY_END -> select(items.size() - 1, true);
            case KEY_ENTER, KEY_KP_ENTER -> {
                T item = getSelected();
                if (item == null) return false;
                onActivate.accept(item);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        T item = getSelected();
        if (item == null) {
            output.add(NarratedElementType.TITLE, Component.empty().append(getMessage())
                    .append(", " + items.size() + " entries"));
        } else {
            output.add(NarratedElementType.TITLE, Component.literal(label.apply(item)
                    + ", " + (selected + 1) + " of " + items.size()));
        }
        output.add(NarratedElementType.USAGE, Component.literal("Arrow keys to move, Enter to open"));
    }
}
