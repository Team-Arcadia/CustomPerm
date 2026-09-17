/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.client.gui.kit.Atlas;
import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.CpTile;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.DashboardData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.perm.BackendKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Landing page: which backend decides permissions, what CustomPerm manages, and what needs the
 * admin's attention. Alerts are listed in full here, where chat only showed them once.
 */
public final class DashboardScreen extends AdminScreen {

    private static final int CARD_HEIGHT = 34;
    private static final int TILE_HEIGHT = 42;
    private static final int GAP = 6;
    private static final int ALERT_ROW = 34;

    private DashboardData data;
    private int reloadWidth;

    public DashboardScreen(GuiContext context, DashboardData data) {
        super(Component.literal("CustomPerm"), context);
        this.data = data;
    }

    @Override
    public GuiPage page() {
        return GuiPage.DASHBOARD;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.SHIELD;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (DashboardData) newData;
    }

    // ------------------------------------------------------------------ layout

    private Rect card() {
        return layout.content().top(CARD_HEIGHT);
    }

    private Rect tiles() {
        return layout.content().belowTop(CARD_HEIGHT + GAP).top(TILE_HEIGHT);
    }

    private Rect alertsTitle() {
        return layout.content().belowTop(CARD_HEIGHT + TILE_HEIGHT + 2 * GAP + 2).top(12);
    }

    private Rect actionBar() {
        return layout.content().bottom(Atlas.BUTTON_HEIGHT);
    }

    private Rect alertsList() {
        Rect title = alertsTitle();
        return new Rect(title.x(), title.bottom() + 2, title.w(),
                actionBar().y() - GAP - title.bottom() - 2);
    }

    @Override
    protected void buildPage() {
        Rect row = tiles();
        int w = (row.w() - 3 * GAP) / 4;
        List<CpTile> tiles = List.of(
                new CpTile(Icon.COMMAND, "Commands", String.valueOf(data.exposedCommands()),
                        "of " + data.dispatcherCommands() + " total", Palette.TEXT, () -> navigate(GuiPage.COMMANDS)),
                new CpTile(Icon.ALIAS, "Aliases", String.valueOf(data.aliases()), "defined", Palette.TEXT, null),
                new CpTile(Icon.CLOCK, "Limits", data.rateLimitsEnabled() + " / " + data.rateLimits(),
                        "enabled", Palette.TEXT, null),
                gradesTile());
        for (int i = 0; i < tiles.size(); i++) {
            addRenderableWidget(tiles.get(i).at(new Rect(row.x() + i * (w + GAP), row.y(), w, row.h())));
        }

        CpList<DashboardData.Alert> alerts = new CpList<DashboardData.Alert>(Component.literal("Alerts"), ALERT_ROW)
                .renderer(this::renderAlert)
                .label(DashboardData.Alert::message)
                .emptyText("No alerts: everything CustomPerm manages is working.")
                .at(alertsList());
        alerts.setItems(data.alerts());
        addRenderableWidget(alerts);

        Rect bar = actionBar();
        CpButton reload = CpButton.neutral(Component.literal("Reload config"), this::confirmReload)
                .icon(Icon.REFRESH);
        reloadWidth = reload.preferredWidth(font, 8);
        addRenderableWidget(reload.at(bar.right(reloadWidth)));
    }

    private CpTile gradesTile() {
        if (context.backend().usesInternalGrades()) {
            return new CpTile(Icon.SHIELD, "Grades", String.valueOf(data.grades()),
                    data.playersWithGrades() + (data.playersWithGrades() == 1 ? " player" : " players"),
                    Palette.TEXT, null);
        }
        return new CpTile(Icon.SHIELD, "Grades", "LuckPerms", "uses groups", Palette.TEXT_DIM, null);
    }

    private void confirmReload() {
        confirm("Reload configuration",
                "Re-reads grades, aliases, exposed commands and rate limits from disk. "
                        + "Changes that could not be saved are lost.",
                "Reload",
                () -> act(GuiAction.RELOAD));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackendCard(g);
        sectionTitle(g, "Alerts (" + data.alerts().size() + ")", alertsTitle());

        if (data.luckPermsInstalled()) {
            Rect bar = actionBar();
            Skin.text(g, font, "LuckPerms fallback: " + data.fallbackMode(),
                    bar.x(), bar.y() + (bar.h() - 8) / 2, bar.w() - reloadWidth - GAP, Palette.TEXT_MUTE);
        }
    }

    private void renderBackendCard(GuiGraphics g) {
        Rect card = card();
        Skin.panel(g, card);
        BackendKind backend = context.backend();
        int color = switch (backend) {
            case INTERNAL, LUCKPERMS -> Palette.GOOD;
            case INTERNAL_FALLBACK -> Palette.WARN;
            case DENY -> Palette.DANGER;
        };
        Rect inner = card.inset(8, 6);
        Skin.dot(g, inner.x(), inner.y() + 4, color);
        int x = inner.x() + Atlas.DOT_SIZE + 6;
        Skin.text(g, font, "Permissions backend: " + backend.label(), x, inner.y(), inner.right() - x, Palette.TEXT);
        Skin.text(g, font, describe(backend), x, inner.y() + 12, inner.right() - x, Palette.TEXT_DIM);
    }

    private static String describe(BackendKind backend) {
        return switch (backend) {
            case INTERNAL -> "Permissions come from CustomPerm grades (grades.json).";
            case LUCKPERMS -> "Permissions are resolved by LuckPerms.";
            case INTERNAL_FALLBACK -> "LuckPerms is unavailable: internal grades answer until the server restarts.";
            case DENY -> "LuckPerms is unavailable: every permission CustomPerm manages is denied until restart.";
        };
    }

    private void renderAlert(GuiGraphics g, net.minecraft.client.gui.Font font, DashboardData.Alert alert,
                             Rect row, boolean hovered, boolean selected) {
        Rect inner = row.inset(6, 4);
        Skin.icon(g, Icon.WARN, inner.x(), inner.y() + 1, Palette.WARN);
        int x = inner.x() + Atlas.ICON_SIZE + 6;
        List<FormattedCharSequence> lines = font.split(Component.literal(alert.message()), inner.right() - x);
        int shown = Math.min(3, lines.size());
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines.get(i), x, inner.y() + i * 9, Palette.TEXT, false);
        }
    }
}
