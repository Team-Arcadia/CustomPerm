/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.chat.ChatStack;
import com.arcadia.customperm.chat.LegacyText;
import com.arcadia.customperm.chat.NameDecoration;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.ChatLine;
import com.arcadia.customperm.network.gui.NameSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * The Chat tab of the Grades and Players pages: the holder's prefixes and suffixes, the boxes to add one,
 * and a preview of the line players would read. The preview is built by the same code as the server's name,
 * {@link NameDecoration#format} and {@link ChatStack}, so what it shows is what chat will show.
 */
final class ChatFields {

    /** Height kept under the list for the preview. */
    static final int PREVIEW = 46;
    static final int PRIORITY_FIELD = 44;
    static final int DURATION_FIELD = 64;
    static final int WORLD_FIELD = GradesScreen.WORLD_FIELD;

    final CpList<ChatLine> list;
    final CpEditBox text;
    final CpEditBox priority;
    final CpEditBox duration;
    final CpEditBox world;

    ChatFields(Runnable onRebuild) {
        this.text = new CpEditBox(Component.literal("Prefix or suffix"), LegacyText.MAX_LENGTH)
                .hint(Component.literal("text, e.g. &6[VIP] "));
        this.priority = new CpEditBox(Component.literal("Priority"), 11)
                .hint(Component.literal("priority"));
        this.duration = new CpEditBox(Component.literal("Duration"), 16)
                .hint(Component.literal("for, e.g. 30d"));
        this.world = new CpEditBox(Component.literal("World"), 128)
                .hint(Component.literal("in, e.g. the_nether"));
        this.list = new CpList<ChatLine>(Component.literal("Prefixes and suffixes"), 14)
                .renderer(this::renderLine)
                .label(line -> (line.suffix() ? "suffix " : "prefix ") + line.text() + ", priority " + line.priority())
                .identity(line -> (line.suffix() ? "suffix:" : "prefix:") + line.priority() + "@" + line.context())
                .emptyText("No prefix or suffix: names show plain.")
                .onSelect(line -> {
                    text.setValue(line.text());
                    priority.setValue(String.valueOf(line.priority()));
                    world.setValue(com.arcadia.customperm.perm.Contexts.typed(line.context()));
                    onRebuild.run();
                });
    }

    /** Shows what the server holds; the boxes keep what the admin is typing. */
    void fill(List<ChatLine> lines) {
        list.setItems(lines == null ? List.of() : lines);
    }

    void setEditable(boolean editable) {
        text.setEditable(editable);
        priority.setEditable(editable);
        duration.setEditable(editable);
        world.setEditable(editable);
    }

    /** The typed priority, 0 when the box is empty, {@code null} when it is not a whole number. */
    Integer typedPriority() {
        String typed = priority.getValue().trim();
        if (typed.isEmpty()) return 0;
        try {
            return Integer.parseInt(typed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void clearTyped() {
        text.setValue("");
        duration.setValue("");
    }

    /** Height of the preview's first line, which carries the name settings beside its label. */
    static final int PREVIEW_HEADER = 14;

    /** The list, above the preview. */
    Rect listRect(Rect area) {
        return area.aboveBottom(previewRect(area).h());
    }

    /** The preview under the list: whole when there is room, else what leaves the list one row, header first. */
    Rect previewRect(Rect area) {
        return area.bottom(Math.max(Math.min(PREVIEW, area.h() - 14), Math.min(area.h(), PREVIEW_HEADER + 2)));
    }

    /** Where the name setting buttons go: the preview's first line, after its label. */
    Rect settingsRow(Rect area, Font font) {
        Rect preview = previewRect(area);
        int labelW = font.width("Preview") + 6;
        return new Rect(preview.x() + labelW, preview.y() + 1, preview.w() - labelW, PREVIEW_HEADER - 2);
    }

    /** The text box, then the priority, world and duration boxes on the right of the row. */
    Rect textRect(Rect row) {
        return boxes(row)[0];
    }

    Rect priorityRect(Rect row) {
        return boxes(row)[1];
    }

    Rect worldRect(Rect row) {
        return boxes(row)[2];
    }

    Rect durationRect(Rect row) {
        return boxes(row)[3];
    }

    private static Rect[] boxes(Rect row) {
        return row.split(4, PRIORITY_FIELD, WORLD_FIELD, DURATION_FIELD);
    }

    private void renderLine(GuiGraphics g, Font font, ChatLine line, Rect r, boolean hovered, boolean selected) {
        String label = line.suffix() ? "SUFFIX" : "PREFIX";
        int w = Skin.badge(g, font, label, r.x() + 4, r.centerY(), line.suffix() ? Palette.INFO : Palette.ACCENT);
        int x = r.x() + 4 + Math.max(w, font.width("PREFIX") + 6) + 5;
        String where = String.valueOf(line.priority());
        Skin.text(g, font, where, x, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        x += Math.max(font.width(where), font.width("000")) + 8;
        String left = GradesScreen.label(line.context(), line.remaining());
        Component shownText = LegacyText.parse(line.text());
        int end = GradesScreen.trailing(g, font, left, r, x, font.width(shownText));
        // Drawn with its codes applied, as players will see it; the box above shows the raw text.
        g.enableScissor(x, r.y(), end, r.bottom());
        g.drawString(font, shownText, x, r.y() + (r.h() - 8) / 2, Palette.TEXT, false);
        g.disableScissor();
    }

    /**
     * Draws what a chat line would look like with what this holder carries, stacked as the settings say, and
     * says plainly when names are not decorated, since a preview of something nobody sees would pass for a
     * setting that works. Only this holder's entries show: a player's grades add theirs on the server.
     */
    void renderPreview(GuiGraphics g, Font font, Rect listArea, String name, NameSettings names) {
        Rect area = previewRect(listArea);
        int y = area.y() + 3;
        Skin.text(g, font, "Preview", area.x(), y, Palette.TEXT_MUTE);
        // Lines that do not fit are left out rather than drawn over the boxes under the list.
        if (area.h() < 26) return;
        List<ChatLine> lines = list.items();
        String prefix = ChatStack.format(ChatLine.texts(lines, false), names.prefix().settings());
        String suffix = ChatStack.format(ChatLine.texts(lines, true), names.suffix().settings());
        MutableComponent line = Component.literal("<")
                .append(NameDecoration.format(names.format(), prefix, Component.literal(name), suffix))
                .append("> Hello!");
        g.drawString(font, line, area.x(), y + 15, Palette.TEXT, false);
        if (area.h() < 38) return;
        String problem = LegacyText.problem(text.getValue());
        String note = problem != null ? "Text: " + problem
                : typedPriority() == null ? "A priority is a whole number: the highest shows first."
                : names.decorate() ? "Codes: &0-&f colours, &l bold, &o italic, &r reset, &#RRGGBB any colour."
                : "Names are not decorated yet: nobody sees this until Names is on, on the Grades page's "
                        + "Chat tab, or /customperm names on.";
        Skin.text(g, font, Skin.ellipsize(font, note, area.w()), area.x(), y + 28,
                problem != null || typedPriority() == null || !names.decorate() ? Palette.WARN : Palette.TEXT_MUTE);
    }
}
