/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpScreen;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Base of the CustomPerm admin pages: navigation sidebar, header toolbar, and the round trip with the
 * server. A page never changes configuration locally: it sends a {@link GuiAction}, shows the result on
 * its status line, and redraws from the page the server pushes back.
 *
 * <p><strong>Refresh.</strong> {@link #refresh} replaces the page data and rebuilds the widgets.
 * Subclasses keep what the admin is doing (search text, selected row, half-typed form) in fields
 * that survive the rebuild, and restore it in {@link #buildPage()}.
 */
public abstract class AdminScreen extends CpScreen {

    private static final int NAV_ROW = 18;
    private static final int TOOL = 16;

    protected GuiContext context;

    protected AdminScreen(Component title, GuiContext context) {
        super(title);
        this.context = context;
    }

    /** The page this screen shows. */
    public abstract GuiPage page();

    /** Stores fresh page data; the widgets are rebuilt right after. */
    protected abstract void apply(GuiPageData data);

    /** Adds the page widgets inside {@code layout.content()}. */
    protected abstract void buildPage();

    /** Applies a server push for this page, keeping the screen open. */
    public final void refresh(GuiContext newContext, GuiPageData data) {
        this.context = newContext;
        apply(data);
        rebuild();
    }

    /**
     * Rebuilds the widgets and gives keyboard focus back to the widget that had it, when that widget
     * instance survives the rebuild (screens keep their list and search box across rebuilds). Without
     * this, moving the selection with the arrow keys would drop focus at the first redraw.
     */
    protected final void rebuild() {
        GuiEventListener focused = getFocused();
        rebuildWidgets();
        if (focused != null && children().contains(focused)) setFocused(focused);
    }

    /** Whether this admin may write to {@code area}; screens render read-only otherwise. */
    protected final boolean canEdit(GuiArea area) {
        return context.canEdit(area);
    }

    @Override
    protected final boolean hasSidebar() {
        return true;
    }

    @Override
    protected final void build() {
        buildNavigation();
        buildToolbar();
        buildPage();
    }

    // ------------------------------------------------------------------ navigation

    private void buildNavigation() {
        if (!layout.hasSidebar()) return;
        Rect side = layout.sidebar().inset(4, 6);
        int y = side.y();
        for (AdminScreens.NavEntry entry : AdminScreens.navigation(context)) {
            CpButton button = CpButton.ghost(Component.literal(entry.label()), () -> navigate(entry.page()))
                    .icon(entry.icon())
                    .selected(entry.page() == page())
                    .at(side.x(), y, side.w(), NAV_ROW);
            addRenderableWidget(button);
            y += NAV_ROW + 2;
        }
    }

    private void buildToolbar() {
        Rect header = layout.header();
        int y = header.y() + (header.h() - TOOL) / 2;
        int x = header.right() - 4 - TOOL;
        addRenderableWidget(CpButton.ghost(Component.literal("Close"), this::onClose)
                .iconOnly(Icon.CROSS).at(x, y, TOOL, TOOL));
        x -= TOOL + 2;
        addRenderableWidget(CpButton.ghost(Component.literal("Refresh"), () -> navigate(page()))
                .iconOnly(Icon.REFRESH).at(x, y, TOOL, TOOL));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderContent(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (context.alertCount() > 0 && layout.hasSidebar()) {
            // Alert badge on the dashboard entry, whichever page is open.
            Rect side = layout.sidebar().inset(4, 6);
            String count = String.valueOf(context.alertCount());
            int w = font.width(count) + 6;
            int x = side.right() - w - 4;
            int y = side.y() + (NAV_ROW - 10) / 2;
            g.fill(x, y, x + w, y + 10, Palette.DANGER);
            Skin.text(g, font, count, x + 3, y + 1, Palette.ON_COLOR);
        }
    }

    /** Non-widget page content, drawn under the widgets. */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    // ------------------------------------------------------------------ server round trip

    /** Asks the server for a page; the reply opens it (or refreshes this one). */
    protected final void navigate(GuiPage target) {
        info("Loading...");
        PacketDistributor.sendToServer(new GuiRequestPayload(target.id()));
    }

    /** Sends an action; the result lands on the status line and the page refreshes itself. */
    protected final void act(GuiAction action, String... args) {
        info("Working...");
        PacketDistributor.sendToServer(new GuiActionPayload(action.name(), List.of(args), page().id()));
    }

    // ------------------------------------------------------------------ drawing helpers

    /**
     * Draws wrapped text from {@code y} inside {@code area}, stopping at its bottom.
     *
     * @return the y below the last drawn line
     */
    protected final int paragraph(GuiGraphics g, String text, Rect area, int y, int color) {
        for (var line : font.split(Component.literal(text), area.w())) {
            if (y + 9 > area.bottom()) break;
            g.drawString(font, line, area.x(), y, color, false);
            y += 10;
        }
        return y;
    }

    /** Section title with a divider, as used above lists and forms. */
    protected final void sectionTitle(GuiGraphics g, String text, Rect area) {
        Skin.text(g, font, text.toUpperCase(java.util.Locale.ROOT), area.x(), area.y(), area.w(), Palette.TEXT_MUTE);
        Skin.hDivider(g, area.x(), area.right(), area.y() + 10);
    }
}
