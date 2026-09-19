/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.MetaLine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Meta tab of the Grades and Players pages: the holder's meta, and the boxes to set one. A mod that
 * declares a number or text permission node reads the meta named after it.
 */
final class MetaFields {

    static final int VALUE_FIELD = 110;
    static final int DURATION_FIELD = 64;
    static final int WORLD_FIELD = GradesScreen.WORLD_FIELD;

    final CpList<MetaLine> list;
    final CpEditBox key;
    final CpEditBox value;
    final CpEditBox duration;
    final CpEditBox world;

    MetaFields(Runnable onRebuild) {
        this.key = new CpEditBox(Component.literal("Meta key"), 128)
                .hint(Component.literal("key, e.g. mymod.homes.max"));
        this.value = new CpEditBox(Component.literal("Value"), 256)
                .hint(Component.literal("value"));
        this.duration = new CpEditBox(Component.literal("Duration"), 16)
                .hint(Component.literal("for, e.g. 30d"));
        this.world = new CpEditBox(Component.literal("World"), 128)
                .hint(Component.literal("in, e.g. the_nether"));
        this.list = new CpList<MetaLine>(Component.literal("Meta"), 14)
                .renderer(this::renderLine)
                .label(line -> line.key() + " = " + line.value())
                .identity(line -> line.key() + "@" + line.context())
                .emptyText("No meta: mods reading a number or text node get their default.")
                .onSelect(line -> {
                    key.setValue(line.key());
                    value.setValue(line.value());
                    world.setValue(com.arcadia.customperm.perm.Contexts.typed(line.context()));
                    onRebuild.run();
                });
    }

    /** Shows what the server holds; the boxes keep what the admin is typing. */
    void fill(List<MetaLine> lines) {
        list.setItems(lines == null ? List.of() : lines);
    }

    void setEditable(boolean editable) {
        key.setEditable(editable);
        value.setEditable(editable);
        duration.setEditable(editable);
        world.setEditable(editable);
    }

    void clearTyped() {
        value.setValue("");
        duration.setValue("");
    }

    /** The key box, then the value, world and duration boxes on the right of the row. */
    Rect keyRect(Rect row) {
        return boxes(row)[0];
    }

    Rect valueRect(Rect row) {
        return boxes(row)[1];
    }

    Rect worldRect(Rect row) {
        return boxes(row)[2];
    }

    Rect durationRect(Rect row) {
        return boxes(row)[3];
    }

    private static Rect[] boxes(Rect row) {
        return row.split(4, VALUE_FIELD, WORLD_FIELD, DURATION_FIELD);
    }

    private void renderLine(GuiGraphics g, Font font, MetaLine line, Rect r, boolean hovered, boolean selected) {
        String left = GradesScreen.label(line.context(), line.remaining());
        String value = "= " + line.value();
        int y = r.y() + (r.h() - 8) / 2;
        int x = r.x() + 6;
        int end = GradesScreen.trailing(g, font, left, r, x, font.width(line.key()) + 6 + font.width(value));
        int keyWidth = Math.min(font.width(line.key()), (end - x) / 2);
        Skin.text(g, font, line.key(), x, y, keyWidth, Palette.TEXT);
        x += keyWidth + 6;
        Skin.text(g, font, value, x, y, end - x, Palette.TEXT_MUTE);
    }
}
