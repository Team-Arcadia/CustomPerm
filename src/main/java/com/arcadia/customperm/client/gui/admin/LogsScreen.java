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
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.LogsData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Activity log on two tabs: admin changes (CustomPerm commands, the interface, the LuckPerms editor and
 * LuckPerms itself) and commands typed by players, with the switches of the player log. The list shows
 * the latest entries, newest first; the panel under it shows the selected entry in full.
 */
public final class LogsScreen extends AdminScreen {

    private static final int ROW = 14;
    private static final int GAP = 6;
    private static final int TOOLBAR = 16;
    private static final int DETAIL = 62;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LogsData data;
    private boolean playersTab;
    /** Right edge of the player-log switches, for the retention text beside them. */
    private int switchesRight;

    private final CpEditBox search;
    private final CpList<LogsData.Entry> list;

    public LogsScreen(GuiContext context, LogsData data) {
        super(Component.literal("Logs"), context);
        this.data = data;
        this.search = new CpEditBox(Component.literal("Search logs"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .onChange(text -> refilter());
        this.list = new CpList<LogsData.Entry>(Component.literal("Log entries"), ROW)
                .renderer(this::renderEntry)
                .label(e -> e.who() + ", " + e.action() + (e.success() ? "" : ", refused"))
                .identity(e -> e.time() + "|" + e.server() + "|" + e.actor() + "|" + e.action())
                .onSelect(e -> { });
        refilter();
    }

    @Override
    public GuiPage page() {
        return GuiPage.LOGS;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.LOG;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (LogsData) newData;
        refilter();
    }

    @Override
    protected Banner banner() {
        if (!playersTab || data.playerLog()) return null;
        return new Banner(Icon.INFO, "Recording is off: player commands are not kept.", Palette.INFO);
    }

    private List<LogsData.Entry> current() {
        return playersTab ? data.players() : data.admin();
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        list.setItems(current().stream()
                .filter(e -> query.isEmpty() || matches(e, query))
                .toList());
        list.emptyText(!query.isEmpty() ? "No entry matches."
                : playersTab ? "No player command recorded." : "No admin change recorded yet.");
    }

    private static boolean matches(LogsData.Entry e, String query) {
        return e.who().toLowerCase(Locale.ROOT).contains(query)
                || e.action().toLowerCase(Locale.ROOT).contains(query)
                || e.result().toLowerCase(Locale.ROOT).contains(query)
                || e.source().toLowerCase(Locale.ROOT).contains(query);
    }

    // ------------------------------------------------------------------ layout

    private Rect toolbar() {
        return layout.content().top(TOOLBAR);
    }

    private Rect detail() {
        return layout.content().bottom(DETAIL);
    }

    /** The player tab has a second row for its switches. */
    private int top() {
        return TOOLBAR + GAP + (playersTab ? TOOLBAR + GAP : 0);
    }

    private Rect switchRow() {
        return new Rect(layout.content().x(), layout.content().y() + TOOLBAR + GAP, layout.content().w(), TOOLBAR);
    }

    private Rect listArea() {
        Rect c = layout.content();
        return new Rect(c.x(), c.y() + top(), c.w(), c.h() - top() - DETAIL - GAP);
    }

    @Override
    protected void buildPage() {
        Rect bar = toolbar();
        CpButton admin = CpButton.ghost(Component.literal("Admin"), () -> setTab(false)).icon(Icon.SHIELD).selected(!playersTab);
        int adminW = admin.preferredWidth(font, 6);
        CpButton players = CpButton.ghost(Component.literal("Players"), () -> setTab(true)).icon(Icon.USER).selected(playersTab);
        int playersW = players.preferredWidth(font, 6);

        addRenderableWidget(admin.at(bar.x(), bar.y(), adminW, bar.h()));
        addRenderableWidget(players.at(bar.x() + adminW + 2, bar.y(), playersW, bar.h()));
        int searchX = bar.x() + adminW + 2 + playersW + GAP;
        int searchW = Math.min(200, bar.right() - searchX);
        addRenderableWidget(search.at(new Rect(bar.right() - searchW, bar.y(), searchW, bar.h())));

        if (playersTab) {
            Rect row = switchRow();
            boolean editable = canEdit(GuiArea.LOGS);
            CpButton mask = CpButton.neutral(Component.literal(data.masking() ? "Masked" : "Unmasked"),
                            () -> act(GuiAction.LOG_MASK, String.valueOf(!data.masking())))
                    .icon(data.masking() ? Icon.LOCK : Icon.WARN)
                    .selected(data.masking())
                    .enabled(editable)
                    .tooltip(Component.literal(data.masking()
                            ? "Arguments of private message and password commands are masked. Click to record them in full."
                            : "Every argument is recorded, private messages and passwords included. Click to mask them."));
            int maskW = mask.preferredWidth(font, 6);

            CpButton record = (data.playerLog()
                    ? CpButton.neutral(Component.literal("Recording"), this::togglePlayerLog).selected(true)
                    : CpButton.accent(Component.literal("Record"), this::togglePlayerLog))
                    .icon(data.playerLog() ? Icon.CHECK : Icon.PLUS)
                    .enabled(editable)
                    .tooltip(Component.literal(data.playerLog()
                            ? "Player commands are recorded. Click to stop; recorded entries are kept."
                            : "Record every command players type."));
            int recordW = record.preferredWidth(font, 6);
            addRenderableWidget(record.at(row.x(), row.y(), recordW, row.h()));
            addRenderableWidget(mask.at(row.x() + recordW + 4, row.y(), maskW, row.h()));
            switchesRight = row.x() + recordW + 4 + maskW;
        }

        addRenderableWidget(list.at(listArea()));
    }

    private void setTab(boolean players) {
        if (playersTab == players) return;
        playersTab = players;
        list.clearSelection();
        refilter();
        rebuild();
    }

    private void togglePlayerLog() {
        if (data.playerLog()) {
            act(GuiAction.LOG_PLAYERS, "false");
            return;
        }
        confirm("Record player commands",
                "Personal data: every command players type is kept " + (data.retentionDays() == 0 ? "without a time limit" : data.retentionDays()
                        + " day(s)") + ", " + (data.masking() ? "sensitive arguments masked." : "arguments in full."),
                "Record",
                () -> act(GuiAction.LOG_PLAYERS, "true"));
    }

    // ------------------------------------------------------------------ rendering

    private static String when(long time) {
        ZonedDateTime at = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        return (at.toLocalDate().equals(LocalDate.now()) ? TIME : DAY_TIME).format(at);
    }

    private void renderEntry(GuiGraphics g, Font font, LogsData.Entry e, Rect r, boolean hovered, boolean selected) {
        int y = r.y() + (r.h() - 8) / 2;
        String time = when(e.time());
        Skin.text(g, font, time, r.x() + 4, y, Palette.TEXT_MUTE);
        int x = r.x() + 4 + font.width("00-00 00:00") + 6;
        Skin.dot(g, x, r.centerY(), e.success() ? Palette.GOOD : Palette.DANGER);
        x += 10;
        int actorW = Math.min(96, font.width(e.who()));
        Skin.text(g, font, e.who(), x, y, 96, Palette.TEXT);
        x += actorW + 6;
        Skin.text(g, font, e.action(), x, y, r.right() - x - 4, e.success() ? Palette.TEXT_DIM : Palette.DANGER);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (playersTab) {
            Rect row = switchRow();
            String note = data.retentionDays() == 0 ? "kept without a time limit" : "kept " + data.retentionDays() + " day(s)";
            Skin.text(g, font, note, switchesRight + GAP, row.y() + (row.h() - 8) / 2, row.right() - switchesRight - GAP,
                    Palette.TEXT_MUTE);
        }

        Rect panel = detail();
        Skin.panel(g, panel);
        Rect in = panel.inset(6);
        LogsData.Entry e = list.getSelected();
        if (e == null) {
            paragraph(g, "Select an entry to read it in full. The latest " + LogsData.ENTRIES_MAX + " entries are shown; files in "
                    + "the world's customperm/logs folder keep " + (data.retentionDays() == 0 ? "everything."
                    : data.retentionDays() + " day(s)."), in, in.y(), Palette.TEXT_MUTE);
            return;
        }
        String head = FULL.format(Instant.ofEpochMilli(e.time()).atZone(ZoneId.systemDefault())) + "  " + e.who()
                + "  (" + e.source() + ")" + (e.success() ? "" : "  refused");
        Skin.text(g, font, head, in.x(), in.y(), in.w(), e.success() ? Palette.TEXT : Palette.DANGER);
        int y = paragraph(g, e.action(), in, in.y() + 11, Palette.TEXT_DIM);
        if (!e.result().isEmpty()) paragraph(g, e.result(), in, y + 1, Palette.TEXT_MUTE);
    }
}
