/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.lp;

import com.arcadia.customperm.network.lp.LpDto;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One player: their groups, their own permission nodes, their meta and their position on every
 * track. The web editor's user pane, minus the bulk operations that only make sense with a
 * mouse and a full-size window.
 *
 * <p>Addressed by UUID throughout. The username is carried alongside for the title only — a
 * player can change it, and an edit keyed on a name would silently target the wrong account.
 */
public final class LpUserScreen extends AbstractLpScreen {

    private static final String INPUT_PERM = "userPermNode";
    private static final String INPUT_PERM_CONTEXTS = "userPermContexts";
    private static final String INPUT_PERM_DURATION = "userPermDuration";
    private static final String INPUT_PARENT_DURATION = "userParentDuration";
    private static final String INPUT_META_KEY = "userMetaKey";
    private static final String INPUT_META_VALUE = "userMetaValue";
    private static final String INPUT_PREFIX_PRIORITY = "userPrefixPriority";
    private static final String INPUT_PREFIX_VALUE = "userPrefixValue";
    private static final String INPUT_SUFFIX_PRIORITY = "userSuffixPriority";
    private static final String INPUT_SUFFIX_VALUE = "userSuffixValue";

    private static final String DEFAULT_PRIORITY = "100";

    private final String uuid;
    private final String username;

    public LpUserScreen(String uuid, String username) {
        super(Component.literal("LuckPerms - " + username));
        this.uuid = uuid;
        this.username = username;
    }

    @Override
    protected String scope() {
        return RequestLpSyncPayload.SCOPE_USER;
    }

    @Override
    protected String target() {
        return uuid;
    }

    @Override
    protected String templateId() {
        return "customperm:ui/lp_user";
    }

    @Override
    protected void fill(LpDto.Snapshot snapshot, Map<String, String> data,
                        Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        LpDto.UserDto user = snapshot.users().isEmpty() ? null : snapshot.users().get(0);

        data.put("userName", username);
        data.put("missing", String.valueOf(user == null));
        data.put("present", String.valueOf(user != null));
        handlers.put("backToUsers", () -> open(new LpUsersScreen()));
        if (user == null) {
            data.put("nodes", "0");
            data.put("parents", "0");
            data.put("candidates", "0");
            data.put("tracks", "0");
            return;
        }

        data.put("primaryGroup", user.primaryGroup());
        data.put("userStatus", user.online() ? "online" : "offline");

        fillNodes(user, data, handlers);
        fillGroups(user, snapshot, data, handlers);
        fillTracks(snapshot, data, handlers);
        registerEditHandlers(handlers, submits);
    }

    private void fillNodes(LpDto.UserDto user, Map<String, String> data, Map<String, Runnable> handlers) {
        List<LpDto.NodeDto> nodes = user.nodes();
        data.put("noNodes", String.valueOf(nodes.isEmpty()));
        data.put("nodes", String.valueOf(nodes.size()));
        for (int i = 0; i < nodes.size(); i++) {
            LpDto.NodeDto node = nodes.get(i);
            data.put("node.key." + i, node.key());
            data.put("node.value." + i, LpFormat.value(node.value()));
            data.put("node.contexts." + i, LpFormat.contexts(node.contexts()));
            data.put("node.expiry." + i, LpFormat.expiry(node.expiry()));

            String removeKey = "node.remove." + i;
            data.put("node.removeAction." + i, removeKey);
            handlers.put(removeKey, () -> edit(LpEditOp.USER_PERM_REMOVE, uuid, node.key(), node.contexts()));
        }
    }

    private void fillGroups(LpDto.UserDto user, LpDto.Snapshot snapshot,
                            Map<String, String> data, Map<String, Runnable> handlers) {
        List<String> parents = user.parents();
        data.put("noParents", String.valueOf(parents.isEmpty()));
        data.put("parents", String.valueOf(parents.size()));
        for (int i = 0; i < parents.size(); i++) {
            String parent = parents.get(i);
            data.put("parent.name." + i, parent);
            data.put("parent.primary." + i, String.valueOf(parent.equalsIgnoreCase(user.primaryGroup())));

            String removeKey = "parent.remove." + i;
            // Distinct from the "parent.primary.<i>" model flag above: one is a boolean the
            // template branches on, the other is a handler key the template invokes.
            String primaryKey = "parent.setprimary." + i;
            data.put("parent.removeAction." + i, removeKey);
            data.put("parent.primaryAction." + i, primaryKey);
            handlers.put(removeKey, () -> edit(LpEditOp.USER_PARENT_REMOVE, uuid, parent, ""));
            handlers.put(primaryKey, () -> edit(LpEditOp.USER_PRIMARY_GROUP_SET, uuid, parent));
        }

        List<LpDto.GroupDto> candidates = snapshot.groups().stream()
                .filter(g -> !parents.contains(g.name()))
                .toList();
        data.put("candidates", String.valueOf(candidates.size()));
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i).name();
            data.put("candidate.name." + i, candidate);
            String addKey = "candidate.add." + i;
            data.put("candidate.addAction." + i, addKey);
            // The duration field is shared by the whole section: a temporary rank is the common
            // case for users, and a per-row field would not fit the row width.
            handlers.put(addKey, () -> edit(LpEditOp.USER_PARENT_ADD, uuid, candidate, "",
                    LpFormat.durationSeconds(value(INPUT_PARENT_DURATION))));
        }
    }

    private void fillTracks(LpDto.Snapshot snapshot, Map<String, String> data, Map<String, Runnable> handlers) {
        List<LpDto.TrackDto> tracks = snapshot.tracks();
        data.put("noTracks", String.valueOf(tracks.isEmpty()));
        data.put("tracks", String.valueOf(tracks.size()));
        for (int i = 0; i < tracks.size(); i++) {
            String track = tracks.get(i).name();
            data.put("track.name." + i, track);
            data.put("track.groups." + i, LpFormat.join(tracks.get(i).groups()));

            String promoteKey = "track.promote." + i;
            String demoteKey = "track.demote." + i;
            data.put("track.promoteAction." + i, promoteKey);
            data.put("track.demoteAction." + i, demoteKey);
            handlers.put(promoteKey, () -> edit(LpEditOp.USER_PROMOTE, uuid, track));
            handlers.put(demoteKey, () -> edit(LpEditOp.USER_DEMOTE, uuid, track));
        }
    }

    private void registerEditHandlers(Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        handlers.put("addAllow", () -> addPermission(true));
        handlers.put("addDeny", () -> addPermission(false));
        submits.put(INPUT_PERM, text -> addPermission(true));

        handlers.put("setMeta", () -> {
            String key = value(INPUT_META_KEY);
            if (key.isEmpty()) return;
            edit(LpEditOp.USER_META_SET, uuid, key, value(INPUT_META_VALUE), "");
        });
        handlers.put("unsetMeta", () -> {
            String key = value(INPUT_META_KEY);
            if (key.isEmpty()) return;
            edit(LpEditOp.USER_META_UNSET, uuid, key, "");
        });

        handlers.put("setPrefix", () -> setChatMeta(LpEditOp.USER_PREFIX_SET, INPUT_PREFIX_PRIORITY, INPUT_PREFIX_VALUE));
        handlers.put("unsetPrefix", () -> edit(LpEditOp.USER_PREFIX_UNSET, uuid, priority(INPUT_PREFIX_PRIORITY), ""));
        handlers.put("setSuffix", () -> setChatMeta(LpEditOp.USER_SUFFIX_SET, INPUT_SUFFIX_PRIORITY, INPUT_SUFFIX_VALUE));
        handlers.put("unsetSuffix", () -> edit(LpEditOp.USER_SUFFIX_UNSET, uuid, priority(INPUT_SUFFIX_PRIORITY), ""));
    }

    private void addPermission(boolean allow) {
        String node = value(INPUT_PERM);
        if (node.isEmpty()) return;
        edit(LpEditOp.USER_PERM_ADD, uuid, node, String.valueOf(allow),
                value(INPUT_PERM_CONTEXTS), LpFormat.durationSeconds(value(INPUT_PERM_DURATION)));
        clearInput(INPUT_PERM);
    }

    private void setChatMeta(LpEditOp op, String priorityInput, String valueInput) {
        String text = value(valueInput);
        if (text.isEmpty()) return;
        edit(op, uuid, priority(priorityInput), text, "");
    }

    private String priority(String inputId) {
        String raw = value(inputId);
        return raw.isEmpty() ? DEFAULT_PRIORITY : raw;
    }
}
