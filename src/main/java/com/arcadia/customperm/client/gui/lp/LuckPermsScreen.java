/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.lp;

import com.arcadia.customperm.client.gui.admin.AdminScreen;
import com.arcadia.customperm.client.gui.admin.AdminScreens;
import com.arcadia.customperm.client.gui.kit.Atlas;
import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.LuckPermsData;
import com.arcadia.customperm.network.lp.LpDto;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.LpEditPayload;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * In-game LuckPerms editor, drawn natively over the existing editor channel: the list of the current
 * section on the left, the selected group, player or track on the right.
 *
 * <p><strong>Data flow.</strong> Nothing here reads LuckPerms. The screen requests one list scope
 * ({@code groups}, {@code users:<search>}, {@code tracks}) and at most one detail scope
 * ({@code group:<name>}, {@code user:<uuid>}); replies are matched by scope, so a late reply for a
 * section the admin left is ignored. An edit names the scope to refresh, and a successful edit also
 * reloads the list, whose counts may have changed.
 *
 * <p>Only reachable while LuckPerms is the active backend: the server refuses to open it otherwise and
 * the navigation has no entry for it.
 */
public final class LuckPermsScreen extends AdminScreen implements AdminScreens.LpSnapshotConsumer {

    private enum Section { GROUPS, PLAYERS, TRACKS }

    private enum Tab { NODES, PARENTS, META }

    private static final int ROW = 16;
    private static final int SMALL_ROW = 14;
    private static final int FIELD = Atlas.INPUT_HEIGHT;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;
    private static final int LABEL = 44;
    private static final String DEFAULT_PRIORITY = "100";

    private Section section;
    private Tab tab = Tab.NODES;
    private boolean requested;
    /** Whether the status line shows "Loading...", so a reply only clears that and never an edit result. */
    private boolean loadingShown;

    private LpDto.Snapshot list = LpDto.Snapshot.EMPTY;
    private LpDto.Snapshot detail = LpDto.Snapshot.EMPTY;
    private String selectedGroup;
    private String selectedUser;
    private String selectedTrack;

    // Widgets kept across rebuilds.
    private final CpList<LpDto.GroupDto> groupList;
    private final CpList<LpDto.UserDto> userList;
    private final CpList<LpDto.TrackDto> trackList;
    private final CpEditBox createField;
    private final CpEditBox searchField;
    private final CpList<LpDto.NodeDto> nodeList;
    private final CpList<String> parentList;
    private final CpList<String> trackGroups;
    private final CpEditBox nodeKey = field("permission node", 256);
    private final CpEditBox nodeContexts = field("contexts, e.g. world=nether", 256);
    private final CpEditBox nodeDuration = field("duration: 30m, 7d", 16);
    private final CpEditBox groupField = field("group", 64);
    private final CpEditBox groupDuration = field("duration", 16);
    private final CpEditBox trackField = field("track", 64);
    private final CpEditBox weightField = field("weight", 9);
    private final CpEditBox displayField = field("display name", 64);
    private final CpEditBox prefixPriority = field("prio", 9);
    private final CpEditBox prefixValue = field("prefix", 128);
    private final CpEditBox suffixPriority = field("prio", 9);
    private final CpEditBox suffixValue = field("suffix", 128);
    private final CpEditBox metaKey = field("key", 64);
    private final CpEditBox metaValue = field("value", 128);
    private final CpEditBox positionField = field("pos", 4);

    public LuckPermsScreen(GuiContext context, LuckPermsData data) {
        super(Component.literal("LuckPerms editor"), context);
        this.section = sectionOf(data.section());
        this.groupList = new CpList<LpDto.GroupDto>(Component.literal("Groups"), ROW)
                .renderer(this::renderGroup)
                .label(g -> g.name() + ", weight " + LpFormat.weight(g.weight()) + ", " + g.nodeCount() + " nodes")
                .identity(LpDto.GroupDto::name)
                .emptyText("No group.")
                .onSelect(g -> selectDetail(g.name()));
        this.userList = new CpList<LpDto.UserDto>(Component.literal("Players"), ROW)
                .renderer(this::renderUser)
                .label(u -> u.username() + (u.online() ? ", online" : ", offline") + ", primary group " + u.primaryGroup())
                .identity(LpDto.UserDto::uuid)
                .emptyText("No player found. Search an exact name to load an offline player.")
                .onSelect(u -> selectDetail(u.uuid()));
        this.trackList = new CpList<LpDto.TrackDto>(Component.literal("Tracks"), ROW)
                .renderer(this::renderTrack)
                .label(t -> t.name() + ", " + t.groups().size() + " groups")
                .identity(LpDto.TrackDto::name)
                .emptyText("No track.")
                .onSelect(t -> {
                    selectedTrack = t.name();
                    fillTrackGroups();
                    rebuild();
                });
        this.createField = field("new name", 64).onSubmit(this::create);
        this.searchField = field("player name, Enter to search", 16).onSubmit(this::requestList);
        this.nodeList = new CpList<LpDto.NodeDto>(Component.literal("Nodes"), SMALL_ROW)
                .renderer(this::renderNode)
                .label(n -> n.key() + ", " + LpFormat.value(n.value()) + ", " + LpFormat.contexts(n.contexts())
                        + ", " + LpFormat.expiry(n.expiry()))
                .identity(n -> n.key() + "|" + n.contexts())
                .emptyText("No node.")
                .onSelect(n -> rebuild());
        this.parentList = new CpList<String>(Component.literal("Parents"), SMALL_ROW)
                .renderer(this::renderParent)
                .identity(name -> name)
                .emptyText("No parent group.")
                .onSelect(name -> {
                    groupField.setValue(name);
                    rebuild();
                });
        this.trackGroups = new CpList<String>(Component.literal("Track groups"), SMALL_ROW)
                .renderer(this::renderTrackGroup)
                .identity(name -> name)
                .emptyText("This track has no group.")
                .onSelect(name -> {
                    groupField.setValue(name);
                    rebuild();
                });
        complete(groupField, this::groupNames);
        complete(trackField, this::trackNames);
    }

    private static CpEditBox field(String hint, int max) {
        return new CpEditBox(Component.literal(hint), max).hint(Component.literal(hint));
    }

    private static Section sectionOf(String id) {
        return switch (id) {
            case LuckPermsData.PLAYERS -> Section.PLAYERS;
            case LuckPermsData.TRACKS -> Section.TRACKS;
            default -> Section.GROUPS;
        };
    }

    @Override
    public GuiPage page() {
        return GuiPage.LUCKPERMS;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.SHIELD;
    }

    @Override
    protected CpEditBox searchBox() {
        return section == Section.PLAYERS ? searchField : null;
    }

    @Override
    protected void apply(GuiPageData data) {
        // Refresh button or navigation onto this page: reload what is on screen.
        showLoading();
        requestList();
        requestDetail();
    }

    private boolean editable() {
        return list.canEdit() && context.canEdit(GuiArea.LUCKPERMS);
    }

    // ------------------------------------------------------------------ requests and replies

    private String listScope() {
        return switch (section) {
            case GROUPS -> RequestLpSyncPayload.SCOPE_GROUPS;
            case PLAYERS -> searchTerm().isEmpty() ? RequestLpSyncPayload.SCOPE_USERS
                    : RequestLpSyncPayload.SCOPE_USERS + ":" + searchTerm();
            case TRACKS -> RequestLpSyncPayload.SCOPE_TRACKS;
        };
    }

    private String detailScope() {
        return switch (section) {
            case GROUPS -> selectedGroup == null ? null : RequestLpSyncPayload.SCOPE_GROUP + ":" + selectedGroup;
            case PLAYERS -> selectedUser == null ? null : RequestLpSyncPayload.SCOPE_USER + ":" + selectedUser;
            case TRACKS -> null;
        };
    }

    private String searchTerm() {
        return searchField.getValue().trim();
    }

    private void requestList() {
        PacketDistributor.sendToServer(switch (section) {
            case GROUPS -> RequestLpSyncPayload.of(RequestLpSyncPayload.SCOPE_GROUPS);
            case PLAYERS -> new RequestLpSyncPayload(RequestLpSyncPayload.SCOPE_USERS, searchTerm());
            case TRACKS -> RequestLpSyncPayload.of(RequestLpSyncPayload.SCOPE_TRACKS);
        });
    }

    private void requestDetail() {
        if (section == Section.GROUPS && selectedGroup != null) {
            PacketDistributor.sendToServer(new RequestLpSyncPayload(RequestLpSyncPayload.SCOPE_GROUP, selectedGroup));
        } else if (section == Section.PLAYERS && selectedUser != null) {
            PacketDistributor.sendToServer(new RequestLpSyncPayload(RequestLpSyncPayload.SCOPE_USER, selectedUser));
        }
    }

    @Override
    public void onLpSync(LpDto.Snapshot snapshot) {
        if (snapshot.scope().equals(listScope())) {
            list = snapshot;
            groupList.setItems(snapshot.groups());
            userList.setItems(snapshot.users());
            trackList.setItems(snapshot.tracks());
            fillTrackGroups();
            if (loadingShown) {
                info("");
                loadingShown = false;
            }
        } else if (snapshot.scope().equals(detailScope())) {
            detail = snapshot;
            fillDetailLists();
        } else {
            return;
        }
        rebuild();
    }

    /** A successful edit may change counts shown in the list: reload it as well. */
    @Override
    public void status(String text, boolean success) {
        super.status(text, success);
        if (success) requestList();
    }

    private void edit(LpEditOp op, String... args) {
        String scope = detailScope() != null
                ? (section == Section.GROUPS ? RequestLpSyncPayload.SCOPE_GROUP : RequestLpSyncPayload.SCOPE_USER)
                : (section == Section.TRACKS ? RequestLpSyncPayload.SCOPE_TRACKS : RequestLpSyncPayload.SCOPE_GROUPS);
        String target = section == Section.GROUPS && selectedGroup != null ? selectedGroup
                : section == Section.PLAYERS && selectedUser != null ? selectedUser : "";
        info("Working...");
        loadingShown = false;
        PacketDistributor.sendToServer(new LpEditPayload(op.name(), List.of(args), scope, target));
    }

    // ------------------------------------------------------------------ selection

    private void switchSection(Section next) {
        if (next == section) return;
        section = next;
        tab = Tab.NODES;
        detail = LpDto.Snapshot.EMPTY;
        list = LpDto.Snapshot.EMPTY;
        groupList.setItems(List.of());
        userList.setItems(List.of());
        trackList.setItems(List.of());
        fillDetailLists();
        showLoading();
        requestList();
        requestDetail();
        rebuild();
    }

    private void showLoading() {
        info("Loading...");
        loadingShown = true;
    }

    private void selectDetail(String key) {
        if (section == Section.GROUPS) selectedGroup = key;
        if (section == Section.PLAYERS) selectedUser = key;
        detail = LpDto.Snapshot.EMPTY;
        fillDetailLists();
        requestDetail();
        rebuild();
    }

    private LpDto.GroupDto group() {
        return detail.groups().stream().filter(g -> g.name().equalsIgnoreCase(selectedGroup)).findFirst().orElse(null);
    }

    private LpDto.UserDto user() {
        return detail.users().isEmpty() ? null : detail.users().get(0);
    }

    private LpDto.TrackDto track() {
        return list.tracks().stream().filter(t -> t.name().equals(selectedTrack)).findFirst().orElse(null);
    }

    private void fillDetailLists() {
        List<LpDto.NodeDto> nodes;
        List<String> parents;
        if (section == Section.GROUPS && group() != null) {
            nodes = group().nodes();
            parents = group().parents();
        } else if (section == Section.PLAYERS && user() != null) {
            nodes = user().nodes();
            parents = user().parents();
        } else {
            nodes = List.of();
            parents = List.of();
        }
        // Inheritance nodes are edited on the parents tab.
        nodeList.setItems(nodes.stream().filter(n -> !isType(n, "INHERITANCE")).toList());
        parentList.setItems(parents);
    }

    private void fillTrackGroups() {
        LpDto.TrackDto track = track();
        trackGroups.setItems(track == null ? List.of() : track.groups());
    }

    private List<String> groupNames() {
        LpDto.Snapshot source = detail.groups().isEmpty() ? list : detail;
        return source.groups().stream().map(LpDto.GroupDto::name).toList();
    }

    private List<String> trackNames() {
        LpDto.Snapshot source = detail.tracks().isEmpty() ? list : detail;
        return source.tracks().stream().map(LpDto.TrackDto::name).toList();
    }

    private static boolean isType(LpDto.NodeDto node, String type) {
        return node.type().equalsIgnoreCase(type);
    }

    // ------------------------------------------------------------------ completion

    /** Grey completion of the first matching name; Enter or an action accepts it through {@link #accepted}. */
    private static void complete(CpEditBox box, Supplier<List<String>> names) {
        box.onChange(typed -> box.setSuggestion(rest(typed, names.get())));
    }

    private static String rest(String typed, List<String> names) {
        if (typed.isEmpty()) return null;
        String lower = typed.toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(lower) && n.length() > typed.length())
                .findFirst()
                .map(n -> n.substring(typed.length()))
                .orElse(null);
    }

    /** The typed value, completed when it is the prefix of exactly the suggested name. */
    private static String accepted(CpEditBox box, List<String> names) {
        String typed = box.getValue().trim();
        for (String name : names) {
            if (name.equalsIgnoreCase(typed)) return name;
        }
        String rest = rest(typed, names);
        return rest == null ? typed : typed + rest;
    }

    // ------------------------------------------------------------------ layout

    private Rect toolbar() {
        return layout.content().top(FIELD);
    }

    private Rect body() {
        return layout.content().belowTop(FIELD + GAP);
    }

    private Rect left() {
        Rect body = body();
        return body.left(body.w() * 36 / 100);
    }

    private Rect inner() {
        return body().afterLeft(left().w() + GAP).inset(8);
    }

    @Override
    protected void buildPage() {
        if (!requested) {
            requested = true;
            showLoading();
            requestList();
        }
        buildSections();
        buildLeft();
        switch (section) {
            case GROUPS -> buildGroupDetail();
            case PLAYERS -> buildUserDetail();
            case TRACKS -> buildTrackDetail();
        }
    }

    private void buildSections() {
        Rect bar = toolbar();
        int x = bar.x();
        for (Section s : Section.values()) {
            String label = switch (s) {
                case GROUPS -> "Groups";
                case PLAYERS -> "Players";
                case TRACKS -> "Tracks";
            };
            Icon icon = switch (s) {
                case GROUPS -> Icon.SHIELD;
                case PLAYERS -> Icon.USER;
                case TRACKS -> Icon.TRACK;
            };
            CpButton button = CpButton.ghost(Component.literal(label), () -> switchSection(s)).icon(icon).selected(s == section);
            int w = button.preferredWidth(font, 8);
            addRenderableWidget(button.at(x, bar.y(), w, bar.h()));
            x += w + 4;
        }
    }

    private void buildLeft() {
        Rect left = left();
        boolean editable = editable();
        switch (section) {
            case GROUPS, TRACKS -> {
                Rect create = left.bottom(FIELD);
                addRenderableWidget((section == Section.GROUPS ? groupList : trackList)
                        .at(left.aboveBottom(FIELD + 4)));
                addRenderableWidget(createField.at(create.beforeRight(FIELD + 4)));
                createField.setEditable(editable);
                addRenderableWidget(CpButton.accent(Component.literal(section == Section.GROUPS ? "Create group" : "Create track"),
                        this::create).iconOnly(Icon.PLUS).enabled(editable).at(create.right(FIELD)));
            }
            case PLAYERS -> {
                addRenderableWidget(searchField.at(left.top(FIELD)));
                addRenderableWidget(userList.at(left.belowTop(FIELD + 4)));
            }
        }
    }

    private void buildTabs(Rect in, String parentsLabel, boolean withMeta) {
        Rect tabs = new Rect(in.x(), in.y() + 24, in.w(), FIELD);
        int x = tabs.x();
        for (Tab t : Tab.values()) {
            if (t == Tab.META && !withMeta) continue;
            String label = switch (t) {
                case NODES -> "Nodes";
                case PARENTS -> parentsLabel;
                case META -> "Chat & meta";
            };
            CpButton button = CpButton.ghost(Component.literal(label), () -> {
                tab = t;
                rebuild();
            }).selected(t == tab);
            int w = button.preferredWidth(font, 6);
            addRenderableWidget(button.at(x, tabs.y(), w, tabs.h()));
            x += w + 2;
        }
    }

    /** List area of a tab, leaving {@code bottomRows} pixels of controls under it. */
    private Rect tabList(Rect in, int bottomRows) {
        int top = in.y() + 24 + FIELD + 4;
        return new Rect(in.x(), top, in.w(), in.bottom() - bottomRows - top);
    }

    private void buildGroupDetail() {
        LpDto.GroupDto group = group();
        if (selectedGroup == null || group == null) return;
        Rect in = inner();
        boolean editable = editable();
        addRenderableWidget(CpButton.danger(Component.literal("Delete group"), () -> confirm("Delete group " + group.name(),
                        "Every player and group inheriting from " + group.name() + " loses it. This cannot be undone.",
                        "Delete " + group.name(), () -> {
                            edit(LpEditOp.GROUP_DELETE, group.name());
                            selectedGroup = null;
                            detail = LpDto.Snapshot.EMPTY;
                            fillDetailLists();
                            rebuild();
                        }))
                .iconOnly(Icon.TRASH).enabled(editable).at(new Rect(in.right() - FIELD, in.y(), FIELD, FIELD)));
        buildTabs(in, "Parents (" + group.parents().size() + ")", true);
        String name = group.name();
        switch (tab) {
            case NODES -> buildNodesTab(in, editable,
                    (key, allow, ctx, duration) -> edit(LpEditOp.GROUP_PERM_ADD, name, key, allow, ctx, duration),
                    this::removeGroupNode);
            case PARENTS -> {
                int controls = FIELD + 4 + BUTTON;
                addRenderableWidget(parentList.at(tabList(in, controls + 4)));
                Rect fieldRow = new Rect(in.x(), in.bottom() - controls, in.w(), FIELD);
                addRenderableWidget(groupField.at(fieldRow));
                groupField.setEditable(editable);
                Rect buttons = new Rect(in.x(), fieldRow.bottom() + 4, in.w(), BUTTON);
                CpButton add = CpButton.accent(Component.literal("Add parent"),
                        () -> edit(LpEditOp.GROUP_PARENT_ADD, name, accepted(groupField, groupNames()), ""))
                        .icon(Icon.PLUS).enabled(editable);
                addRenderableWidget(add.at(buttons.left(add.preferredWidth(font, 6))));
                String parent = parentList.getSelected();
                CpButton remove = CpButton.neutral(Component.literal("Remove"),
                        () -> edit(LpEditOp.GROUP_PARENT_REMOVE, name, parent, ""))
                        .icon(Icon.MINUS).enabled(editable && parent != null);
                addRenderableWidget(remove.at(buttons.right(remove.preferredWidth(font, 6))));
            }
            case META -> {
                int y = in.y() + 24 + FIELD + 6;
                y = metaRow(in, y, editable, weightField, null,
                        () -> edit(LpEditOp.GROUP_WEIGHT_SET, name, weightField.getValue().trim().isEmpty() ? "-1" : weightField.getValue().trim()),
                        null);
                y = metaRow(in, y, editable, displayField, null,
                        () -> edit(LpEditOp.GROUP_DISPLAYNAME_SET, name, displayField.getValue().trim()),
                        null);
                buildChatMetaRows(in, y, editable,
                        (op, args) -> edit(op, prepend(name, args)),
                        LpEditOp.GROUP_PREFIX_SET, LpEditOp.GROUP_PREFIX_UNSET,
                        LpEditOp.GROUP_SUFFIX_SET, LpEditOp.GROUP_SUFFIX_UNSET,
                        LpEditOp.GROUP_META_SET, LpEditOp.GROUP_META_UNSET);
            }
        }
    }

    private void buildUserDetail() {
        LpDto.UserDto user = user();
        if (selectedUser == null || user == null) return;
        Rect in = inner();
        boolean editable = editable();
        buildTabs(in, "Groups (" + user.parents().size() + ")", true);
        String uuid = user.uuid();
        switch (tab) {
            case NODES -> buildNodesTab(in, editable,
                    (key, allow, ctx, duration) -> edit(LpEditOp.USER_PERM_ADD, uuid, key, allow, ctx, duration),
                    node -> removeUserNode(uuid, node));
            case PARENTS -> {
                int controls = FIELD + 4 + BUTTON + 4 + FIELD;
                addRenderableWidget(parentList.at(tabList(in, controls + 4)));
                Rect row = new Rect(in.x(), in.bottom() - controls, in.w(), FIELD);
                int durW = Math.min(60, row.w() / 3);
                addRenderableWidget(groupField.at(row.beforeRight(durW + 4)));
                addRenderableWidget(groupDuration.at(row.right(durW)));
                groupField.setEditable(editable);
                groupDuration.setEditable(editable);

                Rect buttons = new Rect(in.x(), row.bottom() + 4, in.w(), BUTTON);
                String parent = parentList.getSelected();
                CpButton add = CpButton.accent(Component.literal("Add"), () -> edit(LpEditOp.USER_PARENT_ADD, uuid,
                        accepted(groupField, groupNames()), "", LpFormat.durationSeconds(groupDuration.getValue())))
                        .icon(Icon.PLUS).enabled(editable);
                int addW = add.preferredWidth(font, 6);
                addRenderableWidget(add.at(buttons.left(addW)));
                CpButton primary = CpButton.neutral(Component.literal("Primary"),
                        () -> edit(LpEditOp.USER_PRIMARY_GROUP_SET, uuid, parent))
                        .icon(Icon.CHECK).enabled(editable && parent != null && !parent.equalsIgnoreCase(user.primaryGroup()));
                addRenderableWidget(primary.at(new Rect(buttons.x() + addW + 4, buttons.y(), primary.preferredWidth(font, 6), BUTTON)));
                CpButton remove = CpButton.neutral(Component.literal("Remove"),
                        () -> edit(LpEditOp.USER_PARENT_REMOVE, uuid, parent, ""))
                        .icon(Icon.MINUS).enabled(editable && parent != null);
                addRenderableWidget(remove.at(buttons.right(remove.preferredWidth(font, 6))));

                Rect trackRow = new Rect(in.x(), buttons.bottom() + 4, in.w(), FIELD);
                CpButton promote = CpButton.neutral(Component.literal("Promote"),
                        () -> edit(LpEditOp.USER_PROMOTE, uuid, accepted(trackField, trackNames()))).iconOnly(Icon.UP)
                        .enabled(editable);
                CpButton demote = CpButton.neutral(Component.literal("Demote"),
                        () -> edit(LpEditOp.USER_DEMOTE, uuid, accepted(trackField, trackNames()))).iconOnly(Icon.DOWN)
                        .enabled(editable);
                addRenderableWidget(trackField.at(trackRow.beforeRight(2 * FIELD + 8)));
                trackField.setEditable(editable);
                addRenderableWidget(promote.at(new Rect(trackRow.right() - 2 * FIELD - 4, trackRow.y(), FIELD, FIELD)));
                addRenderableWidget(demote.at(trackRow.right(FIELD)));
            }
            case META -> buildChatMetaRows(in, in.y() + 24 + FIELD + 6, editable,
                    (op, args) -> edit(op, prepend(uuid, args)),
                    LpEditOp.USER_PREFIX_SET, LpEditOp.USER_PREFIX_UNSET,
                    LpEditOp.USER_SUFFIX_SET, LpEditOp.USER_SUFFIX_UNSET,
                    LpEditOp.USER_META_SET, LpEditOp.USER_META_UNSET);
        }
    }

    private void buildTrackDetail() {
        LpDto.TrackDto track = track();
        if (track == null) return;
        Rect in = inner();
        boolean editable = editable();
        String name = track.name();
        addRenderableWidget(CpButton.danger(Component.literal("Delete track"), () -> confirm("Delete track " + name,
                        "Players keep their groups, but promote and demote on " + name + " stop working.",
                        "Delete " + name, () -> {
                            edit(LpEditOp.TRACK_DELETE, name);
                            selectedTrack = null;
                            fillTrackGroups();
                            rebuild();
                        }))
                .iconOnly(Icon.TRASH).enabled(editable).at(new Rect(in.right() - FIELD, in.y(), FIELD, FIELD)));

        int controls = FIELD + 4 + BUTTON;
        Rect groups = new Rect(in.x(), in.y() + 24, in.w(), in.bottom() - controls - 4 - in.y() - 24);
        addRenderableWidget(trackGroups.at(groups));
        Rect row = new Rect(in.x(), in.bottom() - controls, in.w(), FIELD);
        addRenderableWidget(groupField.at(row.beforeRight(40)));
        addRenderableWidget(positionField.at(row.right(36)));
        groupField.setEditable(editable);
        positionField.setEditable(editable);
        positionField.setFilter(text -> text.chars().allMatch(Character::isDigit));

        Rect buttons = new Rect(in.x(), row.bottom() + 4, in.w(), BUTTON);
        CpButton append = CpButton.accent(Component.literal("Append"),
                () -> edit(LpEditOp.TRACK_APPEND, name, accepted(groupField, groupNames()))).icon(Icon.PLUS).enabled(editable);
        int appendW = append.preferredWidth(font, 6);
        addRenderableWidget(append.at(buttons.left(appendW)));
        CpButton insert = CpButton.neutral(Component.literal("Insert"),
                () -> edit(LpEditOp.TRACK_INSERT, name, accepted(groupField, groupNames()),
                        positionField.getValue().isEmpty() ? "0" : positionField.getValue()))
                .icon(Icon.DOWN).enabled(editable)
                .tooltip(Component.literal("Inserts the group at the position typed on the right (0 = first)."));
        addRenderableWidget(insert.at(new Rect(buttons.x() + appendW + 4, buttons.y(), insert.preferredWidth(font, 6), BUTTON)));
        String selected = trackGroups.getSelected();
        CpButton remove = CpButton.neutral(Component.literal("Remove"),
                () -> edit(LpEditOp.TRACK_REMOVE, name, selected)).icon(Icon.MINUS).enabled(editable && selected != null);
        addRenderableWidget(remove.at(buttons.right(remove.preferredWidth(font, 6))));
    }

    @FunctionalInterface
    private interface NodeAdder {
        void add(String key, String allow, String contexts, String duration);
    }

    @FunctionalInterface
    private interface MetaEditor {
        void edit(LpEditOp op, String... args);
    }

    private void buildNodesTab(Rect in, boolean editable, NodeAdder adder, java.util.function.Consumer<LpDto.NodeDto> remover) {
        int controls = FIELD + 4 + FIELD + 4 + BUTTON;
        addRenderableWidget(nodeList.at(tabList(in, controls + 4)));
        Rect keyRow = new Rect(in.x(), in.bottom() - controls, in.w(), FIELD);
        addRenderableWidget(nodeKey.at(keyRow));
        Rect ctxRow = new Rect(in.x(), keyRow.bottom() + 4, in.w(), FIELD);
        int durW = Math.min(90, ctxRow.w() / 3);
        addRenderableWidget(nodeContexts.at(ctxRow.beforeRight(durW + 4)));
        addRenderableWidget(nodeDuration.at(ctxRow.right(durW)));
        nodeKey.setEditable(editable);
        nodeContexts.setEditable(editable);
        nodeDuration.setEditable(editable);

        Rect buttons = new Rect(in.x(), ctxRow.bottom() + 4, in.w(), BUTTON);
        Runnable allow = () -> addNode(adder, true);
        CpButton allowButton = CpButton.good(Component.literal("Allow"), allow).icon(Icon.CHECK).enabled(editable);
        CpButton denyButton = CpButton.danger(Component.literal("Deny"), () -> addNode(adder, false)).icon(Icon.CROSS).enabled(editable);
        int allowW = allowButton.preferredWidth(font, 6);
        addRenderableWidget(allowButton.at(buttons.left(allowW)));
        addRenderableWidget(denyButton.at(new Rect(buttons.x() + allowW + 4, buttons.y(), denyButton.preferredWidth(font, 6), BUTTON)));
        nodeKey.onSubmit(allow);
        LpDto.NodeDto selected = nodeList.getSelected();
        CpButton remove = CpButton.neutral(Component.literal("Remove"), () -> {
            if (selected != null) remover.accept(selected);
        }).icon(Icon.MINUS).enabled(editable && selected != null);
        addRenderableWidget(remove.at(buttons.right(remove.preferredWidth(font, 6))));
    }

    private void addNode(NodeAdder adder, boolean allow) {
        String key = nodeKey.getValue().trim();
        if (key.isEmpty()) return;
        adder.add(key, String.valueOf(allow), nodeContexts.getValue().trim(), LpFormat.durationSeconds(nodeDuration.getValue()));
        nodeKey.setValue("");
    }

    /**
     * One labelled form row: a field, an optional second field, a Set button and an optional Clear
     * button. Returns the y of the next row.
     */
    private int metaRow(Rect in, int y, boolean editable, CpEditBox first, CpEditBox second, Runnable set, Runnable clear) {
        Rect row = new Rect(in.x() + LABEL, y, in.w() - LABEL, FIELD);
        int buttons = FIELD + (clear != null ? 4 + FIELD : 0);
        Rect fields = row.beforeRight(buttons + 4);
        if (second == null) {
            addRenderableWidget(first.at(fields));
        } else {
            int firstW = first == prefixPriority || first == suffixPriority ? 34 : fields.w() / 2 - 2;
            addRenderableWidget(first.at(fields.left(firstW)));
            addRenderableWidget(second.at(fields.afterLeft(firstW + 4)));
            second.setEditable(editable);
        }
        first.setEditable(editable);
        int x = row.right() - buttons;
        addRenderableWidget(CpButton.accent(Component.literal("Set"), set).iconOnly(Icon.CHECK).enabled(editable)
                .at(new Rect(x, y, FIELD, FIELD)));
        if (clear != null) {
            addRenderableWidget(CpButton.neutral(Component.literal("Clear"), clear).iconOnly(Icon.CROSS).enabled(editable)
                    .at(new Rect(x + FIELD + 4, y, FIELD, FIELD)));
        }
        return y + FIELD + 4;
    }

    private void buildChatMetaRows(Rect in, int y, boolean editable, MetaEditor editor,
                                   LpEditOp prefixSet, LpEditOp prefixUnset, LpEditOp suffixSet, LpEditOp suffixUnset,
                                   LpEditOp metaSet, LpEditOp metaUnset) {
        y = metaRow(in, y, editable, prefixPriority, prefixValue,
                () -> {
                    if (!prefixValue.getValue().isEmpty()) editor.edit(prefixSet, priority(prefixPriority), prefixValue.getValue(), "");
                },
                () -> editor.edit(prefixUnset, priority(prefixPriority), ""));
        y = metaRow(in, y, editable, suffixPriority, suffixValue,
                () -> {
                    if (!suffixValue.getValue().isEmpty()) editor.edit(suffixSet, priority(suffixPriority), suffixValue.getValue(), "");
                },
                () -> editor.edit(suffixUnset, priority(suffixPriority), ""));
        metaRow(in, y, editable, metaKey, metaValue,
                () -> {
                    if (!metaKey.getValue().trim().isEmpty()) editor.edit(metaSet, metaKey.getValue().trim(), metaValue.getValue(), "");
                },
                () -> {
                    if (!metaKey.getValue().trim().isEmpty()) editor.edit(metaUnset, metaKey.getValue().trim(), "");
                });
    }

    private static String priority(CpEditBox box) {
        String raw = box.getValue().trim();
        return raw.isEmpty() ? DEFAULT_PRIORITY : raw;
    }

    private static String[] prepend(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    // ------------------------------------------------------------------ node removal by type

    private void removeGroupNode(LpDto.NodeDto node) {
        String name = selectedGroup;
        String key = node.key();
        if (isType(node, "WEIGHT")) {
            edit(LpEditOp.GROUP_WEIGHT_SET, name, "-1");
        } else if (isType(node, "DISPLAY_NAME")) {
            edit(LpEditOp.GROUP_DISPLAYNAME_SET, name, "");
        } else if (isType(node, "PREFIX")) {
            edit(LpEditOp.GROUP_PREFIX_UNSET, name, segment(key), node.contexts());
        } else if (isType(node, "SUFFIX")) {
            edit(LpEditOp.GROUP_SUFFIX_UNSET, name, segment(key), node.contexts());
        } else if (isType(node, "META")) {
            edit(LpEditOp.GROUP_META_UNSET, name, segment(key), node.contexts());
        } else {
            edit(LpEditOp.GROUP_PERM_REMOVE, name, key, node.contexts());
        }
    }

    private void removeUserNode(String uuid, LpDto.NodeDto node) {
        String key = node.key();
        if (isType(node, "PREFIX")) {
            edit(LpEditOp.USER_PREFIX_UNSET, uuid, segment(key), node.contexts());
        } else if (isType(node, "SUFFIX")) {
            edit(LpEditOp.USER_SUFFIX_UNSET, uuid, segment(key), node.contexts());
        } else if (isType(node, "META")) {
            edit(LpEditOp.USER_META_UNSET, uuid, segment(key), node.contexts());
        } else {
            edit(LpEditOp.USER_PERM_REMOVE, uuid, key, node.contexts());
        }
    }

    /** Second dot-separated part of a meta node key: the priority of {@code prefix.100.x}, the key of {@code meta.k.v}. */
    private static String segment(String key) {
        String[] parts = key.split("\\.", 3);
        return parts.length > 1 ? parts[1] : "";
    }

    private void create() {
        String name = createField.getValue().trim();
        if (name.isEmpty()) return;
        edit(section == Section.GROUPS ? LpEditOp.GROUP_CREATE : LpEditOp.TRACK_CREATE, name);
        createField.setValue("");
    }

    // ------------------------------------------------------------------ rendering

    private void renderGroup(GuiGraphics g, Font font, LpDto.GroupDto group, Rect r, boolean hovered, boolean selected) {
        String weight = LpFormat.weight(group.weight());
        int w = font.width(weight);
        Skin.text(g, font, weight, r.right() - w - 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        String label = group.displayName().isEmpty() ? group.name() : group.name() + " (" + group.displayName() + ")";
        Skin.text(g, font, label, r.x() + 6, r.y() + (r.h() - 8) / 2, r.w() - w - 16, Palette.TEXT);
    }

    private void renderUser(GuiGraphics g, Font font, LpDto.UserDto user, Rect r, boolean hovered, boolean selected) {
        Skin.dot(g, r.x() + 6, r.centerY(), user.online() ? Palette.GOOD : Palette.LINE_STRONG);
        int w = font.width(user.primaryGroup());
        Skin.text(g, font, user.primaryGroup(), r.right() - w - 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        int x = r.x() + 6 + Atlas.DOT_SIZE + 5;
        Skin.text(g, font, user.username(), x, r.y() + (r.h() - 8) / 2, r.right() - w - x - 10, Palette.TEXT);
    }

    private void renderTrack(GuiGraphics g, Font font, LpDto.TrackDto track, Rect r, boolean hovered, boolean selected) {
        String count = String.valueOf(track.groups().size());
        int w = font.width(count);
        Skin.text(g, font, count, r.right() - w - 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, track.name(), r.x() + 6, r.y() + (r.h() - 8) / 2, r.w() - w - 16, Palette.TEXT);
    }

    private void renderNode(GuiGraphics g, Font font, LpDto.NodeDto node, Rect r, boolean hovered, boolean selected) {
        String badge;
        int color;
        if (isType(node, "PERMISSION") || isType(node, "REGEX_PERMISSION")) {
            badge = node.value() ? "ALLOW" : "DENY";
            color = node.value() ? Palette.GOOD : Palette.DANGER;
        } else {
            badge = node.type().toUpperCase(Locale.ROOT).replace("DISPLAY_NAME", "NAME");
            color = Palette.INFO;
        }
        int bw = Skin.badge(g, font, badge, r.x() + 4, r.centerY(), color);
        int x = r.x() + 4 + Math.max(bw, font.width("SUFFIX") + 6) + 5;
        String extra = (node.contexts().isEmpty() ? "" : LpFormat.contexts(node.contexts()) + "  ")
                + (node.expiry() == LpDto.NO_EXPIRY ? "" : LpFormat.expiry(node.expiry()));
        int ew = font.width(extra);
        if (!extra.isEmpty()) Skin.text(g, font, extra, r.right() - ew - 4, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, node.key(), x, r.y() + (r.h() - 8) / 2, r.right() - ew - x - 8, Palette.TEXT);
    }

    private void renderParent(GuiGraphics g, Font font, String parent, Rect r, boolean hovered, boolean selected) {
        LpDto.UserDto user = section == Section.PLAYERS ? user() : null;
        boolean primary = user != null && parent.equalsIgnoreCase(user.primaryGroup());
        int right = r.right() - 4;
        if (primary) {
            int w = font.width("PRIMARY") + 6;
            Skin.badge(g, font, "PRIMARY", right - w, r.centerY(), Palette.ACCENT_HI);
            right -= w + 3;
        }
        Skin.text(g, font, parent, r.x() + 6, r.y() + (r.h() - 8) / 2, right - r.x() - 10, Palette.TEXT);
    }

    private void renderTrackGroup(GuiGraphics g, Font font, String group, Rect r, boolean hovered, boolean selected) {
        int index = trackGroups.items().indexOf(group);
        Skin.text(g, font, "#" + index, r.x() + 5, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        int x = r.x() + 5 + font.width("#00") + 6;
        Skin.text(g, font, group, x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect in = inner();
        Skin.panel(g, in.inset(-8));
        if (!editable()) {
            String note = "Read-only: needs " + GuiArea.LUCKPERMS.node();
            Rect bar = toolbar();
            int w = Math.min(font.width(note), bar.w() / 2);
            Skin.text(g, font, note, bar.right() - w, bar.y() + (bar.h() - 8) / 2, w, Palette.TEXT_MUTE);
        }
        switch (section) {
            case GROUPS -> {
                LpDto.GroupDto group = group();
                if (group == null) {
                    placeholder(g, in, selectedGroup == null
                            ? "Select a group to edit its nodes, parents, weight, prefix, suffix and meta."
                            : "Loading " + selectedGroup + "...");
                    return;
                }
                header(g, in, group.name(), "weight " + LpFormat.weight(group.weight()) + "  |  prefix "
                        + LpFormat.orDash(group.prefix()) + "  |  " + group.nodeCount() + " nodes");
                if (tab == Tab.META) metaLabels(g, in, true);
            }
            case PLAYERS -> {
                LpDto.UserDto user = user();
                if (user == null) {
                    placeholder(g, in, selectedUser == null
                            ? "Select a player. Online players are listed; search an exact name to load an offline one."
                            : "Loading player...");
                    return;
                }
                header(g, in, user.username(), (user.online() ? "online" : "offline") + "  |  primary group "
                        + user.primaryGroup() + "  |  " + user.nodeCount() + " nodes");
                if (tab == Tab.META) metaLabels(g, in, false);
            }
            case TRACKS -> {
                LpDto.TrackDto track = track();
                if (track == null) {
                    placeholder(g, in, "Select a track: an ordered ladder of groups used by promote and demote.");
                    return;
                }
                header(g, in, track.name(), track.groups().size() + " groups, from first to last");
            }
        }
    }

    private void header(GuiGraphics g, Rect in, String title, String subtitle) {
        Skin.text(g, font, title, in.x(), in.y(), in.w() - FIELD - 6, Palette.TEXT);
        Skin.text(g, font, subtitle, in.x(), in.y() + 11, in.w() - FIELD - 6, Palette.TEXT_MUTE);
    }

    private void placeholder(GuiGraphics g, Rect in, String text) {
        paragraph(g, text, in, in.y(), Palette.TEXT_MUTE);
    }

    private void metaLabels(GuiGraphics g, Rect in, boolean groupRows) {
        int y = in.y() + 24 + FIELD + 6;
        List<String> labels = groupRows
                ? List.of("Weight", "Name", "Prefix", "Suffix", "Meta")
                : List.of("Prefix", "Suffix", "Meta");
        for (String label : labels) {
            Skin.text(g, font, label, in.x(), y + (FIELD - 8) / 2, LABEL - 4, Palette.TEXT_DIM);
            y += FIELD + 4;
        }
    }
}
