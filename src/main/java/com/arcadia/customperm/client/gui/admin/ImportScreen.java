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
 * Moving a setup between LuckPerms and CustomPerm, both ways, in two steps each. First read, which changes
 * nothing and answers with the report; then apply, which carries out that report and nothing else.
 *
 * <p>Two steps rather than one confirmation dialog because the report is the point: it runs to a dozen
 * lines or more, says what is left out as much as what is carried, and that is what the admin is agreeing
 * to. A fixed-size dialog with one short message would hide exactly the part that matters.
 *
 * <p>The export tab adds a third condition: the admin says LuckPerms is backed up. An import writes files
 * this mod copies first; an export writes LuckPerms' storage, which only {@code /lp export} can copy.
 */
public final class ImportScreen extends AdminScreen {

    private static final int ROW = 12;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;
    private static final int INTRO = 34;

    private ImportData data;
    private final CpList<String> report;
    /** Which tab is shown: from LuckPerms, or to it. */
    private boolean toLuckPerms;
    /** Whether a translated {@code minecraft.command} node also exposes its command. */
    private boolean exposeCommands = true;
    /** Whether applying overwrites a grade, or clears a group's customperm nodes, instead of adding to it. */
    private boolean replace;
    /** The admin's word that LuckPerms is backed up, without which the export stays disabled. */
    private boolean backedUp;

    public ImportScreen(GuiContext context, ImportData data) {
        super(Component.literal("Import"), context);
        this.data = data;
        this.exposeCommands = data.previewed() ? data.exposeCommands() : true;
        // An export under way is what an admin opening this page wants to see.
        this.toLuckPerms = data.export().exporting() || (data.export().previewed() && !data.previewed());
        this.report = new CpList<String>(Component.literal("What it would do"), ROW)
                .renderer(this::renderLine)
                .label(line -> line)
                .identity(line -> line);
        showReport();
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
        showReport();
    }

    private void showReport() {
        if (!toLuckPerms) {
            report.emptyText("Read LuckPerms to see what an import would do.");
            report.setItems(data.report());
            return;
        }
        ImportData.Export export = data.export();
        report.emptyText("Read the grades to see what an export would write.");
        report.setItems(export.exporting()
                ? List.of("Exporting: " + export.done() + " of " + export.total() + " holder(s) written.")
                : export.report());
    }

    @Override
    protected Banner banner() {
        if (!data.running()) {
            return new Banner(Icon.WARN, "LuckPerms is installed but not running, so there is nothing to "
                    + (toLuckPerms ? "write to" : "read") + ". On a singleplayer or LAN world it never runs.",
                    Palette.WARN);
        }
        if (toLuckPerms) {
            if (data.export().exporting()) {
                return new Banner(Icon.CLOCK, "An export is running: " + data.export().done() + " of "
                        + data.export().total() + " written. This page follows it.", Palette.INFO);
            }
            if (!canExport()) {
                return new Banner(Icon.INFO, "Read-only: exporting needs " + GuiArea.GRADES.node() + " and "
                        + GuiArea.LUCKPERMS.node() + ".", Palette.INFO);
            }
            return new Banner(Icon.WARN, "An export writes into LuckPerms, which decides at once, and cannot be "
                    + "undone from here: run /lp export <file> first.", Palette.WARN);
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

    private boolean canExport() {
        return canEdit(GuiArea.GRADES) && canEdit(GuiArea.LUCKPERMS);
    }

    // ------------------------------------------------------------------ layout

    private Rect tabs() {
        return layout.content().top(BUTTON);
    }

    private Rect intro() {
        return layout.content().belowTop(BUTTON + GAP).top(INTRO);
    }

    private Rect options() {
        return layout.content().belowTop(BUTTON + GAP + INTRO + GAP).top(BUTTON);
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
        placeButtonRow(tabs(), 6, false, List.of(
                tab("From LuckPerms", false, "Bring LuckPerms groups and players over as grades."),
                tab("To LuckPerms", true, "Write the grades into LuckPerms, so they keep deciding once it is installed.")));
        if (toLuckPerms) {
            buildExport();
        } else {
            buildImport();
        }
        addRenderableWidget(report.at(reportArea()));
    }

    private CpButton tab(String label, boolean export, String tooltip) {
        return CpButton.ghost(Component.literal(label), () -> {
            if (toLuckPerms == export) return;
            toLuckPerms = export;
            // A replace chosen for one direction means something else in the other: never carried over.
            replace = false;
            showReport();
            rebuild();
        }).selected(toLuckPerms == export).tooltip(Component.literal(tooltip));
    }

    private void buildImport() {
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

        Rect bar = actionBar();
        CpButton read = CpButton.neutral(Component.literal(data.previewed() ? "Read again" : "Read LuckPerms"),
                () -> act(GuiAction.IMPORT_PREVIEW, String.valueOf(exposeCommands)))
                .icon(Icon.REFRESH).enabled(ready)
                .tooltip(Component.literal("Changes nothing: it answers with what an import would do."));
        addRenderableWidget(read.at(bar.left(read.preferredWidth(font, 8))));

        CpButton apply = CpButton.accent(Component.literal("Import"), this::confirmImport)
                .icon(Icon.CHECK)
                .enabled(ready && data.previewed())
                .tooltip(Component.literal(data.previewed()
                        ? "Applies what is listed above, after a backup of every config file."
                        : "Read LuckPerms first: what is imported is what the report says."));
        addRenderableWidget(apply.at(bar.right(apply.preferredWidth(font, 8))));
    }

    private void buildExport() {
        ImportData.Export export = data.export();
        boolean ready = data.running() && canExport() && !export.exporting();

        placeButtonRow(options(), 6, false, List.of(
                CpButton.ghost(Component.literal("LuckPerms is backed up"), () -> {
                    backedUp = !backedUp;
                    rebuild();
                }).icon(backedUp ? Icon.CHECK : Icon.CROSS).selected(backedUp).enabled(ready)
                        .tooltip(Component.literal("Run /lp export <file> first. Nothing here can undo an export, "
                                + "and one that fails part way leaves LuckPerms half written: /lp import <file> "
                                + "is the way back.")),
                CpButton.ghost(Component.literal(replace ? "Replace in groups" : "Add to groups"), () -> {
                    replace = !replace;
                    rebuild();
                }).icon(replace ? Icon.WARN : Icon.PLUS).selected(replace).enabled(ready)
                        .tooltip(Component.literal(replace
                                ? "A group or player written is first cleared of its customperm nodes and parents. "
                                        + "Prefix, suffix, meta and the nodes of other mods stay."
                                : "What LuckPerms already holds stays, weight included, and where it sets a node "
                                        + "the other way its value is kept."))));

        Rect bar = actionBar();
        CpButton read = CpButton.neutral(Component.literal(export.previewed() ? "Read again" : "Read the grades"),
                () -> act(GuiAction.EXPORT_PREVIEW))
                .icon(Icon.REFRESH).enabled(ready)
                .tooltip(Component.literal("Changes nothing: it answers with what an export would write."));
        addRenderableWidget(read.at(bar.left(read.preferredWidth(font, 8))));

        CpButton apply = CpButton.accent(Component.literal("Export"), this::confirmExport)
                .icon(Icon.CHECK)
                .enabled(ready && export.previewed() && backedUp)
                .tooltip(Component.literal(!export.previewed()
                        ? "Read the grades first: what is exported is what the report says."
                        : backedUp ? "Writes what is listed above into LuckPerms, in the background."
                                : "Say LuckPerms is backed up first."));
        addRenderableWidget(apply.at(bar.right(apply.preferredWidth(font, 8))));
    }

    private void confirmImport() {
        confirm("Import from LuckPerms",
                (replace ? "Grades of the same name are emptied first. " : "Grades of the same name keep what "
                        + "they hold. ") + "Every config file is copied to backup/ first.",
                replace ? "Replace and import" : "Import",
                () -> act(GuiAction.IMPORT_APPLY, replace ? "replace" : "merge"));
    }

    private void confirmExport() {
        confirm("Export to LuckPerms",
                (replace ? "Groups and players written lose their customperm nodes and parents first. "
                        : "What LuckPerms holds stays. ") + "LuckPerms decides at once, and only /lp import undoes it.",
                replace ? "Replace and export" : "Export",
                () -> act(GuiAction.EXPORT_APPLY, replace ? "replace" : "merge"));
    }

    // ------------------------------------------------------------------ rendering

    private void renderLine(GuiGraphics g, Font font, String line, Rect r, boolean hovered, boolean selected) {
        Skin.text(g, font, line, r.x() + 6, r.y() + (r.h() - 8) / 2, r.w() - 12, Palette.TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        paragraph(g, toLuckPerms
                ? "Writes the grades, their players and the default grade into LuckPerms as groups, users and "
                        + "nodes, as they are: nothing is translated. Reading changes nothing."
                : "Reads the groups, the players and their nodes from LuckPerms and writes them as grades. "
                        + "Reading changes nothing: it answers with what it would do, including what it would "
                        + "leave behind and why.",
                intro(), intro().y(), Palette.TEXT_MUTE);
    }
}
