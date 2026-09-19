/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.cluster.Cluster;
import com.arcadia.customperm.cluster.ClusterGate;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cluster mode as far as a single GameTest server can see it: switched on without what it needs, the server
 * keeps running alone and says why; switched off again, the alert goes. Arcadia Lib is never on the GameTest
 * classpath, so this also proves CustomPerm starts and decides without it.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ClusterGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "cluster")
    public static void clusterWithoutWhatItNeedsRunsAloneAndSaysWhy(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var settings = CustomPerm.configManager.getSettings().cluster;
        if (Cluster.state() != ClusterGate.State.OFF) fail("Cluster mode must be off by default, got " + Cluster.state());
        ClusterGate.State expected = CustomPerm.isLuckPermsActive() ? ClusterGate.State.LUCKPERMS
                : server.isDedicatedServer() ? ClusterGate.State.NO_ARCADIA_LIB : ClusterGate.State.SINGLEPLAYER;
        try {
            settings.enabled = true;
            Cluster.onServerStarted(new ServerStartedEvent(server));
            if (Cluster.state() != expected) fail("Expected " + expected + ", got " + Cluster.state());
            if (Cluster.serverName() != null) fail("A server running alone has no cluster name.");
            boolean alert = AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE);
            if (alert == (expected == ClusterGate.State.LUCKPERMS)) {
                fail("The cluster alert must be raised unless LuckPerms decides, alert=" + alert + " state=" + expected);
            }
        } finally {
            settings.enabled = false;
            Cluster.onServerStarted(new ServerStartedEvent(server));
        }
        if (Cluster.state() != ClusterGate.State.OFF) fail("Switched off again, cluster mode must be OFF.");
        if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) fail("Switched off, the cluster alert must go.");
        helper.succeed();
    }

    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }
}
