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
 * Internal grade editor. Left: grades and creation. Right: the selected grade, on four tabs: its
 * permission nodes (ALLOW or DENY, the most specific entry winning), the grades it inherits, its
 * players, who can be assigned while offline as long as they joined the server before, and the chat
 * prefix and suffix it gives them. One grade can be
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
    /** Width of the duration box beside a node or a player name: enough for "1d12h" and its hint cut short. */
    private static final int DURATION_FIELD = 64;
    /** Width of the world box beside it: enough for "the_nether". */
    static final int WORLD_FIELD = 72;

    /** One node of the selected grade; {@code context} is empty for a node that applies everywhere. */
    private record NodeRow(String node, boolean deny, String context) {
    }

    /** One grade the selected grade inherits, or refuses to inherit; {@code context} is empty for everywhere. */
    private record ParentRow(String grade, boolean refused, String context) {
    }

    /** One player assigned to the selected grade, or refusing it. */
    private record MemberRow(GradesData.Member member, boolean refused) {
    }

    /** Right-hand side of the page: what the selected grade is looked at through. */
    private enum Tab { NODES, PARENTS, PLAYERS, CHAT }

    private GradesData data;
    private Tab tab = Tab.NODES;

    private final CpEditBox search;
    private final CpList<GradesData.Grade> gradeList;
    private final CpEditBox newGrade;
    private final CpList<NodeRow> nodeList;
    private final CpEditBox nodeField;
    private final CpList<ParentRow> parentList;
    private final CpEditBox parentField;
    private final CpList<MemberRow> memberList;
    private final CpEditBox playerField;
    private final CpEditBox weightField;
    private final ChatFields chat;
    /** How long what is added lasts, shared by the node and player fields; empty for good. */
    private final CpEditBox durationField;
    /** The world what is added is limited to, shared like the duration; empty for everywhere. */
    private final CpEditBox worldField;
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
        this.chat = new ChatFields(this::rebuild);
        this.durationField = new CpEditBox(Component.literal("Duration"), 16)
                .hint(Component.literal("for, e.g. 30d"));
        this.worldField = new CpEditBox(Component.literal("World"), 64)
                .hint(Component.literal("in, e.g. the_nether"));
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
        this.parentList = new CpList<ParentRow>(Component.literal("Parents"), 14)
                .renderer(this::renderParent)
                .label(row -> (row.refused() ? "refuses " : "inherits ") + row.grade())
                .identity(row -> (row.refused() ? "deny:" : "parent:") + row.grade() + "@" + row.context())
                .emptyText("No parent: this grade inherits nothing.")
                .onSelect(row -> {
                    parentField.setValue(row.grade());
                    rebuild();
                });
        this.memberList = new CpList<MemberRow>(Component.literal("Players"), 14)
                .renderer(this::renderMember)
                .label(row -> row.member().name() + (row.refused() ? ", refuses it" : "")
                        + (row.member().online() ? ", online" : ", offline"))
                .identity(row -> (row.refused() ? "deny:" : "member:") + row.member().uuid())
                .emptyText("No player has this grade.")
                .onSelect(row -> rebuild());
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
            grade.deny().forEach(n -> nodes.add(new NodeRow(n, true, "")));
            grade.allow().forEach(n -> nodes.add(new NodeRow(n, false, "")));
            grade.scoped().forEach(e -> nodes.add(new NodeRow(e.value(), e.deny(), e.context())));
        }
        nodeList.setItems(nodes);
        List<ParentRow> parents = new ArrayList<>();
        List<MemberRow> members = new ArrayList<>();
        if (grade != null) {
            grade.parents().forEach(name -> parents.add(new ParentRow(name, false, "")));
            grade.deniedParents().forEach(name -> parents.add(new ParentRow(name, true, "")));
            grade.scoped().stream().filter(e -> e.kind().equals("parent") || e.kind().equals("refused"))
                    .forEach(e -> parents.add(new ParentRow(e.value(), e.kind().equals("refused"), e.context())));
            grade.members().forEach(member -> members.add(new MemberRow(member, false)));
            grade.refusers().forEach(member -> members.add(new MemberRow(member, true)));
        }
        parentList.setItems(parents);
        memberList.setItems(members);
        // The box shows the weight in force, so submitting it unchanged is a no-op rather than a reset.
        weightField.setValue(grade == null ? "" : String.valueOf(grade.weight()));
        chat.fill(grade == null ? List.of() : grade.chat());
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
        // The box shows the weight in force. Set here rather than only when the selection changes: the first
        // selection after the page opens left it empty, and a placed widget must not disagree with the data.
        if (!weightField.isFocused()) weightField.setValue(String.valueOf(grade.weight()));

        Rect tabs = new Rect(in.x(), in.y() + 24, in.w(), FIELD);
        String nodesTab = "Nodes (" + (grade.allow().size() + grade.deny().size()) + ")";
        String parentsTab = "Parents (" + (grade.parents().size() + grade.deniedParents().size()) + ")";
        String playersTab = "Players (" + (grade.members().size() + grade.refusers().size()) + ")";
        String chatTab = "Chat";
        // Four tabs do not always fit. A bare name reads better than a count clipped to "Nodes (", so the
        // counts go before the width is shared; the icons go after, in placeButtonRow.
        if (font.width(nodesTab) + font.width(parentsTab) + font.width(playersTab) + font.width(chatTab)
                + 4 * 16 + 12 > tabs.w()) {
            nodesTab = "Nodes";
            parentsTab = "Parents";
            playersTab = "Players";
        }
        placeButtonRow(tabs, 8, false, List.of(
                CpButton.ghost(Component.literal(nodesTab), () -> setTab(Tab.NODES))
                        .icon(Icon.LOCK).selected(tab == Tab.NODES),
                CpButton.ghost(Component.literal(parentsTab), () -> setTab(Tab.PARENTS))
                        .icon(Icon.SHIELD).selected(tab == Tab.PARENTS),
                CpButton.ghost(Component.literal(playersTab), () -> setTab(Tab.PLAYERS))
                        .icon(Icon.USER).selected(tab == Tab.PLAYERS),
                CpButton.ghost(Component.literal(chatTab), () -> setTab(Tab.CHAT))
                        .icon(Icon.EDIT).selected(tab == Tab.CHAT)));

        Rect list = listArea();
        Rect fieldRow = new Rect(in.x(), list.bottom() + 4, in.w(), FIELD);
        Rect buttonRow = new Rect(in.x(), fieldRow.bottom() + 4, in.w(), BUTTON);
        if (tab == Tab.NODES) {
            addRenderableWidget(nodeList.at(list));
            addRenderableWidget(nodeField.at(fieldRow.beforeRight(DURATION_FIELD + WORLD_FIELD + 8)));
            addRenderableWidget(worldField.at(fieldRow.right(DURATION_FIELD + WORLD_FIELD + 4).left(WORLD_FIELD)));
            addRenderableWidget(durationField.at(fieldRow.right(DURATION_FIELD)));
            nodeField.setEditable(editable);
            worldField.setEditable(editable);
            durationField.setEditable(editable);
            NodeRow selected = nodeList.getSelected();
            placeButtonRow(buttonRow, 6, true, List.of(
                    CpButton.good(Component.literal("Allow"), () -> addNode(false)).icon(Icon.CHECK).enabled(editable),
                    CpButton.danger(Component.literal("Deny"), () -> addNode(true)).icon(Icon.CROSS).enabled(editable)
                            .tooltip(Component.literal("Refused, operators included, unless a more specific node allows "
                                    + "it: deny * and allow one command to open only that command.")),
                    CpButton.neutral(Component.literal("Remove"), () -> removeNode(selected)).icon(Icon.MINUS)
                            .enabled(editable && selected != null)));
        } else if (tab == Tab.PARENTS) {
            addRenderableWidget(parentList.at(list));
            addRenderableWidget(parentField.at(fieldRow.beforeRight(DURATION_FIELD + WORLD_FIELD + 8)));
            addRenderableWidget(worldField.at(fieldRow.right(DURATION_FIELD + WORLD_FIELD + 4).left(WORLD_FIELD)));
            addRenderableWidget(durationField.at(fieldRow.right(DURATION_FIELD)));
            parentField.setEditable(editable);
            worldField.setEditable(editable);
            durationField.setEditable(editable);
            ParentRow selected = parentList.getSelected();
            placeButtonRow(buttonRow, 6, true, List.of(
                    CpButton.accent(Component.literal("Inherit"), this::addParent).icon(Icon.PLUS).enabled(editable)
                            .tooltip(Component.literal("What a parent says applies where this grade says nothing as "
                                    + "precise about a node. A node set here still wins over the same node inherited.")),
                    CpButton.danger(Component.literal("Refuse"), this::refuseParent).icon(Icon.CROSS).enabled(editable)
                            .tooltip(Component.literal("Nothing this grade inherits brings that grade back. It removes "
                                    + "it from this chain only, never from another grade a player holds.")),
                    CpButton.neutral(Component.literal("Remove"), () -> removeParent(selected)).icon(Icon.MINUS)
                            .enabled(editable && selected != null)));
        } else if (tab == Tab.CHAT) {
            addRenderableWidget(chat.list.at(chat.listRect(list)));
            addRenderableWidget(chat.text.at(chat.textRect(fieldRow)));
            addRenderableWidget(chat.priority.at(chat.priorityRect(fieldRow)));
            addRenderableWidget(chat.duration.at(chat.durationRect(fieldRow)));
            addRenderableWidget(chat.world.at(chat.worldRect(fieldRow)));
            chat.setEditable(editable);
            boolean decorate = data.names().decorate();
            boolean stacked = data.names().prefix().stacked();
            placeButtonRow(buttonRow, 6, true, List.of(
                    CpButton.accent(Component.literal("Prefix"), () -> addChat(false)).icon(Icon.PLUS).enabled(editable)
                            .tooltip(Component.literal("Adds the text at that priority, replacing one already there. "
                                    + "The highest priority a player reaches shows first; at equal priority their own, "
                                    + "then the heaviest grade.")),
                    CpButton.accent(Component.literal("Suffix"), () -> addChat(true)).icon(Icon.PLUS).enabled(editable),
                    CpButton.neutral(Component.literal("Remove"), this::removeChat).icon(Icon.MINUS)
                            .enabled(editable && chat.list.getSelected() != null),
                    CpButton.ghost(Component.literal(stacked ? "Stacked" : "Highest"),
                                    () -> act(GuiAction.NAMES_STACK, stacked ? "highest" : "stacked"))
                            .icon(stacked ? Icon.CHECK : Icon.CROSS).selected(stacked)
                            .enabled(canEdit(GuiArea.CONFIG))
                            .tooltip(Component.literal("Highest shows one prefix and one suffix; Stacked shows several "
                                    + "in a row, highest priority first. Spacers and the limit are in settings.json. Needs "
                                    + GuiArea.CONFIG.node() + ".")),
                    CpButton.ghost(Component.literal(decorate ? "Names decorated" : "Names plain"),
                                    () -> act(GuiAction.NAMES_DECORATE, String.valueOf(!decorate)))
                            .icon(decorate ? Icon.CHECK : Icon.CROSS).selected(decorate)
                            .enabled(canEdit(GuiArea.CONFIG))
                            .tooltip(Component.literal("Puts prefixes and suffixes around player names, in chat and "
                                    + "wherever the game shows them. Messages stay signed. Needs "
                                    + GuiArea.CONFIG.node() + "."))));
        } else {
            addRenderableWidget(memberList.at(list));
            addRenderableWidget(playerField.at(fieldRow.beforeRight(DURATION_FIELD + WORLD_FIELD + 8)));
            addRenderableWidget(worldField.at(fieldRow.right(DURATION_FIELD + WORLD_FIELD + 4).left(WORLD_FIELD)));
            addRenderableWidget(durationField.at(fieldRow.right(DURATION_FIELD)));
            playerField.setEditable(editable);
            worldField.setEditable(editable);
            durationField.setEditable(editable);
            MemberRow selected = memberList.getSelected();
            placeButtonRow(buttonRow, 6, true, List.of(
                    CpButton.accent(Component.literal("Assign"), this::assign).icon(Icon.PLUS).enabled(editable),
                    CpButton.danger(Component.literal("Refuse"), this::refuseForPlayer).icon(Icon.CROSS)
                            .enabled(editable)
                            .tooltip(Component.literal("This grade is not read for that player, whichever grade of "
                                    + "theirs would have brought it. Unassign a grade they hold directly instead.")),
                    // One button for the two ways out: a row is either an assignment or a refusal.
                    CpButton.neutral(
                            Component.literal(selected != null && selected.refused() ? "Accept" : "Unassign"),
                            () -> takeBack(selected)).icon(Icon.MINUS).enabled(editable && selected != null)));
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

    /** Sends the text at the typed priority; the text is sent as typed, a trailing space being part of it. */
    private void addChat(boolean suffix) {
        GradesData.Grade grade = gradeList.getSelected();
        String text = chat.text.getValue();
        if (grade == null || text.isEmpty()) return;
        Integer priority = chat.typedPriority();
        if (priority == null) {
            status("A priority is a whole number: the highest shows first.", false);
            return;
        }
        act(GuiAction.GRADE_CHAT_ADD, grade.name(), suffix ? "suffix" : "prefix", String.valueOf(priority), text,
                chat.duration.getValue().trim(), context(chat.world.getValue()));
        chat.clearTyped();
    }

    private void removeChat() {
        GradesData.Grade grade = gradeList.getSelected();
        com.arcadia.customperm.network.gui.ChatLine line = chat.list.getSelected();
        if (grade == null || line == null) return;
        act(GuiAction.GRADE_CHAT_REMOVE, grade.name(), line.suffix() ? "suffix" : "prefix", String.valueOf(line.priority()),
                line.context());
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
        act(GuiAction.GRADE_PARENT_ADD, grade.name(), name, durationField.getValue().trim(), context(worldField.getValue()));
        parentField.setValue("");
        parentField.setSuggestion(null);
    }

    private void addParent() {
        String typed = parentField.getValue().trim();
        addParent(Objects.requireNonNullElse(parentCompletion(typed), typed));
    }

    private void refuseParent() {
        GradesData.Grade grade = gradeList.getSelected();
        String typed = parentField.getValue().trim();
        String name = Objects.requireNonNullElse(parentCompletion(typed), typed);
        if (grade == null || name.isEmpty()) return;
        act(GuiAction.GRADE_PARENT_DENY, grade.name(), name, durationField.getValue().trim(), context(worldField.getValue()));
        parentField.setValue("");
        parentField.setSuggestion(null);
    }

    private void removeParent(ParentRow row) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || row == null) return;
        act(row.refused() ? GuiAction.GRADE_PARENT_ALLOW : GuiAction.GRADE_PARENT_REMOVE, grade.name(), row.grade(),
                row.context());
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
        act(GuiAction.GRADE_NODE_ADD, grade.name(), node, deny ? "deny" : "allow", durationField.getValue().trim(),
                context(worldField.getValue()));
        nodeField.setValue("");
    }

    private void removeNode(NodeRow row) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || row == null) return;
        act(GuiAction.GRADE_NODE_REMOVE, grade.name(), row.node(), row.deny() ? "deny" : "allow", row.context());
        nodeField.setValue("");
    }

    private void assign() {
        GradesData.Grade grade = gradeList.getSelected();
        String name = typedPlayer();
        if (grade == null || name == null) return;
        act(GuiAction.GRADE_ASSIGN, name, grade.name(), durationField.getValue().trim(), context(worldField.getValue()));
        playerField.setValue("");
        playerField.setSuggestion(null);
    }

    /** The player name in the field, completed against the known ones; {@code null} when it is empty. */
    private String typedPlayer() {
        String typed = playerField.getValue().trim();
        if (typed.isEmpty()) return null;
        String exact = data.knownPlayers().stream().filter(n -> n.equalsIgnoreCase(typed)).findFirst().orElse(null);
        return exact != null ? exact : Objects.requireNonNullElse(completion(typed), typed);
    }

    /** Makes the player named in the field refuse this grade, wherever one of theirs would bring it. */
    private void refuseForPlayer() {
        GradesData.Grade grade = gradeList.getSelected();
        String name = typedPlayer();
        if (grade == null || name == null) return;
        act(GuiAction.GRADE_REFUSE, name, grade.name(), durationField.getValue().trim(), context(worldField.getValue()));
        playerField.setValue("");
        playerField.setSuggestion(null);
    }

    private void takeBack(MemberRow row) {
        GradesData.Grade grade = gradeList.getSelected();
        if (grade == null || row == null) return;
        if (row.refused()) {
            act(GuiAction.GRADE_ACCEPT, row.member().uuid(), grade.name(), row.member().context());
        } else {
            act(GuiAction.GRADE_UNASSIGN, row.member().uuid(), grade.name(), row.member().context());
        }
    }

    /** What the world box holds, as a context: {@code the_nether} is {@code world=the_nether}, empty is everywhere. */
    static String context(String typed) {
        String world = typed.trim();
        return world.isEmpty() || world.contains("=") ? world : "world=" + world;
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

    private void renderParent(GuiGraphics g, Font font, ParentRow row, Rect r, boolean hovered, boolean selected) {
        String label = row.refused() ? "REFUSED" : "INHERITS";
        int w = Skin.badge(g, font, label, r.x() + 4, r.centerY(), row.refused() ? Palette.DANGER : Palette.ACCENT);
        int x = r.x() + 4 + Math.max(w, font.width("INHERITS") + 6) + 5;
        GradesData.Grade grade = gradeList.getSelected();
        String left = !row.context().isEmpty() ? where(row.context())
                : grade == null ? "" : timeLeft(grade.remaining(row.refused() ? "refusedParent" : "parent", row.grade()));
        int lw = left.isEmpty() ? 0 : font.width(left) + 8;
        if (!left.isEmpty()) Skin.text(g, font, left, r.right() - lw + 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, row.grade(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4 - lw, Palette.TEXT);
    }

    private void renderNode(GuiGraphics g, Font font, NodeRow row, Rect r, boolean hovered, boolean selected) {
        String label = row.deny() ? "DENY" : "ALLOW";
        int w = Skin.badge(g, font, label, r.x() + 4, r.centerY(), row.deny() ? Palette.DANGER : Palette.GOOD);
        int x = r.x() + 4 + Math.max(w, font.width("ALLOW") + 6) + 5;
        GradesData.Grade grade = gradeList.getSelected();
        String left = !row.context().isEmpty() ? where(row.context())
                : grade == null ? "" : timeLeft(grade.remaining(row.deny() ? "deny" : "allow", row.node()));
        int lw = left.isEmpty() ? 0 : font.width(left) + 8;
        if (!left.isEmpty()) Skin.text(g, font, left, r.right() - lw + 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, row.node(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4 - lw, Palette.TEXT);
    }

    /** {@code in the_nether}, or nothing for an entry that applies everywhere. */
    static String where(String context) {
        return context.isEmpty() ? "" : "in " + com.arcadia.customperm.perm.Contexts.describe(context);
    }

    /** {@code 29d 23h left}, or nothing for a permanent entry. */
    static String timeLeft(long seconds) {
        return seconds > 0 ? com.arcadia.customperm.perm.Expiry.describe(seconds) + " left" : "";
    }

    private void renderMember(GuiGraphics g, Font font, MemberRow row, Rect r, boolean hovered, boolean selected) {
        GradesData.Member member = row.member();
        Skin.dot(g, r.x() + 6, r.centerY(), member.online() ? Palette.GOOD : Palette.LINE_STRONG);
        int x = r.x() + 6 + Atlas.DOT_SIZE + 5;
        String state = (row.refused() ? "refuses it" : member.online() ? "online" : "offline")
                + (member.remaining() > 0 ? ", " + timeLeft(member.remaining()) : "")
                + (member.context().isEmpty() ? "" : ", " + where(member.context()));
        int sw = font.width(state);
        Skin.text(g, font, state, r.right() - sw - 4, r.y() + (r.h() - 8) / 2,
                row.refused() ? Palette.DANGER : Palette.TEXT_MUTE);
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
        // Ordered by what is said nowhere else on the page: the weight has its own box and the counts are
        // on the tabs, so they go last, where a narrow panel clips them.
        String sub = (isDefault ? "every player, " : "")
                + (grade.parents().isEmpty() ? "" : "inherits " + String.join(" ", grade.parents()) + ", ")
                + (grade.deniedParents().isEmpty() ? "" : "refuses " + String.join(" ", grade.deniedParents()) + ", ")
                + grade.allow().size() + " allow, " + grade.deny().size() + " deny"
                + (canEdit(GuiArea.GRADES) ? "" : "  |  read-only: needs " + GuiArea.GRADES.node());
        Skin.text(g, font, sub, in.x(), in.y() + 11, headerW, Palette.TEXT_MUTE);
        if (tab == Tab.CHAT) chat.renderPreview(g, font, listArea(), previewName(), data.names());
    }

    /** The admin's own name in the preview: it reads as a real line rather than a template. */
    private String previewName() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "Steve";
    }
}
