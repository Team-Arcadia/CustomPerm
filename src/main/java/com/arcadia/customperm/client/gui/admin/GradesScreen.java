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
import com.arcadia.customperm.network.gui.GradesData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiCodecs;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Internal grade editor. Left: grades and creation. Right: the selected grade, on three tabs: its
 * permission nodes (ALLOW or DENY, the most specific entry winning), the grades it inherits, and its
 * players, who can be assigned while offline as long as they joined the server before. One grade can be
 * the default grade, applied to every player below their own grades, and it can inherit other grades,
 * whose entries apply where it says nothing as precise. The header carries the weight, which breaks a tie between two grades covering a node
 * just as specifically; the list is ordered by it.
 *
 * <p>The page is always reachable, so the fallback grades can be read while LuckPerms runs or fails.
 * A banner says whether grades currently decide permissions; while LuckPerms is the active backend the
 * page is read-only, like the grade commands.
 */
public final class GradesScreen extends AdminScreen {

    private static final int ROW = 16;
    private static final int FIELD = Atlas.INPUT_HEIGHT;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;
    /** Width of the weight box in the header: enough for a minus sign and four digits. */
    private static final int WEIGHT_FIELD = 40;

    /** One node of the selected grade. */
    private record NodeRow(String node, boolean deny) {
    }

    /** Right-hand side of the page: what the selected grade is looked at through. */
    private enum Tab { NODES, PARENTS, PLAYERS }

    private GradesData data;
    private Tab tab = Tab.NODES;

    private final CpEditBox search;
    private final CpList<GradesData.Grade> gradeList;
    private final CpEditBox newGrade;
    private final CpList<NodeRow> nodeList;
    private final CpEditBox nodeField;
    private final CpList<String> parentList;
    private final CpEditBox parentField;
    private final CpList<GradesData.Member> memberList;
    private final CpEditBox playerField;
    private final CpEditBox weightField;
    /** Grade to select once the next refresh lands, after creating it. */
    private String pendingGrade;

    public GradesScreen(GuiContext context, GradesData data) {
        super(Component.literal("Grades"), context);
        this.data = data;
        this.nodeField = new CpEditBox(Component.literal("Permission node"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("permission node"))
                .onSubmit(() -> addNode(false));
        this.playerField = new CpEditBox(Component.literal("Player name"), 16)
                .hint(Component.literal("player name"))
                .onSubmit(this::assign);
        playerField.onChange(this::suggestPlayer);
        this.weightField = new CpEditBox(Component.literal("Grade weight"), 7)
                .hint(Component.literal("weight"))
                .onSubmit(this::applyWeight);
        this.search = new CpEditBox(Component.literal("Search grades"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .onChange(text -> refilter());
        this.gradeList = new CpList<GradesData.Grade>(Component.literal("Grades"), ROW)
                .renderer(this::renderGrade)
                .label(g -> g.name() + ", weight " + g.weight() + ", " + g.allow().size() + " allowed, "
                        + g.deny().size() + " denied, " + g.members().size() + " players")
                .identity(GradesData.Grade::name)
                .emptyText("No grade yet: create one below.")
                .onSelect(g -> {
                    fillDetails();
                    rebuild();
                });
        this.newGrade = new CpEditBox(Component.literal("New grade name"), 64)
                .hint(Component.literal("new grade"))
                .onSubmit(this::create);
        this.nodeList = new CpList<NodeRow>(Component.literal("Nodes"), 14)
                .renderer(this::renderNode)
                .label(n -> (n.deny() ? "denied " : "allowed ") + n.node())
                .identity(n -> (n.deny() ? "deny:" : "allow:") + n.node())
                .emptyText("No node: this grade grants nothing yet.")
                .onSelect(n -> {
                    nodeField.setValue(n.node());
                    rebuild();
                });
        this.parentField = new CpEditBox(Component.literal("Parent grade"), 64)
                .hint(Component.literal("grade to inherit"))
                .onSubmit(this::addParent);
        parentField.onChange(this::suggestParent);
        this.parentList = new CpList<String>(Component.literal("Parents"), 14)
                .renderer(this::renderParent)
                .label(name -> "inherits " + name)
                .identity(name -> name)
                .emptyText("No parent: this grade inherits nothing.")
                .onSelect(name -> {
                    parentField.setValue(name);
                    rebuild();
                });
        this.memberList = new CpList<GradesData.Member>(Component.literal("Players"), 14)
                .renderer(this::renderMember)
                .label(m -> m.name() + (m.online() ? ", online" : ", offline"))
                .identity(GradesData.Member::uuid)
                .emptyText("No player has this grade.")
                .onSelect(m -> rebuild());
        refilter();
        fillDetails();
    }

    @Override
    public GuiPage page() {
        return GuiPage.GRADES;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.SHIELD;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (GradesData) newData;
        refilter();
        if (pendingGrade != null) gradeList.selectByKey(pendingGrade);
        pendingGrade = null;
        fillDetails();
    }

    @Override
    protected Banner banner() {
        boolean internalFallback = "internal".equalsIgnoreCase(data.fallbackMode());
        return switch (context.backend()) {
            case INTERNAL -> !data.defaultGrade().isEmpty() && !data.gateAll()
                    ? new Banner(Icon.INFO, "Default grade " + data.defaultGrade() + " only restricts exposed commands and "
                    + "aliases: turn on Gate all on the Commands page to cover every command.", Palette.INFO)
                    : null;
            case LUCKPERMS -> new Banner(Icon.INFO, "Not active: LuckPerms decides permissions, these grades are read-only. "
                    + (internalFallback
                    ? "They take over if LuckPerms becomes unavailable (luckPermsFallbackMode=internal)."
                    : "They are not used even if LuckPerms fails (luckPermsFallbackMode=" + data.fallbackMode() + ")."),
                    Palette.INFO);
            case INTERNAL_FALLBACK -> new Banner(Icon.WARN, "Active as a fallback: LuckPerms is unavailable, these grades "
                    + "decide permissions until the server restarts.", Palette.WARN);
            case DENY -> new Banner(Icon.WARN, "Not active: LuckPerms is unavailable and luckPermsFallbackMode=deny, so every "
                    + "permission CustomPerm manages is denied until restart. Grades stay editable for luckPermsFallbackMode=internal.",
                    Palette.DANGER);
        };
    }

    private boolean editable() {
        return canEdit(GuiArea.GRADES) && !context.luckPermsActive();
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        GradesData.Grade before = gradeList.getSelected();
        gradeList.setItems(data.grades().stream()
                .filter(g -> query.isEmpty() || g.name().toLowerCase(Locale.ROOT).contains(query))
                .toList());
        if (layout != null && !Objects.equals(before == null ? null : before.name(),
                gradeList.getSelected() == null ? null : gradeList.getSelected().name())) {
            fillDetails();
            rebuild();
        }
    }

    private void fillDetails() {
        GradesData.Grade grade = gradeList.getSelected();
        List<NodeRow> nodes = new ArrayList<>();
        if (grade != null) {
            grade.deny().forEach(n -> nodes.add(new NodeRow(n, true)));
            grade.allow().forEach(n -> nodes.add(new NodeRow(n, false)));
        }
        nodeList.setItems(nodes);
        parentList.setItems(grade == null ? List.of() : grade.parents());
        memberList.setItems(grade == null ? List.of() : grade.members());
        // The box shows the weight in force, so submitting it unchanged is a no-op rather than a reset.
        weightField.setValue(grade == null ? "" : String.valueOf(grade.weight()));
    }

    /** Shows the rest of the first known player name that starts with what was typed. */
    private void suggestPlayer(String typed) {
        String completion = completion(typed);
        playerField.setSuggestion(completion == null ? null : completion.substring(typed.length()));
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

    /** Area between the tab row and the editing rows. */
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
        Rect createRow = left.bottom(FIELD);
        addRenderableWidget(gradeList.at(new Rect(left.x(), left.y() + FIELD + 4, left.w(),
                createRow.y() - 4 - left.y() - FIELD - 4)));
        CpButton create = CpButton.accent(Component.literal("Create grade"), this::create).iconOnly(Icon.PLUS)
                .enabled(editable);
        addRenderableWidget(newGrade.at(createRow.beforeRight(FIELD + 4)));
        addRenderableWidget(create.at(createRow.right(FIELD)));
        newGrade.setEditable(editable);

        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null) return;
        Rect in = inner();

        CpButton delete = CpButton.danger(Component.literal("Delete grade"), () -> confirmDelete(grade))
                .iconOnly(Icon.TRASH).enabled(editable);
        addRenderableWidget(delete.at(new Rect(in.right() - FIELD, in.y(), FIELD, FIELD)));
        boolean isDefault = grade.name().equals(data.defaultGrade());
        CpButton makeDefault = CpButton.ghost(Component.literal("Default"), () -> toggleDefault(grade))
                .icon(isDefault ? Icon.CHECK : Icon.USER)
                .selected(isDefault)
                .enabled(editable)
                .tooltip(Component.literal(isDefault
                        ? "Applies to every player, below their own grades. Click to stop."
                        : "Apply this grade to every player, below their own grades, operators included."));
        int defaultW = makeDefault.preferredWidth(font, 6);
        addRenderableWidget(makeDefault.at(new Rect(in.right() - FIELD - 4 - defaultW, in.y(), defaultW, FIELD)));
        addRenderableWidget(weightField.at(new Rect(in.right() - FIELD - 4 - defaultW - 4 - WEIGHT_FIELD, in.y(),
                WEIGHT_FIELD, FIELD)));
        weightField.setEditable(editable);

        Rect tabs = new Rect(in.x(), in.y() + 24, in.w(), FIELD);
        int tabX = tabs.x();
        for (CpButton button : List.of(
                CpButton.ghost(Component.literal("Nodes (" + (grade.allow().size() + grade.deny().size()) + ")"),
                        () -> setTab(Tab.NODES)).icon(Icon.LOCK).selected(tab == Tab.NODES),
                CpButton.ghost(Component.literal("Parents (" + grade.parents().size() + ")"),
                        () -> setTab(Tab.PARENTS)).icon(Icon.SHIELD).selected(tab == Tab.PARENTS),
                CpButton.ghost(Component.literal("Players (" + grade.members().size() + ")"),
                        () -> setTab(Tab.PLAYERS)).icon(Icon.USER).selected(tab == Tab.PLAYERS))) {
            int width = button.preferredWidth(font, 8);
            addRenderableWidget(button.at(new Rect(tabX, tabs.y(), width, FIELD)));
            tabX += width + 4;
        }

        Rect list = listArea();
        Rect fieldRow = new Rect(in.x(), list.bottom() + 4, in.w(), FIELD);
        Rect buttonRow = new Rect(in.x(), fieldRow.bottom() + 4, in.w(), BUTTON);
        if (tab == Tab.NODES) {
            addRenderableWidget(nodeList.at(list));
            addRenderableWidget(nodeField.at(fieldRow));
            nodeField.setEditable(editable);
            NodeRow selected = nodeList.getSelected();
            CpButton allow = CpButton.good(Component.literal("Allow"), () -> addNode(false)).icon(Icon.CHECK).enabled(editable);
            CpButton deny = CpButton.danger(Component.literal("Deny"), () -> addNode(true)).icon(Icon.CROSS).enabled(editable)
                    .tooltip(Component.literal("Refused, operators included, unless a more specific node allows it: "
                            + "deny * and allow one command to open only that command."));
            int allowW = allow.preferredWidth(font, 6);
            addRenderableWidget(allow.at(buttonRow.left(allowW)));
            addRenderableWidget(deny.at(new Rect(buttonRow.x() + allowW + 4, buttonRow.y(), deny.preferredWidth(font, 6), BUTTON)));
            CpButton remove = CpButton.neutral(Component.literal("Remove"), () -> removeNode(selected)).icon(Icon.MINUS)
                    .enabled(editable && selected != null);
            addRenderableWidget(remove.at(buttonRow.right(remove.preferredWidth(font, 6))));
        } else if (tab == Tab.PARENTS) {
            addRenderableWidget(parentList.at(list));
            addRenderableWidget(parentField.at(fieldRow));
            parentField.setEditable(editable);
            String selected = parentList.getSelected();
            CpButton inherit = CpButton.accent(Component.literal("Inherit"), this::addParent).icon(Icon.PLUS)
                    .enabled(editable)
                    .tooltip(Component.literal("What a parent says applies where this grade says nothing as precise "
                            + "about a node. A node set here still wins over the same node inherited."));
            addRenderableWidget(inherit.at(buttonRow.left(inherit.preferredWidth(font, 6))));
            CpButton stop = CpButton.neutral(Component.literal("Remove"), () -> removeParent(selected)).icon(Icon.MINUS)
                    .enabled(editable && selected != null);
            addRenderableWidget(stop.at(buttonRow.right(stop.preferredWidth(font, 6))));
        } else {
            addRenderableWidget(memberList.at(list));
            addRenderableWidget(playerField.at(fieldRow));
            playerField.setEditable(editable);
            GradesData.Member selected = memberList.getSelected();
            CpButton assign = CpButton.accent(Component.literal("Assign"), this::assign).icon(Icon.PLUS).enabled(editable);
            addRenderableWidget(assign.at(buttonRow.left(assign.preferredWidth(font, 6))));
            CpButton unassign = CpButton.neutral(Component.literal("Unassign"), () -> unassign(selected)).icon(Icon.MINUS)
                    .enabled(editable && selected != null);
            addRenderableWidget(unassign.at(buttonRow.right(unassign.preferredWidth(font, 6))));
        }
    }

    private void setTab(Tab wanted) {
        this.tab = wanted;
        rebuild();
    }

    // ------------------------------------------------------------------ actions

    private void create() {
        String name = newGrade.getValue().trim();
        if (name.isEmpty()) return;
        pendingGrade = name;
        act(GuiAction.GRADE_CREATE, name);
        newGrade.setValue("");
    }

    private void confirmDelete(GradesData.Grade grade) {
        confirm("Delete grade " + grade.name(),
                "Its " + (grade.allow().size() + grade.deny().size()) + " node(s) are removed and "
                        + grade.members().size() + " player(s) lose it.",
                "Delete " + grade.name(),
                () -> act(GuiAction.GRADE_DELETE, grade.name()));
    }

    private void toggleDefault(GradesData.Grade grade) {
        if (grade.name().equals(data.defaultGrade())) {
            act(GuiAction.GRADE_DEFAULT, "");
            return;
        }
        confirm("Default grade: " + grade.name(),
                "Every player follows it below their own grades, operators included.",
                "Set as default",
                () -> act(GuiAction.GRADE_DEFAULT, grade.name()));
    }

    /** Submits the weight box. A grade that weighs nothing is the norm, so a blank box means 0. */
    private void applyWeight() {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null) return;
        String typed = weightField.getValue().trim();
        if (typed.isEmpty()) typed = "0";
        try {
            Integer.parseInt(typed);
        } catch (NumberFormatException e) {
            status("A weight is a whole number, negative allowed.", false);
            return;
        }
        act(GuiAction.GRADE_WEIGHT_SET, grade.name(), typed);
    }

    private void addParent(String name) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || name.isEmpty()) return;
        act(GuiAction.GRADE_PARENT_ADD, grade.name(), name);
        parentField.setValue("");
        parentField.setSuggestion(null);
    }

    private void addParent() {
        String typed = parentField.getValue().trim();
        addParent(Objects.requireNonNullElse(parentCompletion(typed), typed));
    }

    private void removeParent(String name) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || name == null) return;
        act(GuiAction.GRADE_PARENT_REMOVE, grade.name(), name);
        parentField.setValue("");
    }

    /** Shows the rest of the first grade name that starts with what was typed, itself excluded. */
    private void suggestParent(String typed) {
        String completion = parentCompletion(typed);
        parentField.setSuggestion(completion == null ? null : completion.substring(typed.length()));
    }

    private String parentCompletion(String typed) {
        if (typed.isEmpty()) return null;
        GradesData.Grade selected = gradeList.getSelected();
        String lower = typed.toLowerCase(Locale.ROOT);
        return data.grades().stream()
                .map(GradesData.Grade::name)
                .filter(name -> selected == null || !name.equals(selected.name()))
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower) && name.length() > typed.length())
                .findFirst()
                .orElse(null);
    }

    private void addNode(boolean deny) {
        GradesData.Grade grade = gradeList.getSelected();
        String node = nodeField.getValue().trim();
        if (grade == null || node.isEmpty()) return;
        act(GuiAction.GRADE_NODE_ADD, grade.name(), node, deny ? "deny" : "allow");
        nodeField.setValue("");
    }

    private void removeNode(NodeRow row) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || row == null) return;
        act(GuiAction.GRADE_NODE_REMOVE, grade.name(), row.node(), row.deny() ? "deny" : "allow");
        nodeField.setValue("");
    }

    private void assign() {
        GradesData.Grade grade = gradeList.getSelected();
        String typed = playerField.getValue().trim();
        if (grade == null || typed.isEmpty()) return;
        String exact = data.knownPlayers().stream().filter(n -> n.equalsIgnoreCase(typed)).findFirst().orElse(null);
        String name = exact != null ? exact : Objects.requireNonNullElse(completion(typed), typed);
        act(GuiAction.GRADE_ASSIGN, name, grade.name());
        playerField.setValue("");
        playerField.setSuggestion(null);
    }

    private void unassign(GradesData.Member member) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || member == null) return;
        act(GuiAction.GRADE_UNASSIGN, member.uuid(), grade.name());
    }

    // ------------------------------------------------------------------ rendering

    private void renderGrade(GuiGraphics g, Font font, GradesData.Grade grade, Rect r, boolean hovered, boolean selected) {
        boolean isDefault = grade.name().equals(data.defaultGrade());
        String count = isDefault ? "all" : String.valueOf(grade.members().size());
        int cw = font.width(count);
        Skin.icon(g, Icon.USER, r.right() - cw - 14, r.y() + (r.h() - 8) / 2, isDefault ? Palette.ACCENT_HI : Palette.TEXT_MUTE);
        Skin.text(g, font, count, r.right() - cw - 4, r.y() + (r.h() - 8) / 2, isDefault ? Palette.ACCENT_HI : Palette.TEXT_MUTE);
        // The list is ordered by weight: show it, or the order looks arbitrary. Zero is the norm, left blank.
        String weight = grade.weight() == 0 ? "" : "w" + grade.weight();
        int ww = weight.isEmpty() ? 0 : font.width(weight) + 6;
        if (!weight.isEmpty()) {
            Skin.text(g, font, weight, r.right() - cw - 14 - ww, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        }
        Skin.text(g, font, grade.name(), r.x() + 6, r.y() + (r.h() - 8) / 2, r.w() - cw - 26 - ww, Palette.TEXT);
    }

    private void renderParent(GuiGraphics g, Font font, String name, Rect r, boolean hovered, boolean selected) {
        Skin.icon(g, Icon.SHIELD, r.x() + 6, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        int x = r.x() + 6 + 12;
        Skin.text(g, font, name, x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT);
    }

    private void renderNode(GuiGraphics g, Font font, NodeRow row, Rect r, boolean hovered, boolean selected) {
        String label = row.deny() ? "DENY" : "ALLOW";
        int w = Skin.badge(g, font, label, r.x() + 4, r.centerY(), row.deny() ? Palette.DANGER : Palette.GOOD);
        int x = r.x() + 4 + Math.max(w, font.width("ALLOW") + 6) + 5;
        Skin.text(g, font, row.node(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT);
    }

    private void renderMember(GuiGraphics g, Font font, GradesData.Member member, Rect r, boolean hovered, boolean selected) {
        Skin.dot(g, r.x() + 6, r.centerY(), member.online() ? Palette.GOOD : Palette.LINE_STRONG);
        int x = r.x() + 6 + Atlas.DOT_SIZE + 5;
        String state = member.online() ? "online" : "offline";
        int sw = font.width(state);
        Skin.text(g, font, state, r.right() - sw - 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, member.name(), x, r.y() + (r.h() - 8) / 2, r.right() - sw - x - 10, Palette.TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect in = inner();
        Skin.panel(g, in.inset(-8));
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null) {
            paragraph(g, "Select a grade to edit its nodes and players. A player can hold several grades: the most specific "
                    + "node wins (exact, then a.b.*, then *), the heaviest grade breaks a tie at the same level, a DENY wins "
                    + "between equal weights, and it applies to operators too.",
                    in, in.y(), Palette.TEXT_MUTE);
            if (!canEdit(GuiArea.GRADES)) {
                paragraph(g, "Read-only: editing grades needs " + GuiArea.GRADES.node() + ".", in, in.y() + 44, Palette.TEXT_MUTE);
            }
            return;
        }
        boolean isDefault = grade.name().equals(data.defaultGrade());
        int headerW = in.w() - FIELD - 10 - font.width("Default") - 26 - WEIGHT_FIELD - 4;
        Skin.text(g, font, grade.name(), in.x(), in.y(), headerW, Palette.TEXT);
        // Player and node totals are on the tabs: keep this line short enough for the Default button beside it.
        String sub = grade.allow().size() + " allow, " + grade.deny().size() + " deny"
                + ", weight " + grade.weight()
                + (grade.parents().isEmpty() ? "" : ", inherits " + String.join(" ", grade.parents()))
                + (isDefault ? ", every player" : "")
                + (canEdit(GuiArea.GRADES) ? "" : "  |  read-only: needs " + GuiArea.GRADES.node());
        Skin.text(g, font, sub, in.x(), in.y() + 11, headerW, Palette.TEXT_MUTE);
    }
}
