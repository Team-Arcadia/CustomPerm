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
import com.arcadia.customperm.client.gui.kit.CpScreen;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.client.gui.kit.WindowLayout;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base of the CustomPerm admin pages: navigation sidebar, header toolbar, and the round trip with the
 * server. A page never changes configuration locally: it sends a {@link GuiAction}, shows the result on
 * its status line, and redraws from the page the server pushes back.
 *
 * <p><strong>Refresh.</strong> {@link #refresh} replaces the page data and rebuilds the widgets.
 * Subclasses keep what the admin is doing (search text, selected row, half-typed form) in fields
 * that survive the rebuild, and restore it in {@link #buildPage()}.
 */
public abstract class AdminScreen extends CpScreen {

    private static final int NAV_ROW = 18;
    private static final int TOOL = 16;

    protected GuiContext context;

    protected AdminScreen(Component title, GuiContext context) {
        super(title);
        this.context = context;
    }

    /**
     * Places buttons on one row, in a width that is not always enough for them. Their natural widths when
     * they fit, the last one against the right edge when {@code lastAtRight}; without their icons when that
     * is what it takes, the label being what says what a button does; then with tighter padding and
     * gaps; an equal share when even that overflows, each label then clipped by the button rather than drawn over its neighbour.
     *
     * <p>Written after seeing three buttons overlap and a tab row run past its panel on an ordinary small
     * window: a row that assumes its content fits is a row that breaks on someone's screen.
     */
    protected void placeButtonRow(Rect row, int padding, boolean lastAtRight, List<CpButton> buttons) {
        int gaps = 4 * (buttons.size() - 1);
        int natural = rowWidth(buttons, padding);
        if (natural + gaps > row.w()) {
            buttons.forEach(button -> button.icon(null));
            natural = rowWidth(buttons, padding);
        }
        int gap = 4;
        if (natural + gaps > row.w()) {
            // Tighter padding and gaps before labels get clipped: every label whole beats roomy buttons.
            gap = 2;
            gaps = gap * (buttons.size() - 1);
            int spare = row.w() - gaps - rowWidth(buttons, 0);
            padding = Math.min(padding, spare / (2 * buttons.size()));
            if (padding < CpButton.MIN_PADDING) {
                int each = (row.w() - gaps) / buttons.size();
                int x = row.x();
                for (CpButton button : buttons) {
                    addRenderableWidget(button.at(new Rect(x, row.y(), each, row.h())));
                    x += each + gap;
                }
                return;
            }
        }
        int x = row.x();
        for (int i = 0; i < buttons.size(); i++) {
            CpButton button = buttons.get(i);
            int width = button.preferredWidth(font, padding);
            boolean last = i == buttons.size() - 1;
            addRenderableWidget(button.at(last && lastAtRight
                    ? row.right(width)
                    : new Rect(x, row.y(), width, row.h())));
            x += width + gap;
        }
    }

    private int rowWidth(List<CpButton> buttons, int padding) {
        int total = 0;
        for (CpButton button : buttons) total += button.preferredWidth(font, padding);
        return total;
    }

    /** The page this screen shows. */
    public abstract GuiPage page();

    /** Stores fresh page data; the widgets are rebuilt right after. */
    protected abstract void apply(GuiPageData data);

    /** Adds the page widgets inside {@code layout.content()}. */
    protected abstract void buildPage();

    /** Applies a server push for this page, keeping the screen open. */
    public final void refresh(GuiContext newContext, GuiPageData data) {
        this.context = newContext;
        apply(data);
        rebuild();
    }

    /**
     * Rebuilds the widgets and gives keyboard focus back to the widget that had it, when that widget
     * instance survives the rebuild (screens keep their list and search box across rebuilds). Without
     * this, moving the selection with the arrow keys would drop focus at the first redraw.
     */
    protected final void rebuild() {
        GuiEventListener focused = getFocused();
        // A page that rebuilds as a field is typed in (a search refilters) must not close that field's candidates.
        boolean listOpen = focused instanceof CpEditBox box && box.completionsOpen();
        rebuildWidgets();
        if (focused != null && children().contains(focused)) {
            setFocused(focused);
            if (listOpen) ((CpEditBox) focused).reopenCompletions();
        }
    }

    /** Whether this admin may write to {@code area}; screens render read-only otherwise. */
    protected final boolean canEdit(GuiArea area) {
        return context.canEdit(area);
    }

    @Override
    protected final boolean hasSidebar() {
        return true;
    }

    /** A banner shown above the page content, e.g. why this page is not active. */
    public record Banner(Icon icon, String text, int color) {
    }

    /** The banner for the current state, or {@code null} for none. */
    protected Banner banner() {
        return null;
    }

    private Rect bannerRect;

    @Override
    protected final void build() {
        Banner banner = banner();
        bannerRect = null;
        if (banner != null) {
            // Reserve the banner's height at the top of the content area, so every page lays out under it.
            Rect content = layout.content();
            int textW = content.w() - Atlas.ICON_SIZE - 16;
            int lines = Math.max(1, Math.min(3, font.split(Component.literal(banner.text()), textW).size()));
            bannerRect = content.top(lines * 10 + 8);
            layout = new WindowLayout(layout.window(), layout.header(), layout.sidebar(),
                    content.belowTop(bannerRect.h() + 6), layout.footer());
        }
        serverNote = null;
        buildNavigation();
        buildToolbar();
        buildPage();
    }

    // ------------------------------------------------------------------ navigation

    private void buildNavigation() {
        if (!layout.hasSidebar()) return;
        Rect side = layout.sidebar().inset(4, 6);
        int y = side.y();
        for (AdminScreens.NavEntry entry : AdminScreens.navigation(context)) {
            CpButton button = CpButton.ghost(Component.literal(entry.label()), () -> navigate(entry.page()))
                    .icon(entry.icon())
                    .selected(entry.page() == page())
                    .at(side.x(), y, side.w(), NAV_ROW);
            addRenderableWidget(button);
            y += NAV_ROW + 2;
        }
    }

    private void buildToolbar() {
        Rect header = layout.header();
        int y = header.y() + (header.h() - TOOL) / 2;
        int x = header.right() - 4 - TOOL;
        addRenderableWidget(CpButton.ghost(Component.literal("Close"), this::onClose)
                .iconOnly(Icon.CROSS).at(x, y, TOOL, TOOL));
        x -= TOOL + 2;
        addRenderableWidget(CpButton.ghost(Component.literal("Refresh"), () -> navigate(page()))
                .iconOnly(Icon.REFRESH).at(x, y, TOOL, TOOL));
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Banner banner = banner();
        if (banner != null && bannerRect != null) {
            g.fill(bannerRect.x(), bannerRect.y(), bannerRect.right(), bannerRect.bottom(),
                    Palette.mix(Palette.BG1, banner.color(), 0.18f));
            Skin.outline(g, bannerRect, Palette.mix(Palette.BG1, banner.color(), 0.6f));
            g.fill(bannerRect.x(), bannerRect.y(), bannerRect.x() + 2, bannerRect.bottom(), banner.color());
            Skin.icon(g, banner.icon(), bannerRect.x() + 7, bannerRect.y() + 5, banner.color());
            paragraph(g, banner.text(), new Rect(bannerRect.x() + Atlas.ICON_SIZE + 12, bannerRect.y() + 4,
                    bannerRect.w() - Atlas.ICON_SIZE - 16, bannerRect.h() - 4), bannerRect.y() + 5, Palette.TEXT);
        }
        renderContent(g, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------ cluster server lists

    private static final int SERVER_GAP = 4;
    /** The note under the server toggles, drawn over the page; null when the toggles say it all. */
    private String serverNote;
    private Rect serverNoteAt;
    private int serverNoteColor;

    /** Whether a list leaves this server out, for a badge on the element's row; false outside a cluster. */
    protected final boolean elsewhereOnly(List<String> servers) {
        var cluster = context.cluster();
        return cluster.inCluster() && !servers.isEmpty() && !servers.contains(cluster.here());
    }

    /** Whether a {@code servers} list is worth showing: in a cluster, or when one was set before leaving it. */
    protected final boolean showsServers(List<String> servers) {
        return context.cluster().inCluster() || !servers.isEmpty();
    }

    /** Height {@link #buildServerToggles} takes in {@code width}: its lines of buttons and the note line. */
    protected final int serverTogglesHeight(int width, List<String> servers, int part) {
        int lines = flow(width, serverButtons(servers, part, false, text -> { })).size();
        return lines * Atlas.BUTTON_HEIGHT + (lines - 1) * SERVER_GAP + (note(servers, part, "") == null ? 0 : 12);
    }

    /**
     * The cluster members an element is active on, as toggles: All members, then one per member heard, this server
     * marked, then any listed name no member answers to now, marked too and kept. A click sends the new list, names
     * joined by commas or {@code all}. A note under them says when the list is ignored here or stays on this server.
     */
    protected final void buildServerToggles(Rect area, List<String> servers, int part, String partName, boolean editable,
                                            Consumer<String> send) {
        List<List<CpButton>> lines = flow(area.w(), serverButtons(servers, part, editable, send));
        int y = area.y();
        for (List<CpButton> line : lines) {
            int x = area.x();
            for (CpButton button : line) {
                int w = Math.min(button.preferredWidth(font, 6), area.w());
                addRenderableWidget(button.at(x, y, w, Atlas.BUTTON_HEIGHT));
                x += w + SERVER_GAP;
            }
            y += Atlas.BUTTON_HEIGHT + SERVER_GAP;
        }
        serverNote = note(servers, part, partName);
        serverNoteAt = new Rect(area.x(), y - SERVER_GAP + 3, area.w(), 9);
        serverNoteColor = context.cluster().inCluster() && context.cluster().shares(part) ? Palette.TEXT_MUTE : Palette.WARN;
    }

    private String note(List<String> servers, int part, String partName) {
        var cluster = context.cluster();
        if (!cluster.inCluster()) return "No cluster here: the list is kept, not read.";
        if (!cluster.shares(part)) return "This server keeps its " + partName + " local: the list stays here.";
        if (!servers.isEmpty() && !servers.contains(cluster.here())) return "Not active on this server.";
        return null;
    }

    private List<CpButton> serverButtons(List<String> servers, int part, boolean editable, Consumer<String> send) {
        var cluster = context.cluster();
        boolean enabled = editable && cluster.inCluster();
        List<String> names = new ArrayList<>(cluster.members());
        // This server even when it hears no member: the database may be down, and it can still be picked.
        if (cluster.inCluster() && !names.contains(cluster.here())) names.add(0, cluster.here());
        servers.stream().filter(name -> !names.contains(name)).forEach(names::add);
        List<CpButton> buttons = new ArrayList<>();
        buttons.add(toggle("All", servers.isEmpty(), () -> send.accept(com.arcadia.customperm.config.ServerScope.ALL))
                .enabled(enabled)
                .tooltip(Component.literal("All members: active on every member of the cluster, no list.")));
        for (String name : names) {
            boolean listed = servers.contains(name);
            boolean here = name.equals(cluster.here());
            boolean heard = here || cluster.members().contains(name);
            List<String> next = new ArrayList<>(servers);
            if (listed) next.remove(name);
            else next.add(name);
            CpButton button = toggle(name, listed,
                            () -> send.accept(next.isEmpty() ? com.arcadia.customperm.config.ServerScope.ALL : String.join(",", next)))
                    .enabled(enabled)
                    .tooltip(Component.literal(name + ": " + (!heard
                            ? "no member answers to this name right now. Kept, so it applies once that server runs."
                            : here ? "this server." : "a member of the cluster.")));
            // The icon says which server it is; the fill says whether it is picked.
            if (!heard) button.icon(Icon.WARN);
            else if (here) button.icon(Icon.HOME);
            buttons.add(button);
        }
        return buttons;
    }

    // ------------------------------------------------------------------ one node, server by server

    /** Title line above the per-server buttons of a node. */
    protected static final int NODE_SERVERS_TITLE = 12;

    /**
     * What a holder says about {@code node} on each server, from their entries: {@code allow} or {@code deny} for
     * an entry limited to exactly {@code server=<name>}, absent when they say nothing there. {@code rows} gives each
     * entry as node, whether it denies, and its context.
     */
    protected static <R> java.util.Map<String, String> nodeServerStates(String node, List<R> rows,
            java.util.function.Function<R, String> nodeOf, java.util.function.Predicate<R> denies,
            java.util.function.Function<R, String> contextOf) {
        java.util.Map<String, String> states = new java.util.TreeMap<>();
        for (R row : rows) {
            String context = contextOf.apply(row);
            if (!nodeOf.apply(row).equals(node) || context == null || !context.startsWith("server=") || context.contains(",")) continue;
            states.put(context.substring("server=".length()), denies.test(row) ? "deny" : "allow");
        }
        return states;
    }

    /** Height of {@link #buildNodeServerToggles} in {@code width}, its title included. */
    protected final int nodeServerTogglesHeight(int width, java.util.Map<String, String> states) {
        int lines = flow(width, nodeServerButtons(states, false, (s, n) -> { })).size();
        return NODE_SERVERS_TITLE + lines * Atlas.BUTTON_HEIGHT + (lines - 1) * SERVER_GAP;
    }

    /**
     * One button per server for a node: framed while the holder says nothing there and follows their other entries,
     * green when allowed there, red when denied there. A click moves to the next state, and {@code send} gets the
     * server and that state: {@code allow}, {@code deny} or {@code inherit}.
     */
    protected final void buildNodeServerToggles(Rect area, java.util.Map<String, String> states, boolean editable,
                                                java.util.function.BiConsumer<String, String> send) {
        List<List<CpButton>> lines = flow(area.w(), nodeServerButtons(states, editable, send));
        int y = area.y() + NODE_SERVERS_TITLE;
        for (List<CpButton> line : lines) {
            int x = area.x();
            for (CpButton button : line) {
                int w = Math.min(button.preferredWidth(font, 6), area.w());
                addRenderableWidget(button.at(x, y, w, Atlas.BUTTON_HEIGHT));
                x += w + SERVER_GAP;
            }
            y += Atlas.BUTTON_HEIGHT + SERVER_GAP;
        }
    }

    private List<CpButton> nodeServerButtons(java.util.Map<String, String> states, boolean editable,
                                             java.util.function.BiConsumer<String, String> send) {
        var cluster = context.cluster();
        List<String> names = new ArrayList<>(cluster.members());
        if (cluster.inCluster() && !names.contains(cluster.here())) names.add(0, cluster.here());
        states.keySet().stream().filter(name -> !names.contains(name)).forEach(names::add);
        List<CpButton> buttons = new ArrayList<>();
        for (String name : names) {
            String state = states.getOrDefault(name, "inherit");
            String next = switch (state) {
                case "inherit" -> "allow";
                case "allow" -> "deny";
                default -> "inherit";
            };
            CpButton button = switch (state) {
                case "allow" -> CpButton.good(Component.literal(name), () -> send.accept(name, next)).icon(Icon.CHECK);
                case "deny" -> CpButton.danger(Component.literal(name), () -> send.accept(name, next)).icon(Icon.CROSS);
                default -> CpButton.neutral(Component.literal(name), () -> send.accept(name, next));
            };
            String now = switch (state) {
                case "allow" -> "allowed on " + name;
                case "deny" -> "denied on " + name;
                default -> "follows the other entries on " + name;
            };
            buttons.add(button.enabled(editable && cluster.inCluster())
                    .tooltip(Component.literal("Now " + now + (name.equals(cluster.here()) ? " (this server)" : "")
                            + ". Click: " + (next.equals("inherit") ? "follow the other entries" : next) + " there.")));
        }
        return buttons;
    }

    /** A toggle that reads as one at a glance: filled when on, framed when off. */
    private static CpButton toggle(String label, boolean on, Runnable action) {
        return on ? CpButton.accent(Component.literal(label), action) : CpButton.neutral(Component.literal(label), action);
    }

    /** Buttons cut into lines that fit {@code width}, each line keeping at least one button. */
    private List<List<CpButton>> flow(int width, List<CpButton> buttons) {
        List<List<CpButton>> lines = new ArrayList<>();
        List<CpButton> line = new ArrayList<>();
        int used = 0;
        for (CpButton button : buttons) {
            int w = Math.min(button.preferredWidth(font, 6), width);
            if (!line.isEmpty() && used + SERVER_GAP + w > width) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            used += (line.isEmpty() ? 0 : SERVER_GAP) + w;
            line.add(button);
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    @Override
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (serverNote != null && serverNoteAt != null) {
            Skin.text(g, font, serverNote, serverNoteAt.x(), serverNoteAt.y(), serverNoteAt.w(), serverNoteColor);
        }
        if (context.alertCount() > 0 && layout.hasSidebar()) {
            // Alert badge on the dashboard entry, whichever page is open.
            Rect side = layout.sidebar().inset(4, 6);
            String count = String.valueOf(context.alertCount());
            int w = font.width(count) + 6;
            int x = side.right() - w - 4;
            int y = side.y() + (NAV_ROW - 10) / 2;
            g.fill(x, y, x + w, y + 10, Palette.DANGER);
            Skin.text(g, font, count, x + 3, y + 1, Palette.ON_COLOR);
        }
    }

    /** Non-widget page content, drawn under the widgets. */
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    // ------------------------------------------------------------------ server round trip

    /** Asks the server for a page; the reply opens it (or refreshes this one). */
    protected final void navigate(GuiPage target) {
        info("Loading...");
        PacketDistributor.sendToServer(new GuiRequestPayload(target.id()));
    }

    /** Sends an action; the result lands on the status line and the page refreshes itself. */
    protected final void act(GuiAction action, String... args) {
        info("Working...");
        PacketDistributor.sendToServer(new GuiActionPayload(action.name(), List.of(args), page().id()));
    }

    // ------------------------------------------------------------------ drawing helpers

    /**
     * Draws wrapped text from {@code y} inside {@code area}, stopping at its bottom.
     *
     * @return the y below the last drawn line
     */
    protected final int paragraph(GuiGraphics g, String text, Rect area, int y, int color) {
        for (var line : font.split(Component.literal(text), area.w())) {
            if (y + 9 > area.bottom()) break;
            g.drawString(font, line, area.x(), y, color, false);
            y += 10;
        }
        return y;
    }

    /** Section title with a divider, as used above lists and forms. */
    protected final void sectionTitle(GuiGraphics g, String text, Rect area) {
        Skin.text(g, font, text.toUpperCase(java.util.Locale.ROOT), area.x(), area.y(), area.w(), Palette.TEXT_MUTE);
        Skin.hDivider(g, area.x(), area.right(), area.y() + 10);
    }
}
