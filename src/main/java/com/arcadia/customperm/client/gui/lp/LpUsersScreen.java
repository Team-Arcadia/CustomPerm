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
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Player list. Shows whoever LuckPerms currently has in memory — which in practice means the
 * online players — and resolves an exact username on demand, so an offline player can be edited
 * without waiting for them to log in.
 *
 * <p>The search term is part of the scope, not a client-side filter: LuckPerms owns the
 * username-to-UUID mapping, and a client cannot resolve a player it has never seen.
 */
public final class LpUsersScreen extends AbstractLpScreen {

    private static final String INPUT_SEARCH = "userSearch";

    private String search = "";

    public LpUsersScreen() {
        super(Component.literal("LuckPerms - Players"));
    }

    @Override
    protected String scope() {
        return RequestLpSyncPayload.SCOPE_USERS;
    }

    @Override
    protected String target() {
        return search;
    }

    @Override
    protected String templateId() {
        return "customperm:ui/lp_users";
    }

    @Override
    protected void fill(LpDto.Snapshot snapshot, Map<String, String> data,
                        Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        List<LpDto.UserDto> users = snapshot.users();
        data.put("searching", String.valueOf(!search.isEmpty()));
        data.put("searchTerm", search);
        data.put("noUsers", String.valueOf(users.isEmpty()));
        data.put("users", String.valueOf(users.size()));

        handlers.put("search", this::runSearch);
        handlers.put("clearSearch", () -> {
            clearInput(INPUT_SEARCH);
            search = "";
            requestRefresh();
        });
        submits.put(INPUT_SEARCH, text -> runSearch());

        for (int i = 0; i < users.size(); i++) {
            LpDto.UserDto user = users.get(i);
            data.put("user.name." + i, user.username());
            data.put("user.status." + i, user.online() ? "online" : "offline");
            data.put("user.primary." + i, user.primaryGroup());
            data.put("user.parents." + i, LpFormat.join(user.parents()));
            data.put("user.nodeCount." + i, String.valueOf(user.nodeCount()));

            String editKey = "user.edit." + i;
            data.put("user.editAction." + i, editKey);
            handlers.put(editKey, () -> open(new LpUserScreen(user.uuid(), user.username())));
        }
    }

    private void runSearch() {
        search = value(INPUT_SEARCH);
        requestRefresh();
    }
}
