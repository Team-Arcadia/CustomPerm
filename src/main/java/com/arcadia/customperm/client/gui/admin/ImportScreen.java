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
import com.arcadia.customperm.network.gui.ImportData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Moving a setup between LuckPerms and CustomPerm, both ways, in two steps each. First read, which changes
 * nothing and answers with the report; then apply, which carries out that report and nothing else.
 *
 * <p>Two steps rather than one confirmation dialog because the report is the point: it runs to a dozen
 * lines or more, says what is left out as much as what is carried, and that is what the admin is agreeing
 * to. A fixed-size dialog with one short message would hide exactly the part that matters.
 *
 * <p>Choose switches the report for what the next import or export carries: which groups or grades, players
 * and tracks, and which kinds of entries. The server keeps that selection and answers with the report it gives,
 * so the report stays the thing the admin agrees to.
 *
 * <p>The export tab adds a third condition: the admin says LuckPerms is backed up. An import writes files
 * this mod copies first; an export writes LuckPerms' storage, which only {@code /lp export} can copy.
 */
public final class ImportScreen extends AdminScreen {

    private static final int ROW = 12;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;

    private ImportData data;
    private final CpList<String> report;
    /** The report as the server wrote it, one sentence a line, before wrapping to the list width. */
    private List<String> reportLines = List.of();
    /** Width the report lines were last wrapped to. */
    private int reportWidth = Integer.MAX_VALUE;
    /** Which tab is shown: from LuckPerms, or to it. */
    private boolean toLuckPerms;
    /** Whether applying overwrites a grade, or clears a group's customperm nodes, instead of adding to it. */
    private boolean replace;
    /** The admin's word that LuckPerms is backed up, without which the export stays disabled. */
    private boolean backedUp;
    /** Whether the report area shows what is carried, to choose it, instead of the report. */
    private boolean choosing;

    /** The part of the selection shown while choosing. */
    private enum Part { GROUPS, PLAYERS, TRACKS, KINDS }

    private Part part = Part.GROUPS;
    private final CpList<ImportData.Item> choices;
    private final CpEditBox choiceSearch;

    public ImportScreen(GuiContext context, ImportData data) {
        super(Component.literal("Import"), context);
        this.data = data;
        // An export under way is what an admin opening this page wants to see.
        this.toLuckPerms = data.export().exporting() || (data.export().previewed() && !data.previewed());
        this.report = new CpList<String>(Component.literal("What it would do"), ROW)
                .renderer(this::renderLine)
                .label(line -> line)
                .identity(line -> line);
        this.choiceSearch = new CpEditBox(Component.literal("Search"), 64)
                .hint(Component.literal("Search"))
                .completes(Completions.search(() -> choice(part).stream().map(ImportData.Item::label).toList()))
                .onChange(text -> fillChoices());
        this.choices = new CpList<ImportData.Item>(Component.literal("What is carried"), 14)
                .renderer(this::renderChoice)
                .label(item -> item.label() + (item.on() ? ", carried" : ", left out"))
                .identity(ImportData.Item::key)
                // One click ticks or unticks, like a checkbox; the click still selects the row.
                .onRowClick((item, row, mouseX, mouseY) -> {
                    toggle(item);
                    return false;
                });
        showReport();
        fillChoices();
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
        showReport();
        fillChoices();
    }

    // ------------------------------------------------------------------ choosing

    private ImportData.Choice choice() {
        return toLuckPerms ? data.export().choice() : data.choice();
    }

    private List<ImportData.Item> choice(Part shown) {
        return switch (shown) {
            case GROUPS -> choice().groups();
            case PLAYERS -> choice().players();
            case TRACKS -> choice().tracks();
            case KINDS -> kindItems();
        };
    }

    /** The kinds of entries as rows to tick, each saying what it covers; exposing commands is an import's only. */
    private List<ImportData.Item> kindItems() {
        List<String> taken = choice().kinds();
        List<ImportData.Item> items = new ArrayList<>(List.of(
                new ImportData.Item("nodes", "Nodes: allowed and denied", taken.contains("nodes")),
                new ImportData.Item("parents", toLuckPerms ? "Parents: a grade's parents, a player's grades"
                        : "Parents: a group's parents, a player's groups", taken.contains("parents")),
                new ImportData.Item("chat", "Prefixes and suffixes", taken.contains("chat")),
                new ImportData.Item("meta", "Meta values", taken.contains("meta")),
                new ImportData.Item("contexts", "Contexts: entries limited to a world, a mode, a server",
                        taken.contains("contexts")),
                new ImportData.Item("temporary", "Temporary: entries with an expiry", taken.contains("temporary"))));
        if (!toLuckPerms) {
            items.add(new ImportData.Item("commands", "Expose the commands the nodes need", taken.contains("commands")));
        }
        return items;
    }

    private void fillChoices() {
        String query = choiceSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        choices.setItems(choice(part).stream()
                .filter(item -> query.isEmpty() || item.label().toLowerCase(java.util.Locale.ROOT).contains(query))
                .toList());
        boolean read = part == Part.KINDS || (toLuckPerms ? data.export().previewed() : data.previewed());
        choices.emptyText(!read ? (toLuckPerms ? "Read the grades first: the choice is among what they hold."
                : "Read LuckPerms first: the choice is among what it holds.")
                : query.isEmpty() ? "Nothing of this kind was read." : "Nothing matches.");
    }

    private GuiAction selectAction() {
        return toLuckPerms ? GuiAction.EXPORT_SELECT : GuiAction.IMPORT_SELECT;
    }

    private String partWord() {
        return part.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** One click carries a row, or leaves it out; the server answers with the report the selection gives. */
    private void toggle(ImportData.Item item) {
        if (item == null || !canChoose()) return;
        act(selectAction(), partWord(), item.on() ? "remove" : "add", item.key());
    }

    private boolean canChoose() {
        return data.running() && (toLuckPerms ? canExport() && !data.export().exporting() : canImport());
    }

    private void buildChoosing(Rect area) {
        List<CpButton> parts = new ArrayList<>();
        for (Part shown : Part.values()) {
            String label = switch (shown) {
                case GROUPS -> toLuckPerms ? "Grades" : "Groups";
                case PLAYERS -> "Players";
                case TRACKS -> "Tracks";
                case KINDS -> "Kinds";
            };
            parts.add(CpButton.ghost(Component.literal(label), () -> {
                part = shown;
                choiceSearch.setValue("");
                fillChoices();
                rebuild();
            }).selected(part == shown));
        }
        boolean editable = canChoose();
        // On the tabs' row: a small window leaves the list only a few lines, which a row of its own would take.
        parts.add(CpButton.neutral(Component.literal("All"), () -> act(selectAction(), partWord(), "set", "all"))
                .icon(Icon.CHECK).enabled(editable));
        parts.add(CpButton.neutral(Component.literal("None"), () -> act(selectAction(), partWord(), "set", "none"))
                .icon(Icon.CROSS).enabled(editable));
        placeButtonRow(area.top(BUTTON), 6, false, parts);
        Rect list = area.belowTop(BUTTON + 4);
        if (part == Part.PLAYERS) {
            addRenderableWidget(choiceSearch.at(list.top(Atlas.INPUT_HEIGHT)));
            list = list.belowTop(Atlas.INPUT_HEIGHT + 4);
        }
        addRenderableWidget(choices.at(list));
    }

    private void renderChoice(GuiGraphics g, Font font, ImportData.Item item, Rect r, boolean hovered, boolean selected) {
        Skin.icon(g, item.on() ? Icon.CHECK : Icon.CROSS, r.x() + 4, r.y() + (r.h() - Atlas.ICON_SIZE) / 2,
                item.on() ? Palette.GOOD : Palette.TEXT_MUTE);
        Skin.text(g, font, item.label(), r.x() + Atlas.ICON_SIZE + 8, r.y() + (r.h() - 8) / 2, r.w() - Atlas.ICON_SIZE - 12,
                item.on() ? Palette.TEXT : Palette.TEXT_MUTE);
    }

    private void showReport() {
        if (!toLuckPerms) {
            report.emptyText("Read LuckPerms to see what an import would do.");
            reportLines = data.report();
        } else {
            ImportData.Export export = data.export();
            report.emptyText("Read the grades to see what an export would write.");
            reportLines = export.exporting()
                    ? List.of("Exporting: " + export.done() + " of " + export.total() + " holder(s) written.")
                    : export.report();
        }
        fillReport();
    }

    /**
     * Wraps each report sentence to the list width, continuation lines indented: cut at the edge, a line
     * such as "3 command(s) also exposed, without which those nodes w..." loses what it was saying.
     */
    private void fillReport() {
        // Before the first layout there is no font yet; buildPage wraps once the width is known.
        if (font == null) {
            report.setItems(reportLines);
            return;
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : reportLines) {
            boolean first = true;
            for (FormattedText part : font.getSplitter().splitLines(line, reportWidth, Style.EMPTY)) {
                wrapped.add(first ? part.getString() : "  " + part.getString());
                first = false;
            }
        }
        report.setItems(wrapped);
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

    /**
     * As tall as its text wraps to, so no sentence is cut on a narrow panel; shown only until something is read,
     * the report then saying more, and never while choosing, which needs the room.
     */
    private Rect intro() {
        Rect below = layout.content().belowTop(BUTTON + GAP);
        if (!showsIntro()) return below.top(0);
        int lines = font.split(Component.literal(introText()), below.w()).size();
        return below.top(Math.max(1, lines) * 10);
    }

    private boolean showsIntro() {
        boolean read = toLuckPerms ? data.export().previewed() || data.export().exporting() : data.previewed();
        return !read && !choosing;
    }

    private Rect options() {
        Rect intro = intro();
        return new Rect(intro.x(), intro.bottom() + (intro.h() == 0 ? 0 : GAP), intro.w(), BUTTON);
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
        Rect area = reportArea();
        if (choosing) {
            buildChoosing(area);
            return;
        }
        // The text inset of a row on each side, and the scrollbar.
        int width = Math.max(40, area.w() - 12 - 6);
        if (width != reportWidth) {
            reportWidth = width;
            fillReport();
        }
        addRenderableWidget(report.at(area));
    }

    private CpButton tab(String label, boolean export, String tooltip) {
        return CpButton.ghost(Component.literal(label), () -> {
            if (toLuckPerms == export) return;
            toLuckPerms = export;
            // A replace chosen for one direction means something else in the other: never carried over.
            replace = false;
            showReport();
            fillChoices();
            rebuild();
        }).selected(toLuckPerms == export).tooltip(Component.literal(tooltip));
    }

    private void buildImport() {
        boolean ready = data.running() && canImport();

        placeButtonRow(options(), 6, false, List.of(
                chooseButton(),
                CpButton.ghost(Component.literal(replace ? "Replace grades" : "Add to grades"), () -> {
                    replace = !replace;
                    rebuild();
                }).icon(replace ? Icon.WARN : Icon.PLUS).selected(replace).enabled(ready)
                        .tooltip(Component.literal(replace
                                ? "A grade of the same name is first emptied of the kinds carried, losing what it held of them here."
                                : "A grade of the same name keeps what it holds and its weight, and the import "
                                        + "is added to it."))));

        Rect bar = actionBar();
        CpButton read = CpButton.neutral(Component.literal(data.previewed() ? "Read again" : "Read LuckPerms"),
                // Always read with the commands: whether they are exposed is the selection's commands kind.
                () -> act(GuiAction.IMPORT_PREVIEW, "true"))
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
                chooseButton(),
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
                                ? "A group or player written is first cleared of its customperm nodes and parents, "
                                        + "of the kinds carried. Prefix, suffix, meta and the nodes of other mods stay."
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

    /** Switches the report area to choosing what is carried, and back. */
    private CpButton chooseButton() {
        return CpButton.ghost(Component.literal("Choose"), () -> {
            choosing = !choosing;
            fillChoices();
            rebuild();
        }).icon(Icon.EDIT).selected(choosing)
                .tooltip(Component.literal(choosing ? "Back to the report of what the selection carries."
                        : "Choose the groups, players, tracks and kinds of entries carried."));
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
        if (showsIntro()) paragraph(g, introText(), intro(), intro().y(), Palette.TEXT_MUTE);
    }

    private String introText() {
        return toLuckPerms
                ? "Writes the grades, their players and the default grade into LuckPerms as groups, users and "
                        + "nodes, as they are: nothing is translated. Reading changes nothing."
                : "Reads the groups, the players and their nodes from LuckPerms and writes them as grades. "
                        + "Reading changes nothing: it answers with what it would do, including what it would "
                        + "leave behind and why.";
    }
}
