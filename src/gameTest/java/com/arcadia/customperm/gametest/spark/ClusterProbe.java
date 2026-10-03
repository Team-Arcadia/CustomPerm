/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.spark;

import com.arcadia.customperm.cluster.Cluster;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * S04 of the test procedure, server half: every 30 s, one log line with what a heap summary cannot show, the
 * sequence numbers GapReader still waits for and the live thread count. tools/spark_cluster.py reads them.
 * Only active in the sparkClusterA and sparkClusterB runs.
 */
@EventBusSubscriber(modid = "customperm")
public final class ClusterProbe {
    private static final boolean ACTIVE = Boolean.getBoolean("customperm.sparkCluster");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EVERY_TICKS = 30 * 20;
    private static int ticks;

    private ClusterProbe() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ACTIVE || ++ticks < EVERY_TICKS) return;
        ticks = 0;
        long collections = 0;
        long collectionMs = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            collections += Math.max(0, gc.getCollectionCount());
            collectionMs += Math.max(0, gc.getCollectionTime());
        }
        LOGGER.info("[spark-cluster] pending={} threads={} pollerBytes={} gcCount={} gcMs={}", pending(),
                ManagementFactory.getThreadMXBean().getThreadCount(), pollerAllocatedBytes(), collections, collectionMs);
    }

    /** Bytes allocated so far by the cluster poller thread, -1 when it is not running. */
    private static long pollerAllocatedBytes() {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean threads)) return -1;
        long total = -1;
        for (java.lang.Thread thread : java.lang.Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals("CustomPerm-Cluster")) {
                total = Math.max(0, total) + threads.getThreadAllocatedBytes(thread.threadId());
            }
        }
        return total;
    }

    /** Sequence numbers missing in both readers, or -1 while the cluster is not running. */
    private static int pending() {
        try {
            Object service = field(Cluster.class, "service").get(null);
            if (service == null) return -1;
            int total = 0;
            for (String reader : new String[]{"logReader", "useReader"}) {
                Object gapReader = field(service.getClass(), reader).get(service);
                Map<?, ?> missing = (Map<?, ?>) field(gapReader.getClass(), "missing").get(gapReader);
                // Read from another thread than the poller's: a size is enough, and a stale one only delays the verdict.
                total += missing.size();
            }
            return total;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cluster internals moved; update ClusterProbe", e);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
