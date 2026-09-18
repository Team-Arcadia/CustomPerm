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
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.ImportData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Bringing a LuckPerms setup over, in two steps. First read, which changes nothing and answers with the
 * report; then import, which applies that report and nothing else.
 *
 * <p>Two steps rather than one confirmation dialog because the report is the point: it runs to a dozen
 * lines or more, says what is left behind as much as what is brought over, and that is what the admin is
 * agreeing to. A fixed-size dialog with one short message would hide exactly the part that matters.
 */
public final class ImportScreen extends AdminScreen {

    private static final int ROW = 12;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;

    private ImportData data;
    private final CpList<String> report;
    /** Whether a translated {@code minecraft.command} node also exposes its command. */
    private boolean exposeCommands = true;
    /** Whether applying overwrites a grade of the same name instead of adding to it. */
    private boolean replace;

    public ImportScreen(GuiContext context, ImportData data) {
        super(Component.literal("Import"), context);
        this.data = data;
        this.exposeCommands = data.previewed() ? data.exposeCommands() : true;
        this.report = new CpList<String>(Component.literal("What the import would do"), ROW)
                .renderer(this::renderLine)
                .label(line -> line)
                .identity(line -> line)
                .emptyText("Read LuckPerms to see what an import would do.");
        report.setItems(data.report());
    }

    @Override
    public GuiPage page() {
        return GuiPage.IMPORT;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.REFRESH;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (ImportData) newData;
        if (data.previewed()) exposeCommands = data.exposeCommands();
        report.setItems(data.report());
    }

    @Override
    protected Banner banner() {
        if (!data.running()) {
            return new Banner(Icon.WARN, "LuckPerms is installed but not running, so there is nothing to read. "
                    + "On a singleplayer or LAN world it never runs.", Palette.WARN);
        }
        if (!canImport()) {
            return new Banner(Icon.INFO, "Read-only: importing needs " + GuiArea.GRADES.node() + ", "
                    + GuiArea.COMMANDS.node() + " and " + GuiArea.LUCKPERMS.node() + ".", Palette.INFO);
        }
        return new Banner(Icon.INFO, "LuckPerms decides permissions while it is installed: what is imported "
                + "waits, readable on the Grades page, and takes over the day LuckPerms is removed.", Palette.INFO);
    }

    private boolean canImport() {
        return canEdit(GuiArea.GRADES) && canEdit(GuiArea.COMMANDS) && canEdit(GuiArea.LUCKPERMS);
    }

    // ------------------------------------------------------------------ layout

    private Rect intro() {
        return layout.content().top(44);
    }

    private Rect options() {
        return layout.content().belowTop(44 + GAP).top(Atlas.BUTTON_HEIGHT);
    }

    private Rect actionBar() {
        return layout.content().bottom(BUTTON);
    }

    private Rect reportArea() {
        Rect top = options();
        return new Rect(top.x(), top.bottom() + GAP, top.w(), actionBar().y() - GAP - top.bottom() - GAP);
    }

    @Override
    protected void buildPage() {
        boolean ready = data.running() && canImport();

        placeButtonRow(options(), 6, false, List.of(
                CpButton.ghost(Component.literal("Expose the commands"), () -> {
                    exposeCommands = !exposeCommands;
                    rebuild();
                }).icon(exposeCommands ? Icon.CHECK : Icon.CROSS).selected(exposeCommands).enabled(ready)
                        .tooltip(Component.literal("A node on a command that is not exposed grants nothing, so "
                                + "importing minecraft.command.x also exposes x. Turn off to import the nodes only.")),
                CpButton.ghost(Component.literal(replace ? "Replace grades" : "Add to grades"), () -> {
                    replace = !replace;
                    rebuild();
                }).icon(replace ? Icon.WARN : Icon.PLUS).selected(replace).enabled(ready)
                        .tooltip(Component.literal(replace
                                ? "A grade of the same name is emptied first, losing what it holds here."
                                : "A grade of the same name keeps what it holds and its weight, and the import "
                                        + "is added to it."))));

        addRenderableWidget(report.at(reportArea()));

        Rect bar = actionBar();
        CpButton read = CpButton.neutral(Component.literal(data.previewed() ? "Read again" : "Read LuckPerms"),
                () -> act(GuiAction.IMPORT_PREVIEW, String.valueOf(exposeCommands)))
                .icon(Icon.REFRESH).enabled(ready)
                .tooltip(Component.literal("Changes nothing: it answers with what an import would do."));
        int readWidth = read.preferredWidth(font, 8);
        addRenderableWidget(read.at(bar.left(readWidth)));

        CpButton apply = CpButton.accent(Component.literal("Import"), this::confirmImport)
                .icon(Icon.CHECK)
                .enabled(ready && data.previewed())
                .tooltip(Component.literal(data.previewed()
                        ? "Applies what is listed above, after a backup of every config file."
                        : "Read LuckPerms first: what is imported is what the report says."));
        addRenderableWidget(apply.at(bar.right(apply.preferredWidth(font, 8))));
    }

    private void confirmImport() {
        confirm("Import from LuckPerms",
                (replace ? "Grades of the same name are emptied first. " : "Grades of the same name keep what "
                        + "they hold. ") + "Every config file is copied to backup/ first.",
                replace ? "Replace and import" : "Import",
                () -> act(GuiAction.IMPORT_APPLY, replace ? "replace" : "merge"));
    }

    // ------------------------------------------------------------------ rendering

    private void renderLine(GuiGraphics g, Font font, String line, Rect r, boolean hovered, boolean selected) {
        Skin.text(g, font, line, r.x() + 6, r.y() + (r.h() - 8) / 2, r.w() - 12, Palette.TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        paragraph(g, "Reads the groups, the players and their nodes from LuckPerms and writes them as grades. "
                + "Reading changes nothing: it answers with what it would do, including what it would leave "
                + "behind and why, and only then can it be applied.", intro(), intro().y(), Palette.TEXT_MUTE);
    }
}
