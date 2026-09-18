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
import com.arcadia.customperm.network.gui.GuiCodecs;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.PlayersData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Nodes carried by one player rather than by a grade. Left: the players who hold something of their own,
 * plus everyone online. Right: the selected player's own ALLOW and DENY nodes, and the grades they hold,
 * read-only here since a grade is defined on the Grades page.
 *
 * <p>A node on a player wins over their grades at the same specificity, whatever a grade weighs, but it
 * does not beat a more specific grade node. Grades, and the grades the player refuses, are shown here and
 * edited on the Grades page, which is where they are defined.
 *
 * <p>A player who holds nothing yet is not in the list: typing their name in the field below it selects
 * them so a first node can be added. That row is local until the node exists; nothing is written for a
 * player with no node.
 */
public final class PlayersScreen extends AdminScreen {

    private static final int ROW = 16;
    private static final int FIELD = Atlas.INPUT_HEIGHT;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;
    private static final int DURATION_FIELD = 64;

    /** One node of the selected player; {@code context} is empty for a node that applies everywhere. */
    private record NodeRow(String node, boolean deny, String context) {
    }

    private PlayersData data;

    private final CpEditBox search;
    private final CpList<PlayersData.Player> playerList;
    private final CpEditBox newPlayer;
    private final CpList<NodeRow> nodeList;
    private final CpEditBox nodeField;
    private final ChatFields chat;
    /** How long a node added lasts; empty for good. */
    private final CpEditBox durationField;
    /** The world a node added is limited to; empty for everywhere. */
    private final CpEditBox worldField;
    /** What the right-hand side shows for the selected player. */
    private enum Tab { NODES, CHAT, TRACKS }

    private Tab tab = Tab.NODES;

    /** One track, with the rung the selected player stands on, -1 for none. */
    private record TrackRow(PlayersData.Track track, int rung) {
    }

    private final CpList<TrackRow> trackList;
    /** Name typed in the field below the list, shown as a row while that player holds nothing. */
    private String pendingPlayer;

    public PlayersScreen(GuiContext context, PlayersData data) {
        super(Component.literal("Players"), context);
        this.data = data;
        this.nodeField = new CpEditBox(Component.literal("Permission node"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("permission node"))
                .onSubmit(() -> addNode(false));
        this.chat = new ChatFields(this::rebuild);
        this.durationField = new CpEditBox(Component.literal("Duration"), 16)
                .hint(Component.literal("for, e.g. 30d"));
        this.worldField = new CpEditBox(Component.literal("World"), 64)
                .hint(Component.literal("in, e.g. the_nether"));
        this.search = new CpEditBox(Component.literal("Search players"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .onChange(text -> refilter());
        this.playerList = new CpList<PlayersData.Player>(Component.literal("Players"), ROW)
                .renderer(this::renderPlayer)
                .label(p -> p.name() + (p.online() ? ", online" : ", offline") + ", " + p.allow().size()
                        + " allowed, " + p.deny().size() + " denied, " + p.grades().size() + " grades, "
                        + p.refused().size() + " refused")
                .identity(PlayersData.Player::uuid)
                .emptyText("No player holds a node of their own: name one below.")
                .onSelect(p -> {
                    fillDetails();
                    rebuild();
                });
        this.newPlayer = new CpEditBox(Component.literal("Player name"), 16)
                .hint(Component.literal("player name"))
                .onSubmit(this::track);
        newPlayer.onChange(this::suggestPlayer);
        this.nodeList = new CpList<NodeRow>(Component.literal("Nodes"), 14)
                .renderer(this::renderNode)
                .label(n -> (n.deny() ? "denied " : "allowed ") + n.node())
                .identity(n -> (n.deny() ? "deny:" : "allow:") + n.node())
                .emptyText("No node of their own: this player follows their grades only.")
                .onSelect(n -> {
                    nodeField.setValue(n.node());
                    rebuild();
                });
        this.trackList = new CpList<TrackRow>(Component.literal("Tracks"), 14)
                .renderer(this::renderTrack)
                .label(t -> t.track().name() + ", " + (t.rung() < 0 ? "not on it" : "on " + t.track().grades().get(t.rung())))
                .identity(t -> t.track().name())
                .emptyText("No track defined: create one with /customperm track create.")
                .onSelect(t -> rebuild());
        refilter();
        fillDetails();
    }

    @Override
    public GuiPage page() {
        return GuiPage.PLAYERS;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.USER;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (PlayersData) newData;
        // The pending row is dropped once the server knows that player: the real row replaces it.
        if (pendingPlayer != null && findByName(pendingPlayer) != null) pendingPlayer = null;
        refilter();
        if (pendingPlayer != null) playerList.selectByKey("");
        fillDetails();
    }

    @Override
    protected Banner banner() {
        boolean internalFallback = "internal".equalsIgnoreCase(data.fallbackMode());
        return switch (context.backend()) {
            case INTERNAL -> null;
            case LUCKPERMS -> new Banner(Icon.INFO, "Not active: LuckPerms decides permissions, these nodes are "
                    + "read-only. " + (internalFallback
                    ? "They take over if LuckPerms becomes unavailable (luckPermsFallbackMode=internal)."
                    : "They are not used even if LuckPerms fails (luckPermsFallbackMode=" + data.fallbackMode() + ")."),
                    Palette.INFO);
            case INTERNAL_FALLBACK -> new Banner(Icon.WARN, "Active as a fallback: LuckPerms is unavailable, these "
                    + "nodes and the grades decide permissions until the server restarts.", Palette.WARN);
            case DENY -> new Banner(Icon.WARN, "Not active: LuckPerms is unavailable and luckPermsFallbackMode=deny, "
                    + "so every permission CustomPerm manages is denied until restart.", Palette.DANGER);
        };
    }

    private boolean editable() {
        return canEdit(GuiArea.GRADES) && !context.luckPermsActive();
    }

    private PlayersData.Player findByName(String name) {
        return data.players().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /** The rows of the list: the server's players, plus the pending one while it holds nothing. */
    private List<PlayersData.Player> rows() {
        List<PlayersData.Player> rows = new ArrayList<>();
        if (pendingPlayer != null) {
            rows.add(new PlayersData.Player("", pendingPlayer, false,
                    PlayersData.Held.NONE, List.of(), List.of()));
        }
        rows.addAll(data.players());
        return rows;
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        PlayersData.Player before = playerList.getSelected();
        playerList.setItems(rows().stream()
                .filter(p -> query.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(query))
                .toList());
        if (layout != null && !Objects.equals(before == null ? null : before.uuid(),
                playerList.getSelected() == null ? null : playerList.getSelected().uuid())) {
            fillDetails();
            rebuild();
        }
    }

    private void fillDetails() {
        PlayersData.Player player = playerList.getSelected();
        List<NodeRow> nodes = new ArrayList<>();
        if (player != null) {
            player.deny().forEach(n -> nodes.add(new NodeRow(n, true, "")));
            player.allow().forEach(n -> nodes.add(new NodeRow(n, false, "")));
            player.scoped().stream().filter(e -> !e.kind().equals("grade"))
                    .forEach(e -> nodes.add(new NodeRow(e.value(), e.deny(), e.context())));
        }
        nodeList.setItems(nodes);
        List<TrackRow> tracks = new ArrayList<>();
        if (player != null) {
            for (PlayersData.Track track : data.tracks()) {
                int rung = -1;
                for (int i = 0; i < track.grades().size(); i++) {
                    if (player.grades().contains(track.grades().get(i))) {
                        rung = i;
                        break;
                    }
                }
                tracks.add(new TrackRow(track, rung));
            }
        }
        trackList.setItems(tracks);
        chat.fill(player == null ? List.of() : player.chat());
    }

    /** Shows the rest of the first known player name that starts with what was typed. */
    private void suggestPlayer(String typed) {
        String completion = completion(typed);
        newPlayer.setSuggestion(completion == null ? null : completion.substring(typed.length()));
    }

    private String completion(String typed) {
        if (typed.isEmpty()) return null;
        String lower = typed.toLowerCase(Locale.ROOT);
        return data.knownPlayers().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower) && name.length() > typed.length())
                .findFirst()
                .map(name -> typed + name.substring(typed.length()))
                .orElse(null);
    }

    // ------------------------------------------------------------------ layout

    private Rect left() {
        Rect c = layout.content();
        return c.left(c.w() * 34 / 100);
    }

    private Rect inner() {
        return layout.content().afterLeft(left().w() + GAP).inset(8);
    }

    private Rect tabRow() {
        Rect in = inner();
        return new Rect(in.x(), in.y() + 24, in.w(), FIELD);
    }

    private Rect listArea() {
        Rect in = inner();
        int top = in.y() + 24 + FIELD + 4;
        int bottom = in.bottom() - (FIELD + 4 + BUTTON + 4);
        return new Rect(in.x(), top, in.w(), bottom - top);
    }

    @Override
    protected void buildPage() {
        boolean editable = editable();
        Rect left = left();
        addRenderableWidget(search.at(left.top(FIELD)));
        Rect trackRow = left.bottom(FIELD);
        addRenderableWidget(playerList.at(new Rect(left.x(), left.y() + FIELD + 4, left.w(),
                trackRow.y() - 4 - left.y() - FIELD - 4)));
        CpButton track = CpButton.accent(Component.literal("Name a player"), this::track).iconOnly(Icon.PLUS)
                .enabled(editable)
                .tooltip(Component.literal("Select a player who holds nothing yet, to give them a first node."));
        addRenderableWidget(newPlayer.at(trackRow.beforeRight(FIELD + 4)));
        addRenderableWidget(track.at(trackRow.right(FIELD)));
        newPlayer.setEditable(editable);

        PlayersData.Player player = playerList.getSelected();
        if (player == null) return;
        Rect in = inner();
        Rect list = listArea();
        placeButtonRow(tabRow(), 8, false, List.of(
                CpButton.ghost(Component.literal("Nodes (" + (player.allow().size() + player.deny().size()) + ")"),
                        () -> setTab(Tab.NODES)).icon(Icon.LOCK).selected(tab == Tab.NODES),
                CpButton.ghost(Component.literal("Chat"), () -> setTab(Tab.CHAT)).icon(Icon.EDIT).selected(tab == Tab.CHAT),
                CpButton.ghost(Component.literal("Tracks (" + data.tracks().size() + ")"), () -> setTab(Tab.TRACKS))
                        .icon(Icon.SHIELD).selected(tab == Tab.TRACKS)));
        Rect fieldRow = new Rect(in.x(), list.bottom() + 4, in.w(), FIELD);
        Rect buttonRow = new Rect(in.x(), fieldRow.bottom() + 4, in.w(), BUTTON);
        if (tab == Tab.TRACKS) {
            addRenderableWidget(trackList.at(new Rect(list.x(), list.y(), list.w(), fieldRow.bottom() - list.y())));
            TrackRow selected = trackList.getSelected();
            boolean movable = editable && selected != null && !selected.track().grades().isEmpty();
            placeButtonRow(buttonRow, 6, true, List.of(
                    CpButton.good(Component.literal("Promote"), () -> move(selected, true)).icon(Icon.PLUS)
                            .enabled(movable && selected.rung() < selected.track().grades().size() - 1)
                            .tooltip(Component.literal("One rung up: the grade they stand on is replaced by the next. "
                                    + "On no rung, they get the first one.")),
                    CpButton.neutral(Component.literal("Demote"), () -> move(selected, false)).icon(Icon.MINUS)
                            .enabled(movable && selected.rung() >= 0)
                            .tooltip(Component.literal("One rung down; from the first rung, off the track."))));
            return;
        }
        if (tab == Tab.CHAT) {
            addRenderableWidget(chat.list.at(chat.listRect(list)));
            addRenderableWidget(chat.text.at(chat.textRect(fieldRow)));
            addRenderableWidget(chat.priority.at(chat.priorityRect(fieldRow)));
            addRenderableWidget(chat.duration.at(chat.durationRect(fieldRow)));
            addRenderableWidget(chat.world.at(chat.worldRect(fieldRow)));
            chat.setEditable(editable);
            placeButtonRow(buttonRow, 6, true, List.of(
                    CpButton.accent(Component.literal("Prefix"), () -> addChat(false)).icon(Icon.PLUS).enabled(editable)
                            .tooltip(Component.literal("Their own, at that priority. At equal priority it shows before "
                                    + "any grade's; a grade's at a higher priority still shows first.")),
                    CpButton.accent(Component.literal("Suffix"), () -> addChat(true)).icon(Icon.PLUS).enabled(editable),
                    CpButton.neutral(Component.literal("Remove"), this::removeChat).icon(Icon.MINUS)
                            .enabled(editable && chat.list.getSelected() != null)
                            .tooltip(Component.literal("Their grades' prefixes still apply."))));
            return;
        }
        addRenderableWidget(nodeList.at(list));
        addRenderableWidget(nodeField.at(fieldRow.beforeRight(DURATION_FIELD + GradesScreen.WORLD_FIELD + 8)));
        addRenderableWidget(worldField.at(fieldRow.right(DURATION_FIELD + GradesScreen.WORLD_FIELD + 4)
                .left(GradesScreen.WORLD_FIELD)));
        addRenderableWidget(durationField.at(fieldRow.right(DURATION_FIELD)));
        nodeField.setEditable(editable);
        worldField.setEditable(editable);
        durationField.setEditable(editable);

        NodeRow selected = nodeList.getSelected();
        placeButtonRow(buttonRow, 6, true, List.of(
                CpButton.good(Component.literal("Allow"), () -> addNode(false)).icon(Icon.CHECK).enabled(editable),
                CpButton.danger(Component.literal("Deny"), () -> addNode(true)).icon(Icon.CROSS).enabled(editable)
                        .tooltip(Component.literal("Refused to this player, operators included, unless a more specific "
                                + "node allows it. Their own nodes win over their grades at the same level.")),
                CpButton.neutral(Component.literal("Remove"), () -> removeNode(selected)).icon(Icon.MINUS)
                        .enabled(editable && selected != null && !player.uuid().isEmpty())));
    }

    private void setTab(Tab wanted) {
        tab = wanted;
        rebuild();
    }

    /** By name, like adding a node: a player who holds nothing yet can be put on a first rung. */
    private void move(TrackRow row, boolean up) {
        PlayersData.Player player = playerList.getSelected();
        if (player == null || row == null) return;
        act(up ? GuiAction.TRACK_PROMOTE : GuiAction.TRACK_DEMOTE, player.name(), row.track().name());
    }

    /** {@code staff   member > [vip] > admin}: the ladder, the player's rung bracketed. */
    private void renderTrack(GuiGraphics g, Font font, TrackRow row, Rect r, boolean hovered, boolean selected) {
        int y = r.y() + (r.h() - 8) / 2;
        String name = row.track().name();
        int nw = Math.min(font.width(name), r.w() / 3);
        Skin.text(g, font, name, r.x() + 4, y, nw, Palette.TEXT);
        List<String> rungs = new ArrayList<>();
        for (int i = 0; i < row.track().grades().size(); i++) {
            String grade = row.track().grades().get(i);
            rungs.add(i == row.rung() ? "[" + grade + "]" : grade);
        }
        String ladder = rungs.isEmpty() ? "no grade yet" : String.join(" > ", rungs);
        int x = r.x() + 4 + nw + 10;
        Skin.text(g, font, ladder, x, y, r.right() - x - 4, row.rung() >= 0 ? Palette.ACCENT_HI : Palette.TEXT_MUTE);
    }

    // ------------------------------------------------------------------ actions

    /** By name like a node, so a player who holds nothing yet can get a prefix. */
    private void addChat(boolean suffix) {
        PlayersData.Player player = playerList.getSelected();
        String text = chat.text.getValue();
        if (player == null || text.isEmpty()) return;
        Integer priority = chat.typedPriority();
        if (priority == null) {
            status("A priority is a whole number: the highest shows first.", false);
            return;
        }
        act(GuiAction.USER_CHAT_ADD, player.name(), suffix ? "suffix" : "prefix", String.valueOf(priority), text,
                chat.duration.getValue().trim(), GradesScreen.context(chat.world.getValue()));
        chat.clearTyped();
    }

    private void removeChat() {
        PlayersData.Player player = playerList.getSelected();
        com.arcadia.customperm.network.gui.ChatLine line = chat.list.getSelected();
        if (player == null || line == null) return;
        act(GuiAction.USER_CHAT_REMOVE, player.name(), line.suffix() ? "suffix" : "prefix", String.valueOf(line.priority()),
                line.context());
    }

    /** Selects a player by name, adding a local row when the server does not know them yet. */
    private void track() {
        String typed = newPlayer.getValue().trim();
        if (typed.isEmpty()) return;
        String exact = data.knownPlayers().stream().filter(n -> n.equalsIgnoreCase(typed)).findFirst().orElse(null);
        String name = exact != null ? exact : Objects.requireNonNullElse(completion(typed), typed);
        PlayersData.Player known = findByName(name);
        pendingPlayer = known == null ? name : null;
        refilter();
        playerList.selectByKey(known == null ? "" : known.uuid());
        newPlayer.setValue("");
        newPlayer.setSuggestion(null);
        fillDetails();
        rebuild();
    }

    private void addNode(boolean deny) {
        PlayersData.Player player = playerList.getSelected();
        String node = nodeField.getValue().trim();
        if (player == null || node.isEmpty()) return;
        // Adding addresses the player by name: a pending row has no UUID yet, the server resolves it.
        act(GuiAction.USER_NODE_ADD, player.name(), node, deny ? "deny" : "allow", durationField.getValue().trim(),
                GradesScreen.context(worldField.getValue()));
        nodeField.setValue("");
    }

    private void removeNode(NodeRow row) {
        PlayersData.Player player = playerList.getSelected();
        if (player == null || row == null || player.uuid().isEmpty()) return;
        act(GuiAction.USER_NODE_REMOVE, player.uuid(), row.node(), row.deny() ? "deny" : "allow", row.context());
        nodeField.setValue("");
    }

    // ------------------------------------------------------------------ rendering

    private void renderPlayer(GuiGraphics g, Font font, PlayersData.Player player, Rect r, boolean hovered,
                              boolean selected) {
        Skin.dot(g, r.x() + 6, r.centerY(), player.online() ? Palette.GOOD : Palette.LINE_STRONG);
        int x = r.x() + 6 + Atlas.DOT_SIZE + 5;
        int own = player.allow().size() + player.deny().size()
                + (int) player.scoped().stream().filter(e -> !e.kind().equals("grade")).count();
        String count = own == 0 ? "" : String.valueOf(own);
        int cw = count.isEmpty() ? 0 : font.width(count) + 14;
        if (!count.isEmpty()) {
            Skin.icon(g, Icon.LOCK, r.right() - cw, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
            Skin.text(g, font, count, r.right() - cw + 10, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        }
        Skin.text(g, font, player.name(), x, r.y() + (r.h() - 8) / 2, r.right() - x - cw - 4, Palette.TEXT);
    }

    private void renderNode(GuiGraphics g, Font font, NodeRow row, Rect r, boolean hovered, boolean selected) {
        String label = row.deny() ? "DENY" : "ALLOW";
        int w = Skin.badge(g, font, label, r.x() + 4, r.centerY(), row.deny() ? Palette.DANGER : Palette.GOOD);
        int x = r.x() + 4 + Math.max(w, font.width("ALLOW") + 6) + 5;
        PlayersData.Player player = playerList.getSelected();
        String left = !row.context().isEmpty() ? GradesScreen.where(row.context())
                : player == null ? "" : GradesScreen.timeLeft(player.remaining(row.deny() ? "deny" : "allow", row.node()));
        int lw = left.isEmpty() ? 0 : font.width(left) + 8;
        if (!left.isEmpty()) Skin.text(g, font, left, r.right() - lw + 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, row.node(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4 - lw, Palette.TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect in = inner();
        Skin.panel(g, in.inset(-8));
        PlayersData.Player player = playerList.getSelected();
        if (player == null) {
            paragraph(g, "Select a player to edit the nodes they carry themselves. Such a node wins over their "
                    + "grades at the same level, whatever a grade weighs, but a more specific grade node still "
                    + "wins. Grades themselves are edited on the Grades page.", in, in.y(), Palette.TEXT_MUTE);
            if (!canEdit(GuiArea.GRADES)) {
                paragraph(g, "Read-only: editing these needs " + GuiArea.GRADES.node() + ".", in, in.y() + 44,
                        Palette.TEXT_MUTE);
            }
            return;
        }
        Skin.text(g, font, player.name(), in.x(), in.y(), in.w(), Palette.TEXT);
        // Grades and refusals first: they are said nowhere else on this page, while the node counts are the
        // list right below. A narrow panel then clips the counts rather than the refusals.
        List<String> held = new ArrayList<>(player.grades());
        player.scoped().stream().filter(e -> e.kind().equals("grade"))
                .forEach(e -> held.add(e.value() + " (" + GradesScreen.where(e.context()) + ")"));
        String sub = (held.isEmpty() ? "no grade" : "grades: " + String.join(", ", held))
                + (player.refused().isEmpty() ? "" : "  |  refuses: " + String.join(", ", player.refused()))
                + "  |  " + player.allow().size() + " allow, " + player.deny().size() + " deny"
                + (canEdit(GuiArea.GRADES) ? "" : "  |  read-only: needs " + GuiArea.GRADES.node());
        Skin.text(g, font, sub, in.x(), in.y() + 11, in.w(), Palette.TEXT_MUTE);
        if (tab == Tab.CHAT) chat.renderPreview(g, font, listArea(), player.name(), data.names());
    }
}
