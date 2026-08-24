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
 * Group list: the landing screen of the in-game LuckPerms editor, and the counterpart of the
 * web editor's group column. Create and delete live here; everything else about a group is on
 * {@link LpGroupScreen}, reached by clicking a row.
 */
public final class LpGroupsScreen extends AbstractLpScreen {

    private static final String INPUT_NEW_GROUP = "newGroup";

    public LpGroupsScreen() {
        super(Component.literal("LuckPerms - Groups"));
    }

    @Override
    protected String scope() {
        return RequestLpSyncPayload.SCOPE_GROUPS;
    }

    @Override
    protected String templateId() {
        return "customperm:ui/lp_groups";
    }

    @Override
    protected void fill(LpDto.Snapshot snapshot, Map<String, String> data,
                        Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        List<LpDto.GroupDto> groups = snapshot.groups();
        data.put("noGroups", String.valueOf(groups.isEmpty()));
        data.put("groups", String.valueOf(groups.size()));

        handlers.put("createGroup", this::createGroup);
        submits.put(INPUT_NEW_GROUP, text -> createGroup());

        for (int i = 0; i < groups.size(); i++) {
            LpDto.GroupDto group = groups.get(i);
            String name = group.name();

            data.put("group.name." + i, name);
            data.put("group.weight." + i, LpFormat.weight(group.weight()));
            data.put("group.nodeCount." + i, String.valueOf(group.nodeCount()));
            data.put("group.parents." + i, LpFormat.join(group.parents()));

            String editKey = "group.edit." + i;
            String deleteKey = "group.delete." + i;
            data.put("group.editAction." + i, editKey);
            data.put("group.deleteAction." + i, deleteKey);
            handlers.put(editKey, () -> open(new LpGroupScreen(name)));
            handlers.put(deleteKey, () -> edit(LpEditOp.GROUP_DELETE, name));
        }
    }

    private void createGroup() {
        String name = value(INPUT_NEW_GROUP);
        if (name.isEmpty()) return;
        edit(LpEditOp.GROUP_CREATE, name);
        clearInput(INPUT_NEW_GROUP);
    }
}
