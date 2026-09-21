/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.ConfigSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public interface PermissionService {

    /**
     * What the entries naming a server say about {@code node} for this source, see
     * {@link PermissionResolver#checkServerScoped}. UNSET for a backend that does not keep such entries apart.
     */
    default Tristate checkServerScoped(net.minecraft.commands.CommandSourceStack source, String node) {
        return Tristate.UNSET;
    }

    /**
     * Explicit value of {@code node} for the source's player, as stored by the backend, with no operator
     * logic. Sources that are not players (console, command blocks, functions) are always
     * {@link Tristate#UNSET}: the vanilla permission level decides for them, so the console can never be
     * locked out.
     */
    Tristate check(CommandSourceStack source, String node);

    /**
     * Effective permission: an explicit ALLOW or DENY decides, operators included; a node that is not
     * set falls back to op level 2. This is what makes an operator restrictable: being op only matters
     * where nothing says otherwise.
     */
    default boolean hasPermission(CommandSourceStack source, String node) {
        return switch (check(source, node)) {
            case ALLOW -> true;
            case DENY -> false;
            case UNSET -> source.hasPermission(2);
        };
    }

    /**
     * Whether {@code node} is explicitly granted, operator level ignored. Used for delegation nodes such
     * as {@code customperm.gui.aliases.edit}, whose whole point is to tell operators apart.
     */
    default boolean hasGrantedNode(CommandSourceStack source, String node) {
        return check(source, node) == Tristate.ALLOW;
    }

    /**
     * The chat prefix and suffix this backend gives {@code player}, for the name decoration. Nothing by
     * default: a backend that grants nothing decorates nothing either.
     */
    default ChatMeta chatMeta(ServerPlayer player) {
        return ChatMeta.NONE;
    }

    /**
     * Called after each successful hot reload with the new snapshot. No-op by default: the internal
     * backend reads the live config and LuckPerms keeps its own data.
     */
    default void onConfigReload(ConfigSnapshot snapshot) {}

    static PermissionService get() {
        return CustomPerm.permissions;
    }
}
