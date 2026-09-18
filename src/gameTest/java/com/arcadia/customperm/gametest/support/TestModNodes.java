/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.arcadia.customperm.CustomPerm;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Nodes declared the way another mod would, under a namespace that is not CustomPerm's, so the GameTests
 * can check what the permission handler answers for them. Their defaults differ on purpose: a check that
 * answers the default is told apart from one that answers a grant or a refusal.
 */
@EventBusSubscriber(modid = CustomPerm.MODID)
public final class TestModNodes {

    /** Defaults to true: a refusal can only come from CustomPerm. */
    public static final PermissionNode<Boolean> OPEN =
            new PermissionNode<>("cptest", "probe.open", PermissionTypes.BOOLEAN, (player, uuid, context) -> true);
    /** Defaults to false: a grant can only come from CustomPerm. */
    public static final PermissionNode<Boolean> SHUT =
            new PermissionNode<>("cptest", "probe.shut", PermissionTypes.BOOLEAN, (player, uuid, context) -> false);
    /** A number, which CustomPerm has no storage for: always its default. */
    public static final PermissionNode<Integer> LIMIT =
            new PermissionNode<>("cptest", "probe.limit", PermissionTypes.INTEGER, (player, uuid, context) -> 7);

    private TestModNodes() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(OPEN, SHUT, LIMIT);
    }
}
