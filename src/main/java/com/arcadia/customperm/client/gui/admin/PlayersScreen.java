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

    /** One node of the selected player. */
    private record NodeRow(String node, boolean deny) {
    }

    private PlayersData data;

    private final CpEditBox search;
    private final CpList<PlayersData.Player> playerList;
    private final CpEditBox newPlayer;
    private final CpList<NodeRow> nodeList;
    private final CpEditBox nodeField;
    /** Name typed in the field below the list, shown as a row while that player holds nothing. */
    private String pendingPlayer;

    public PlayersScreen(GuiContext context, PlayersData data) {
        super(Component.literal("Players"), context);
        this.data = data;
        this.nodeField = new CpEditBox(Component.literal("Permission node"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("permission node"))
                .onSubmit(() -> addNode(false));
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
                    new PlayersData.Held(List.of(), List.of()), List.of(), List.of()));
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
            player.deny().forEach(n -> nodes.add(new NodeRow(n, true)));
            player.allow().forEach(n -> nodes.add(new NodeRow(n, false)));
        }
        nodeList.setItems(nodes);
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

    private Rect listArea() {
        Rect in = inner();
        int top = in.y() + 24;
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
        addRenderableWidget(nodeList.at(list));

        Rect fieldRow = new Rect(in.x(), list.bottom() + 4, in.w(), FIELD);
        addRenderableWidget(nodeField.at(fieldRow));
        nodeField.setEditable(editable);

        Rect buttonRow = new Rect(in.x(), fieldRow.bottom() + 4, in.w(), BUTTON);
        NodeRow selected = nodeList.getSelected();
        placeButtonRow(buttonRow, 6, true, List.of(
                CpButton.good(Component.literal("Allow"), () -> addNode(false)).icon(Icon.CHECK).enabled(editable),
                CpButton.danger(Component.literal("Deny"), () -> addNode(true)).icon(Icon.CROSS).enabled(editable)
                        .tooltip(Component.literal("Refused to this player, operators included, unless a more specific "
                                + "node allows it. Their own nodes win over their grades at the same level.")),
                CpButton.neutral(Component.literal("Remove"), () -> removeNode(selected)).icon(Icon.MINUS)
                        .enabled(editable && selected != null && !player.uuid().isEmpty())));
    }

    // ------------------------------------------------------------------ actions

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
        act(GuiAction.USER_NODE_ADD, player.name(), node, deny ? "deny" : "allow");
        nodeField.setValue("");
    }

    private void removeNode(NodeRow row) {
        PlayersData.Player player = playerList.getSelected();
        if (player == null || row == null || player.uuid().isEmpty()) return;
        act(GuiAction.USER_NODE_REMOVE, player.uuid(), row.node(), row.deny() ? "deny" : "allow");
        nodeField.setValue("");
    }

    // ------------------------------------------------------------------ rendering

    private void renderPlayer(GuiGraphics g, Font font, PlayersData.Player player, Rect r, boolean hovered,
                              boolean selected) {
        Skin.dot(g, r.x() + 6, r.centerY(), player.online() ? Palette.GOOD : Palette.LINE_STRONG);
        int x = r.x() + 6 + Atlas.DOT_SIZE + 5;
        int own = player.allow().size() + player.deny().size();
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
        Skin.text(g, font, row.node(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT);
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
        String sub = (player.grades().isEmpty() ? "no grade" : "grades: " + String.join(", ", player.grades()))
                + (player.refused().isEmpty() ? "" : "  |  refuses: " + String.join(", ", player.refused()))
                + "  |  " + player.allow().size() + " allow, " + player.deny().size() + " deny"
                + (canEdit(GuiArea.GRADES) ? "" : "  |  read-only: needs " + GuiArea.GRADES.node());
        Skin.text(g, font, sub, in.x(), in.y() + 11, in.w(), Palette.TEXT_MUTE);
    }
}
