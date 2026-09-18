/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.chat.LegacyText;
import com.arcadia.customperm.chat.NameDecoration;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.NameSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The prefix and suffix boxes of a Chat tab, shared by the Grades and Players pages, with the preview of
 * the line players would read. The preview is built by the same code as the server's name, so what it
 * shows is what chat will show.
 */
final class ChatFields {

    final CpEditBox prefix;
    final CpEditBox suffix;
    /** What the boxes were last filled with, so Save sends only what changed. */
    private String shownPrefix = "";
    private String shownSuffix = "";

    ChatFields(Runnable onSubmit) {
        this.prefix = new CpEditBox(Component.literal("Chat prefix"), LegacyText.MAX_LENGTH)
                .hint(Component.literal("prefix, e.g. &6[VIP] "))
                .onSubmit(onSubmit);
        this.suffix = new CpEditBox(Component.literal("Chat suffix"), LegacyText.MAX_LENGTH)
                .hint(Component.literal("suffix"))
                .onSubmit(onSubmit);
    }

    /** Shows what the server holds, unless the admin is typing in that box. */
    void fill(String prefixText, String suffixText) {
        shownPrefix = prefixText == null ? "" : prefixText;
        shownSuffix = suffixText == null ? "" : suffixText;
        if (!prefix.isFocused()) prefix.setValue(shownPrefix);
        if (!suffix.isFocused()) suffix.setValue(shownSuffix);
    }

    boolean prefixChanged() {
        return !prefix.getValue().equals(shownPrefix);
    }

    boolean suffixChanged() {
        return !suffix.getValue().equals(shownSuffix);
    }

    void setEditable(boolean editable) {
        prefix.setEditable(editable);
        suffix.setEditable(editable);
    }

    /** The two boxes side by side on one row. */
    Rect prefixRect(Rect row) {
        return row.left((row.w() - 4) / 2);
    }

    Rect suffixRect(Rect row) {
        return row.right(row.w() - (row.w() - 4) / 2 - 4);
    }

    /**
     * Draws what a chat line would look like with what is typed, and says plainly when names are not
     * decorated, since a preview of something nobody sees would pass for a setting that works.
     */
    void renderPreview(GuiGraphics g, Font font, Rect area, String name, NameSettings names) {
        int y = area.y() + 4;
        Skin.text(g, font, "Preview", area.x(), y, Palette.TEXT_MUTE);
        MutableComponent line = Component.literal("<")
                .append(NameDecoration.format(names.format(), prefix.getValue(), Component.literal(name),
                        suffix.getValue()))
                .append("> Hello!");
        g.drawString(font, line, area.x(), y + 14, Palette.TEXT, false);
        String problem = firstProblem();
        String note = problem != null ? problem
                : names.decorate() ? "Codes: &0-&f colours, &l bold, &o italic, &r reset, &#RRGGBB any colour."
                : "Names are not decorated yet: nobody sees this until it is turned on below, or with "
                        + "/customperm names on.";
        Skin.text(g, font, Skin.ellipsize(font, note, area.w()), area.x(), y + 30,
                problem != null || !names.decorate() ? Palette.WARN : Palette.TEXT_MUTE);
    }

    private String firstProblem() {
        String problem = LegacyText.problem(prefix.getValue());
        if (problem != null) return "Prefix: " + problem;
        problem = LegacyText.problem(suffix.getValue());
        return problem == null ? null : "Suffix: " + problem;
    }
}
