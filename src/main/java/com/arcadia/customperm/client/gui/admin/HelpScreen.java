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
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What every feature does and how to use it, one topic at a time: topics on the left, searchable (Ctrl+F looks in
 * their text too), the topic on the right, wrapped to the panel and scrolled like a list.
 */
public final class HelpScreen extends AdminScreen {

    private static final int ROW = 16;
    private static final int LINE = 10;
    private static final int GAP = 6;

    /** One wrapped line of a topic, and how to draw it. */
    private record Line(Kind kind, String text) {}

    private enum Kind { TITLE, HEADING, TEXT, TEXT_INDENTED, POINT, COMMAND, BLANK }

    private final CpEditBox search;
    private final CpList<HelpTopics.Topic> topics;
    private final CpList<Line> body;
    private int wrappedFor = -1;
    private HelpTopics.Topic wrappedTopic;

    public HelpScreen(GuiContext context) {
        super(Component.literal("Help"), context);
        this.search = new CpEditBox(Component.literal("Search help"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .completes(Completions.search(() -> HelpTopics.ALL.stream().map(HelpTopics.Topic::title).toList()))
                .onChange(text -> refilter());
        this.topics = new CpList<HelpTopics.Topic>(Component.literal("Help topics"), ROW)
                .renderer((g, font, topic, r, hovered, selected) ->
                        Skin.textIn(g, font, topic.title(), r, 6, selected || hovered ? Palette.TEXT : Palette.TEXT_DIM))
                .label(HelpTopics.Topic::title)
                .identity(HelpTopics.Topic::title)
                .emptyText("No topic mentions that.")
                .onSelect(topic -> {
                    wrappedFor = -1;
                    // The first topic is selected from the constructor, before the screen has a font to wrap with.
                    if (minecraft != null) rebuild();
                });
        this.body = new CpList<Line>(Component.literal("Help text"), LINE)
                .renderer(this::renderLine)
                .label(Line::text)
                .emptyText("Select a topic.");
        refilter();
    }

    @Override
    public GuiPage page() {
        return GuiPage.HELP;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.INFO;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        // Nothing comes from the server: the text ships with the client.
    }

    private Rect left() {
        Rect c = layout.content();
        return c.left(c.w() * 34 / 100);
    }

    private Rect right() {
        Rect c = layout.content();
        return c.afterLeft(left().w() + GAP);
    }

    @Override
    protected void buildPage() {
        Rect left = left();
        addRenderableWidget(search.at(left.top(Atlas.INPUT_HEIGHT)));
        addRenderableWidget(topics.at(left.belowTop(Atlas.INPUT_HEIGHT + 4)));
        // Selected before the list had a height, the first topic was scrolled past; bring it into view now.
        HelpTopics.Topic current = topics.getSelected();
        if (current != null && topics.selectedIndex() == 0) topics.selectByKey(current.title());
        Rect text = right();
        // Wrapped again only when the topic or the width changed, not on every rebuild.
        int width = text.w() - 16;
        HelpTopics.Topic topic = topics.getSelected();
        if (width != wrappedFor || topic != wrappedTopic) {
            wrappedFor = width;
            wrappedTopic = topic;
            body.setItems(topic == null ? List.of() : wrap(topic, width));
        }
        addRenderableWidget(body.at(text));
    }

    /** Topics whose title or text holds what is typed; the first one shown when none is selected. */
    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        // Topics named after the search first, then those that only mention it.
        List<HelpTopics.Topic> shown = new ArrayList<>();
        List<HelpTopics.Topic> mentioned = new ArrayList<>();
        for (HelpTopics.Topic topic : HelpTopics.ALL) {
            if (query.isEmpty() || topic.title().toLowerCase(Locale.ROOT).contains(query)) {
                shown.add(topic);
            } else if (matches(topic, query)) {
                mentioned.add(topic);
            }
        }
        shown.addAll(mentioned);
        topics.setItems(shown);
        if (topics.getSelected() == null && !shown.isEmpty()) topics.selectByKey(shown.get(0).title());
        wrappedFor = -1;
        if (minecraft != null) rebuild();
    }

    private static boolean matches(HelpTopics.Topic topic, String query) {
        if (topic.title().toLowerCase(Locale.ROOT).contains(query)) return true;
        for (String line : topic.lines()) {
            if (line.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    private List<Line> wrap(HelpTopics.Topic topic, int width) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Kind.TITLE, topic.title()));
        lines.add(new Line(Kind.BLANK, ""));
        Kind previous = Kind.TEXT;
        for (String raw : topic.lines()) {
            Kind kind = raw.startsWith("# ") ? Kind.HEADING : raw.startsWith("- ") ? Kind.POINT
                    : raw.startsWith("/") ? Kind.COMMAND : Kind.TEXT;
            String text = kind == Kind.HEADING || kind == Kind.POINT ? raw.substring(2) : raw;
            // Upper case is wider: wrapped as it will be drawn.
            if (kind == Kind.HEADING) text = text.toUpperCase(Locale.ROOT);
            // A blank line between paragraphs and before a heading; commands and points follow each other closely.
            boolean together = kind == previous && (kind == Kind.COMMAND || kind == Kind.POINT);
            if (!together && lines.size() > 2) lines.add(new Line(Kind.BLANK, ""));
            int indent = kind == Kind.POINT || kind == Kind.COMMAND ? 10 : 0;
            boolean first = true;
            for (FormattedText part : font.getSplitter().splitLines(text, Math.max(40, width - indent), Style.EMPTY)) {
                lines.add(new Line(first ? kind : continuation(kind), part.getString()));
                first = false;
            }
            previous = kind;
        }
        return lines;
    }

    /** A wrapped line keeps its paragraph's colour and indent, without the bullet again. */
    private static Kind continuation(Kind kind) {
        return kind == Kind.POINT ? Kind.TEXT_INDENTED : kind;
    }

    private void renderLine(GuiGraphics g, Font font, Line line, Rect r, boolean hovered, boolean selected) {
        int y = r.y() + (r.h() - 8) / 2;
        int x = r.x() + 4;
        switch (line.kind()) {
            case TITLE -> Skin.text(g, font, line.text(), x, y, r.w() - 8, Palette.TEXT);
            case HEADING -> Skin.text(g, font, line.text(), x, y, r.w() - 8, Palette.TEXT_MUTE);
            case TEXT -> Skin.text(g, font, line.text(), x, y, r.w() - 8, Palette.TEXT_DIM);
            case TEXT_INDENTED -> Skin.text(g, font, line.text(), x + 10, y, r.w() - 18, Palette.TEXT_DIM);
            case POINT -> {
                Skin.dot(g, x + 2, r.centerY(), Palette.ACCENT);
                Skin.text(g, font, line.text(), x + 10, y, r.w() - 18, Palette.TEXT_DIM);
            }
            case COMMAND -> Skin.text(g, font, line.text(), x + 10, y, r.w() - 18, Palette.ACCENT_HI);
            case BLANK -> { }
        }
    }
}
