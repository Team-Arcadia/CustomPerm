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
 * Everything the web editor exposes for a single group, on one screen: permission nodes,
 * inheritance, meta, prefix/suffix, weight and display name.
 *
 * <p>The snapshot for this scope puts the edited group first and every other group after it
 * (see {@code LuckPermsAdminService.groupSnapshot}); the tail is what the inheritance section
 * offers as parents, so adding a parent needs no extra round-trip.
 */
public final class LpGroupScreen extends AbstractLpScreen {

    private static final String INPUT_PERM = "permNode";
    private static final String INPUT_PERM_CONTEXTS = "permContexts";
    private static final String INPUT_PERM_DURATION = "permDuration";
    private static final String INPUT_PARENT = "parentName";
    private static final String INPUT_META_KEY = "metaKey";
    private static final String INPUT_META_VALUE = "metaValue";
    private static final String INPUT_PREFIX_PRIORITY = "prefixPriority";
    private static final String INPUT_PREFIX_VALUE = "prefixValue";
    private static final String INPUT_SUFFIX_PRIORITY = "suffixPriority";
    private static final String INPUT_SUFFIX_VALUE = "suffixValue";
    private static final String INPUT_WEIGHT = "weight";
    private static final String INPUT_DISPLAY_NAME = "displayName";

    /** Chat meta priority used when the admin leaves the priority field empty. */
    private static final String DEFAULT_PRIORITY = "100";

    private final String groupName;

    public LpGroupScreen(String groupName) {
        super(Component.literal("LuckPerms - " + groupName));
        this.groupName = groupName;
    }

    @Override
    protected String scope() {
        return RequestLpSyncPayload.SCOPE_GROUP;
    }

    @Override
    protected String target() {
        return groupName;
    }

    @Override
    protected String templateId() {
        return "customperm:ui/lp_group";
    }

    @Override
    protected void fill(LpDto.Snapshot snapshot, Map<String, String> data,
                        Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        LpDto.GroupDto group = snapshot.groups().stream()
                .filter(g -> g.name().equalsIgnoreCase(groupName))
                .findFirst()
                .orElse(null);

        data.put("groupName", groupName);
        data.put("missing", String.valueOf(group == null));
        data.put("present", String.valueOf(group != null));
        handlers.put("backToGroups", () -> open(new LpGroupsScreen()));
        if (group == null) {
            // The group was deleted (possibly from /lp, by someone else) while this screen was
            // open. Say so instead of rendering an editor for something that no longer exists.
            data.put("nodes", "0");
            data.put("parents", "0");
            data.put("candidates", "0");
            return;
        }

        data.put("displayNameValue", LpFormat.orDash(group.displayName()));
        data.put("weightValue", LpFormat.weight(group.weight()));
        data.put("prefixValue", LpFormat.orDash(group.prefix()));
        data.put("suffixValue", LpFormat.orDash(group.suffix()));

        fillNodes(group, data, handlers);
        fillParents(group, snapshot, data, handlers);
        registerEditHandlers(handlers, submits);
    }

    private void fillNodes(LpDto.GroupDto group, Map<String, String> data, Map<String, Runnable> handlers) {
        List<LpDto.NodeDto> nodes = group.nodes();
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
            // Removal is keyed by node text plus context, which is how LuckPerms identifies a
            // node — an index into this list would go stale the moment anything else changes.
            handlers.put(removeKey, () ->
                    edit(LpEditOp.GROUP_PERM_REMOVE, groupName, node.key(), node.contexts()));
        }
    }

    private void fillParents(LpDto.GroupDto group, LpDto.Snapshot snapshot,
                             Map<String, String> data, Map<String, Runnable> handlers) {
        List<String> parents = group.parents();
        data.put("noParents", String.valueOf(parents.isEmpty()));
        data.put("parents", String.valueOf(parents.size()));
        for (int i = 0; i < parents.size(); i++) {
            String parent = parents.get(i);
            data.put("parent.name." + i, parent);
            String removeKey = "parent.remove." + i;
            data.put("parent.removeAction." + i, removeKey);
            handlers.put(removeKey, () -> edit(LpEditOp.GROUP_PARENT_REMOVE, groupName, parent, ""));
        }

        List<LpDto.GroupDto> candidates = snapshot.groups().stream()
                .filter(g -> !g.name().equalsIgnoreCase(groupName))
                .filter(g -> !parents.contains(g.name()))
                .toList();
        data.put("candidates", String.valueOf(candidates.size()));
        for (int i = 0; i < candidates.size(); i++) {
            String candidate = candidates.get(i).name();
            data.put("candidate.name." + i, candidate);
            String addKey = "candidate.add." + i;
            data.put("candidate.addAction." + i, addKey);
            handlers.put(addKey, () -> edit(LpEditOp.GROUP_PARENT_ADD, groupName, candidate, ""));
        }
    }

    private void registerEditHandlers(Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        handlers.put("addAllow", () -> addPermission(true));
        handlers.put("addDeny", () -> addPermission(false));
        submits.put(INPUT_PERM, text -> addPermission(true));

        handlers.put("addParent", this::addParent);
        submits.put(INPUT_PARENT, text -> addParent());

        handlers.put("setMeta", this::setMeta);
        handlers.put("unsetMeta", () -> {
            String key = value(INPUT_META_KEY);
            if (key.isEmpty()) return;
            edit(LpEditOp.GROUP_META_UNSET, groupName, key, "");
        });

        handlers.put("setPrefix", () -> setChatMeta(LpEditOp.GROUP_PREFIX_SET, INPUT_PREFIX_PRIORITY, INPUT_PREFIX_VALUE));
        handlers.put("unsetPrefix", () -> edit(LpEditOp.GROUP_PREFIX_UNSET, groupName, priority(INPUT_PREFIX_PRIORITY), ""));
        handlers.put("setSuffix", () -> setChatMeta(LpEditOp.GROUP_SUFFIX_SET, INPUT_SUFFIX_PRIORITY, INPUT_SUFFIX_VALUE));
        handlers.put("unsetSuffix", () -> edit(LpEditOp.GROUP_SUFFIX_UNSET, groupName, priority(INPUT_SUFFIX_PRIORITY), ""));

        handlers.put("setWeight", () -> edit(LpEditOp.GROUP_WEIGHT_SET, groupName,
                value(INPUT_WEIGHT).isEmpty() ? "-1" : value(INPUT_WEIGHT)));
        handlers.put("setDisplayName", () -> edit(LpEditOp.GROUP_DISPLAYNAME_SET, groupName, value(INPUT_DISPLAY_NAME)));
    }

    private void addPermission(boolean allow) {
        String node = value(INPUT_PERM);
        if (node.isEmpty()) return;
        edit(LpEditOp.GROUP_PERM_ADD, groupName, node, String.valueOf(allow),
                value(INPUT_PERM_CONTEXTS), LpFormat.durationSeconds(value(INPUT_PERM_DURATION)));
        clearInput(INPUT_PERM);
    }

    private void addParent() {
        String parent = value(INPUT_PARENT);
        if (parent.isEmpty()) return;
        edit(LpEditOp.GROUP_PARENT_ADD, groupName, parent, "");
        clearInput(INPUT_PARENT);
    }

    private void setMeta() {
        String key = value(INPUT_META_KEY);
        if (key.isEmpty()) return;
        edit(LpEditOp.GROUP_META_SET, groupName, key, value(INPUT_META_VALUE), "");
    }

    private void setChatMeta(LpEditOp op, String priorityInput, String valueInput) {
        String text = value(valueInput);
        if (text.isEmpty()) return;
        edit(op, groupName, priority(priorityInput), text, "");
    }

    private String priority(String inputId) {
        String raw = value(inputId);
        return raw.isEmpty() ? DEFAULT_PRIORITY : raw;
    }
}
