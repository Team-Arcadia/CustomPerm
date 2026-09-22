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
import com.arcadia.customperm.network.gui.ClusterView;
import com.arcadia.customperm.network.gui.CommandsData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Browser of the server's root commands: search, show only exposed ones, expose or hide a command,
 * and choose whether an exposed command keeps its original requirement. Every root the dispatcher
 * knows is listed, so exposing a modded command needs no typing of its exact name. The Who view of an
 * exposed command lists the grades and players whose entries name its node, and sets theirs everywhere and
 * server by server.
 */
public final class CommandsScreen extends AdminScreen {

    /** Title line above the server toggles. */
    private static final int SERVERS_TITLE = 12;
    /** Whether the details panel shows the servers of the selected command; kept while moving between commands. */
    private boolean serversView;
    /** Whether the details panel shows who may use the selected command where; never with the servers view. */
    private boolean whoView;
    /** Whether the name typed in the Who view is a player's rather than a grade's. */
    private boolean whoPlayer;
    /** A grade or a player picked in the Who view before they have any entry, kept until the page says otherwise. */
    private CommandsData.Holder pendingHolder;
    /** The command's name and status at the top of the details panel. */
    private static final int HEADER = 28;

    private static final int ROW = 16;
    private static final int GAP = 6;
    private static final int TOOLBAR = 16;

    private CommandsData data;

    // Survive rebuilds: the admin's search, filter, selection and scroll position.
    private final CpEditBox search;
    private final CpList<CommandsData.Row> list;
    private final CpList<CommandsData.Holder> holderList;
    private final CpEditBox whoName;
    private boolean exposedOnly;
    private int filtersRight;
    /** Left edge of the right-aligned toolbar controls: the exposed count is drawn before it. */
    private int toolbarRight;

    public CommandsScreen(GuiContext context, CommandsData data) {
        super(Component.literal("Exposed commands"), context);
        this.data = data;
        this.search = new CpEditBox(Component.literal("Search commands"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .completes(Completions.search(() -> this.data.rows().stream().map(CommandsData.Row::name).toList()))
                .onChange(text -> refilter());
        this.list = new CpList<CommandsData.Row>(Component.literal("Commands"), ROW)
                .renderer(this::renderRow)
                .label(row -> "/" + row.name() + ", " + status(row))
                .identity(CommandsData.Row::name)
                .onSelect(row -> rebuild())
                .onActivate(this::primaryAction);
        this.holderList = new CpList<CommandsData.Holder>(Component.literal("Who decides"), ROW)
                .renderer(this::renderHolder)
                .label(holder -> (holder.player() ? "player " : "grade ") + holder.label() + ", " + holderSummary(holder))
                .identity(CommandsScreen::holderKey)
                .emptyText("No grade or player names it yet.")
                .onSelect(holder -> rebuild());
        this.whoName = new CpEditBox(Component.literal("Grade or player"), 64)
                .completes(text -> (whoPlayer ? Completions.players() : Completions.grades()).propose(text))
                .onSubmit(this::pickHolder);
        refilter();
    }

    private static String holderKey(CommandsData.Holder holder) {
        return (holder.player() ? "p:" : "g:") + holder.id();
    }

    @Override
    public GuiPage page() {
        return GuiPage.COMMANDS;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.COMMAND;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (CommandsData) newData;
        refilter();
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<CommandsData.Row> rows = data.rows().stream()
                .filter(row -> !exposedOnly || row.exposed())
                .filter(row -> query.isEmpty() || row.name().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        CommandsData.Row before = list.getSelected();
        list.setItems(rows);
        list.emptyText(exposedOnly && query.isEmpty() ? "No command is exposed yet." : "No command matches.");
        refreshHolders();
        // The detail buttons act on the selected row: rebuild them when filtering changed it.
        if (layout != null && !java.util.Objects.equals(before, list.getSelected())) rebuild();
    }

    // ------------------------------------------------------------------ layout

    private Rect toolbar() {
        return layout.content().top(TOOLBAR);
    }

    private Rect body() {
        return layout.content().belowTop(TOOLBAR + GAP);
    }

    private Rect listArea() {
        Rect body = body();
        return body.left(body.w() * 55 / 100);
    }

    private Rect details() {
        return body().afterLeft(listArea().w() + GAP);
    }

    @Override
    protected void buildPage() {
        Rect bar = toolbar();
        CpButton all = CpButton.ghost(Component.literal("All"), () -> setFilter(false)).selected(!exposedOnly);
        int allW = all.preferredWidth(font, 8);
        CpButton exposed = CpButton.ghost(Component.literal("Exposed"), () -> setFilter(true)).selected(exposedOnly);
        int exposedW = exposed.preferredWidth(font, 8);

        // LuckPerms already checks every command: no switch that would do nothing.
        toolbarRight = bar.right();
        if (!context.luckPermsInstalled()) {
            CpButton gate = CpButton.neutral(Component.literal(data.gateAll() ? "All gated" : "Gate all"), this::toggleGateAll)
                    .icon(data.gateAll() ? Icon.LOCK : Icon.SHIELD)
                    .selected(data.gateAll())
                    .enabled(canEdit(GuiArea.COMMANDS))
                    .tooltip(Component.literal(data.gateAll()
                            ? "Every command follows its customperm.command.<name> node. Click to go back to exposed commands only."
                            : "Only exposed commands follow their node. Click to make every command follow it, so a denied * can restrict operators."));
            int gateW = gate.preferredWidth(font, 6);
            toolbarRight = bar.right() - gateW;
            addRenderableWidget(gate.at(toolbarRight, bar.y(), gateW, bar.h()));
        }

        // The search field takes what the buttons leave, up to its usual width.
        int searchW = Math.max(60, Math.min(180, toolbarRight - bar.x() - allW - 2 - exposedW - 2 * GAP));
        addRenderableWidget(search.at(bar.left(searchW)));
        int x = bar.x() + searchW + GAP;
        addRenderableWidget(all.at(x, bar.y(), allW, bar.h()));
        x += allW + 2;
        addRenderableWidget(exposed.at(x, bar.y(), exposedW, bar.h()));
        filtersRight = x + exposedW;

        addRenderableWidget(list.at(listArea()));
        buildDetailButtons();
    }

    private void setFilter(boolean onlyExposed) {
        this.exposedOnly = onlyExposed;
        refilter();
        rebuild();
    }

    /** The holders of the selected command, with a grade or player just picked and not yet in them. */
    private void refreshHolders() {
        CommandsData.Row row = list.getSelected();
        List<CommandsData.Holder> holders = new java.util.ArrayList<>(row == null ? List.of() : row.holders());
        if (pendingHolder != null) {
            if (holders.stream().anyMatch(h -> holderKey(h).equals(holderKey(pendingHolder))
                    || h.player() && pendingHolder.player() && h.label().equalsIgnoreCase(pendingHolder.label()))) {
                pendingHolder = null;
            } else {
                holders.add(pendingHolder);
            }
        }
        CommandsData.Holder before = holderList.getSelected();
        holderList.setItems(holders);
        if (before != null) {
            // The same player comes back under their UUID once their first entry exists.
            holders.stream().filter(h -> h.player() == before.player() && h.label().equalsIgnoreCase(before.label()))
                    .findFirst().ifPresent(h -> holderList.selectByKey(holderKey(h)));
        }
    }

    /** Number of buttons at the top right of the details panel. */
    private int headerButtons(CommandsData.Row row) {
        if (row == null || !row.exposed()) return 0;
        return 1 + (showsServers(row.servers()) ? 1 : 0);
    }

    private void buildDetailButtons() {
        CommandsData.Row row = list.getSelected();
        if (row == null) return;
        boolean editable = canEdit(GuiArea.COMMANDS);
        Rect actions = details().inset(6).bottom(2 * Atlas.BUTTON_HEIGHT + 4);
        Rect inner = details().inset(8);
        if (row.exposed()) {
            int x = inner.right() - Atlas.BUTTON_HEIGHT * headerButtons(row) - 4 * (headerButtons(row) - 1);
            addRenderableWidget(CpButton.neutral(Component.literal("Who"), () -> {
                        whoView = !whoView;
                        if (whoView) serversView = false;
                        rebuild();
                    })
                    .iconOnly(Icon.USER).selected(whoView)
                    .tooltip(Component.literal(whoView ? "Back to how /" + row.name() + " is authorised."
                            : "The grades and players whose entries decide where /" + row.name() + " may be used."))
                    .at(new Rect(x, inner.y() - 2, Atlas.BUTTON_HEIGHT, Atlas.BUTTON_HEIGHT)));
            if (showingWho(row)) buildWho(row, inner, actions.y() - GAP);
        }
        if (row.exposed() && showsServers(row.servers())) {
            // A view switch: the explanation and the toggles share the space between the header and the buttons.
            addRenderableWidget(CpButton.neutral(Component.literal("Servers"), () -> {
                        serversView = !serversView;
                        if (serversView) whoView = false;
                        rebuild();
                    })
                    .iconOnly(Icon.HOME).selected(serversView)
                    .tooltip(Component.literal(serversView ? "Back to how /" + row.name() + " is authorised."
                            : "The cluster members /" + row.name() + " is exposed on."))
                    .at(new Rect(inner.right() - Atlas.BUTTON_HEIGHT, inner.y() - 2, Atlas.BUTTON_HEIGHT, Atlas.BUTTON_HEIGHT)));
            if (serversView) {
                Rect area = new Rect(inner.x(), inner.y() + HEADER + SERVERS_TITLE, inner.w(),
                        serverTogglesHeight(inner.w(), row.servers(), ClusterView.COMMANDS));
                buildServerToggles(area, row.servers(), ClusterView.COMMANDS, "exposed commands", editable,
                        list -> act(GuiAction.COMMAND_SERVERS, row.name(), list));
            }
        }

        CpButton primary = row.exposed()
                ? CpButton.danger(Component.literal("Hide"), () -> confirmHide(row)).icon(Icon.MINUS)
                : CpButton.accent(Component.literal("Expose"), () -> act(GuiAction.COMMAND_EXPOSE, row.name())).icon(Icon.PLUS);
        primary.enabled(editable && !(row.missing() && !row.exposed()));
        addRenderableWidget(primary.at(actions.top(Atlas.BUTTON_HEIGHT)));

        CpButton keep = CpButton.neutral(Component.literal(row.keepOriginal() ? "Keep original: on" : "Keep original: off"),
                () -> act(GuiAction.COMMAND_KEEP_ORIGINAL, row.name(), String.valueOf(!row.keepOriginal())))
                .icon(row.keepOriginal() ? Icon.LOCK : Icon.CHECK)
                .enabled(editable && row.exposed())
                .tooltip(Component.literal("On: players need customperm.command." + row.name()
                        + " AND the command's own requirement. Off: the node alone is enough."));
        addRenderableWidget(keep.at(actions.bottom(Atlas.BUTTON_HEIGHT)));
    }

    /** Whether the details panel shows the selected command's servers instead of how it is authorised. */
    private boolean showingServers(CommandsData.Row row) {
        return serversView && row != null && row.exposed() && showsServers(row.servers());
    }

    /** Whether the details panel shows who may use the selected command where. */
    private boolean showingWho(CommandsData.Row row) {
        return whoView && row != null && row.exposed();
    }

    /** Top of the Who view's content, under the header and its title. */
    private int whoTop(Rect inner) {
        return inner.y() + HEADER + SERVERS_TITLE;
    }

    /**
     * Grade or Player, a name and a pick button; then the holders; then, for the one selected, its entry held
     * everywhere and one toggle per server. Under LuckPerms its own contexts decide, and only a sentence is drawn.
     */
    private void buildWho(CommandsData.Row row, Rect inner, int bottom) {
        if (context.luckPermsActive()) return;
        boolean editable = canEdit(GuiArea.GRADES);
        int y = whoTop(inner);
        int button = Atlas.BUTTON_HEIGHT;
        CpButton grade = CpButton.ghost(Component.literal("Grade"), () -> {
            whoPlayer = false;
            rebuild();
        }).selected(!whoPlayer);
        CpButton player = CpButton.ghost(Component.literal("Player"), () -> {
            whoPlayer = true;
            rebuild();
        }).selected(whoPlayer);
        int gw = grade.preferredWidth(font, 6);
        int pw = player.preferredWidth(font, 6);
        if (gw + pw + button + 8 > inner.w() / 2) {
            grade.iconOnly(Icon.SHIELD);
            player.iconOnly(Icon.USER);
            gw = button;
            pw = button;
        }
        addRenderableWidget(grade.at(inner.x(), y, gw, button));
        addRenderableWidget(player.at(inner.x() + gw + 2, y, pw, button));
        int fieldX = inner.x() + gw + pw + 6;
        whoName.hint(Component.literal(whoPlayer ? "player" : "grade"));
        addRenderableWidget(whoName.at(new Rect(fieldX, y + (button - Atlas.INPUT_HEIGHT) / 2,
                inner.right() - button - 4 - fieldX, Atlas.INPUT_HEIGHT)));
        addRenderableWidget(CpButton.accent(Component.literal("Pick"), this::pickHolder).iconOnly(Icon.PLUS)
                .tooltip(Component.literal("Show this grade or player, to set where they may use /" + row.name() + "."))
                .at(new Rect(inner.right() - button, y, button, button)));

        CommandsData.Holder selected = holderList.getSelected();
        java.util.Map<String, String> states = selected == null ? java.util.Map.of() : servers(selected);
        int toggles = selected == null ? 0 : button + 4 + nodeServerTogglesHeight(inner.w(), states);
        int listTop = y + button + 4;
        int listH = Math.max(0, bottom - toggles - 4 - listTop);
        if (listH >= ROW * 2) addRenderableWidget(holderList.at(new Rect(inner.x(), listTop, inner.w(), listH)));
        if (selected == null) return;

        String node = "customperm.command." + row.name();
        int everyY = listTop + listH + 4;
        String now = selected.everywhere();
        String next = now.equals("allow") ? "deny" : now.equals("deny") ? "inherit" : "allow";
        String label = switch (now) {
            case "allow" -> "Everywhere: allowed";
            case "deny" -> "Everywhere: denied";
            default -> "Everywhere: not set";
        };
        CpButton everywhere = switch (now) {
            case "allow" -> CpButton.good(Component.literal(label), () -> sendHolder(selected, node, "*", next)).icon(Icon.CHECK);
            case "deny" -> CpButton.danger(Component.literal(label), () -> sendHolder(selected, node, "*", next)).icon(Icon.CROSS);
            default -> CpButton.neutral(Component.literal(label), () -> sendHolder(selected, node, "*", next));
        };
        addRenderableWidget(everywhere.enabled(editable)
                .tooltip(Component.literal("Their entry for " + node + " held everywhere. Click: "
                        + (next.equals("inherit") ? "remove it" : next) + ". A server below has the last word over it."))
                .at(new Rect(inner.x(), everyY, Math.min(inner.w(), everywhere.preferredWidth(font, 6)), button)));
        buildNodeServerToggles(new Rect(inner.x(), everyY + button + 4, inner.w(), nodeServerTogglesHeight(inner.w(), states)),
                states, editable, (server, state) -> sendHolder(selected, node, server, state));
    }

    private static java.util.Map<String, String> servers(CommandsData.Holder holder) {
        java.util.Map<String, String> states = new java.util.TreeMap<>();
        for (String entry : holder.servers()) {
            int eq = entry.indexOf('=');
            if (eq > 0) states.put(entry.substring(0, eq), entry.substring(eq + 1));
        }
        return states;
    }

    private void sendHolder(CommandsData.Holder holder, String node, String server, String state) {
        act(holder.player() ? GuiAction.USER_NODE_SERVER : GuiAction.GRADE_NODE_SERVER, holder.id(), node, server, state);
    }

    /** Shows the grade or player typed, even before they have any entry for the command. */
    private void pickHolder() {
        String name = whoName.getValue().trim();
        if (name.isEmpty()) {
            status("Type a " + (whoPlayer ? "player" : "grade") + " to pick.", false);
            return;
        }
        CommandsData.Holder holder = new CommandsData.Holder(whoPlayer, name, name, "", List.of());
        pendingHolder = holder;
        refreshHolders();
        holderList.items().stream()
                .filter(h -> h.player() == whoPlayer && (h.id().equals(name) || h.label().equalsIgnoreCase(name)))
                .findFirst().ifPresent(h -> holderList.selectByKey(holderKey(h)));
        whoName.setValue("");
        rebuild();
    }

    private void toggleGateAll() {
        if (data.gateAll()) {
            act(GuiAction.COMMAND_GATE_ALL, "false");
            return;
        }
        confirm("Gate every command",
                "Every command follows its node: a DENY blocks operators too, an ALLOW (or *) opens it to anyone.",
                "Gate all",
                () -> act(GuiAction.COMMAND_GATE_ALL, "true"));
    }

    private void primaryAction(CommandsData.Row row) {
        if (!canEdit(GuiArea.COMMANDS)) return;
        if (row.exposed()) {
            confirmHide(row);
        } else if (!row.missing()) {
            act(GuiAction.COMMAND_EXPOSE, row.name());
        }
    }

    private void confirmHide(CommandsData.Row row) {
        confirm("Stop exposing /" + row.name(),
                "Players who run it through customperm.command." + row.name()
                        + " lose access. The command goes back to its original authorisation.",
                "Hide /" + row.name(),
                () -> act(GuiAction.COMMAND_HIDE, row.name()));
    }

    // ------------------------------------------------------------------ rendering

    private static String holderSummary(CommandsData.Holder holder) {
        List<String> parts = new java.util.ArrayList<>();
        if (!holder.everywhere().isEmpty()) parts.add(holder.everywhere().equals("allow") ? "everywhere" : "not anywhere");
        for (String entry : holder.servers()) {
            int eq = entry.indexOf('=');
            if (eq > 0) parts.add((entry.endsWith("=deny") ? "not " : "") + entry.substring(0, eq));
        }
        return parts.isEmpty() ? "nothing set yet" : String.join(", ", parts);
    }

    private void renderHolder(GuiGraphics g, Font font, CommandsData.Holder holder, Rect r, boolean hovered, boolean selected) {
        int y = r.y() + (r.h() - 8) / 2;
        String badge = holder.player() ? "PLAYER" : "GRADE";
        int bw = font.width(badge) + 6;
        Skin.badge(g, font, badge, r.x() + 4, r.centerY(), holder.player() ? Palette.WARN : Palette.TEXT_MUTE);
        String summary = holderSummary(holder);
        int nameW = Math.min(font.width(holder.label()), (r.w() - bw - 16) / 2);
        Skin.text(g, font, holder.label(), r.x() + 8 + bw, y, nameW, Palette.TEXT);
        int x = r.x() + 12 + bw + nameW;
        Skin.text(g, font, summary, x, y, r.right() - 4 - x, Palette.TEXT_DIM);
    }

    private static String status(CommandsData.Row row) {
        if (row.exposed() && row.missing()) return "exposed, not on this server";
        if (row.exposed() && !row.servers().isEmpty()) return "exposed on " + String.join(", ", row.servers());
        return row.exposed() ? "exposed" : "not exposed";
    }

    private void renderRow(GuiGraphics g, Font font, CommandsData.Row row, Rect r, boolean hovered, boolean selected) {
        int dot = row.missing() ? Palette.WARN : row.exposed() ? Palette.GOOD : Palette.LINE_STRONG;
        Skin.dot(g, r.x() + 6, r.centerY(), dot);

        int right = r.right() - 4;
        right = badgeLeft(g, font, row.missing(), "MISSING", Palette.DANGER, right, r);
        right = badgeLeft(g, font, row.exposed() && elsewhereOnly(row.servers()), "OFF", Palette.TEXT_MUTE, right, r);
        right = badgeLeft(g, font, row.keepOriginal() && row.exposed(), "KEEP", Palette.ACCENT_HI, right, r);
        right = badgeLeft(g, font, row.rateLimited(), "LIMIT", Palette.WARN, right, r);
        right = badgeLeft(g, font, row.alias(), "ALIAS", Palette.INFO, right, r);

        int x = r.x() + 6 + Atlas.DOT_SIZE + 5;
        Skin.text(g, font, "/" + row.name(), x, r.y() + (r.h() - 8) / 2, right - x - 4,
                row.exposed() && !elsewhereOnly(row.servers()) ? Palette.TEXT : Palette.TEXT_DIM);
    }

    /** Draws a badge ending at {@code right} when {@code show}; returns the new right edge. */
    private static int badgeLeft(GuiGraphics g, Font font, boolean show, String text, int color, int right, Rect row) {
        if (!show) return right;
        int w = font.width(text) + 6;
        Skin.badge(g, font, text, right - w, row.centerY(), color);
        return right - w - 3;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect bar = toolbar();
        long exposedCount = data.rows().stream().filter(CommandsData.Row::exposed).count();
        String count = exposedCount + " exposed / " + data.rows().size() + (data.truncated() ? "+" : "");
        int w = font.width(count);
        int countRight = toolbarRight < bar.right() ? toolbarRight - GAP : bar.right();
        if (countRight - w >= filtersRight + GAP) {
            Skin.text(g, font, count, countRight - w, bar.y() + (bar.h() - 8) / 2, Palette.TEXT_MUTE);
        }

        Rect panel = details();
        Skin.panel(g, panel);
        Rect inner = panel.inset(8);
        CommandsData.Row row = list.getSelected();
        if (row == null) {
            paragraph(g, "Select a command to see how it is authorised. Double-click or Enter exposes it.",
                    inner, inner.y(), Palette.TEXT_MUTE);
            return;
        }

        int buttons = headerButtons(row) * (Atlas.BUTTON_HEIGHT + 4);
        Skin.text(g, font, "/" + row.name(), inner.x(), inner.y(), inner.w() - buttons, Palette.TEXT);
        int dot = row.missing() ? Palette.WARN : row.exposed() ? Palette.GOOD : Palette.TEXT_MUTE;
        Skin.dot(g, inner.x(), inner.y() + 16, dot);
        Skin.text(g, font, status(row), inner.x() + Atlas.DOT_SIZE + 4, inner.y() + 12,
                inner.w() - Atlas.DOT_SIZE - 4 - buttons, Palette.TEXT_DIM);

        Rect text = new Rect(inner.x(), inner.y(), inner.w(), inner.h() - 2 * Atlas.BUTTON_HEIGHT - 4 - GAP);
        if (showingServers(row)) {
            Skin.text(g, font, "EXPOSED ON", inner.x(), inner.y() + HEADER, inner.w(), Palette.TEXT_MUTE);
            return;
        }
        if (showingWho(row)) {
            Skin.text(g, font, "COMMAND, THEN GRADE, THEN PLAYER", inner.x(), inner.y() + HEADER, inner.w(), Palette.TEXT_MUTE);
            if (context.luckPermsActive()) {
                paragraph(g, "Under LuckPerms, who may use /" + row.name() + " where is set there: customperm.command."
                        + row.name() + " with a server= context.", text, whoTop(inner), Palette.TEXT_DIM);
            } else if (!canEdit(GuiArea.GRADES)) {
                paragraph(g, "Read-only: changing entries needs " + GuiArea.GRADES.node() + ".", text,
                        text.bottom() - 10, Palette.TEXT_MUTE);
            }
            return;
        }
        int y = inner.y() + HEADER;
        boolean gated = data.gateAll() && !context.luckPermsInstalled();
        if (row.exposed()) {
            y = paragraph(g, "Players holding customperm.command." + row.name() + " can run it"
                    + (row.keepOriginal() ? ", if they also pass the command's own requirement." : "."), text, y, Palette.TEXT_DIM);
        } else if (gated) {
            y = paragraph(g, "Gated: its node opens it to anyone, a DENY blocks operators too.", text, y, Palette.TEXT_DIM);
        } else {
            y = paragraph(g, "Only its original requirement applies (usually operators). Expose it to grant it with customperm.command."
                    + row.name() + ".", text, y, Palette.TEXT_DIM);
        }
        if (row.missing()) {
            y = paragraph(g, "No command of this name is registered right now: its mod may be missing. It can still be hidden.",
                    text, y + 4, Palette.WARN);
        }
        if (row.alias()) {
            y = paragraph(g, "This is a CustomPerm alias, authorised by customperm.alias." + row.name() + ".", text, y + 4, Palette.INFO);
        }
        if (row.rateLimited()) {
            y = paragraph(g, "An enabled rate limit applies to it.", text, y + 4, Palette.TEXT_MUTE);
        }
        if (!canEdit(GuiArea.COMMANDS)) {
            paragraph(g, "Read-only: changing exposure needs " + GuiArea.COMMANDS.node() + ".", text, y + 4, Palette.TEXT_MUTE);
        }
    }
}
