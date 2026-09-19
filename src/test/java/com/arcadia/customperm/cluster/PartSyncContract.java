/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.GradesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What any {@link ClusterStore} must give {@link PartSync}: run on the memory store and on SQL (H2 in MySQL mode).
 * Each server of a test gets its own store object over the same data, as two servers each open their own connections.
 */
abstract class PartSyncContract {

    /** A store object over the data of this test, a new one per call. */
    protected abstract ClusterStore store();

    /** Makes every store object of this test fail as an unreachable database would, or answer again. */
    protected abstract void setDown(boolean down);


    private static final String STEVE = "8667ba71-b85a-4004-af54-457a9734eed7";
    private static final GradesCodec CODEC = new GradesCodec();

    /** One server: its configuration and what it was told changed. */
    private static final class Node implements PartSync.Host<GradesConfig> {
        GradesConfig config = CODEC.empty();
        final List<Set<String>> changes = new ArrayList<>();
        final PartSync<GradesConfig> sync;

        Node(ClusterStore store, String name) {
            sync = new PartSync<>(CODEC, store, name, this);
        }

        @Override public GradesConfig current() { return config; }
        @Override public void changed(GradesConfig c, Set<String> holders) { changes.add(holders); }
        @Override public String label(String holder) { return holder; }

        void poll() throws ClusterStore.StoreException {
            sync.apply(sync.fetch());
        }
    }

    private static GradesConfig.Grade grade(String name, String... nodes) {
        GradesConfig.Grade g = new GradesConfig.Grade();
        g.name = name;
        g.permissions.addAll(List.of(nodes));
        return g;
    }

    @Test
    void anEmptyStoreIsSeededAndTheNextServerAdoptsIt() throws Exception {
        Node a = new Node(store(), "a");
        a.config.grades.put("vip", grade("vip", "n"));
        assertEquals(PartSync.Start.SEEDED, a.sync.start());

        Node b = new Node(store(), "b");
        b.config.grades.put("other", grade("other"));
        assertEquals(PartSync.Start.ADOPTED_REPLACED, b.sync.start());
        assertEquals(Set.of("vip"), b.config.grades.keySet());
    }

    @Test
    void aChangeReachesTheOtherServer() throws Exception {
        Node a = new Node(store(), "a");
        Node b = new Node(store(), "b");
        a.sync.start();
        b.sync.start();

        a.config.grades.put("vip", grade("vip", "n"));
        a.config.userGrades.put(STEVE, new ArrayList<>(List.of("vip")));
        assertNull(a.sync.publish());
        b.poll();
        assertEquals(Set.of("n"), b.config.grades.get("vip").permissions);
        assertEquals(List.of("vip"), b.config.userGrades.get(STEVE));

        a.config.grades.remove("vip");
        a.config.userGrades.remove(STEVE);
        assertNull(a.sync.publish());
        b.poll();
        assertFalse(b.config.grades.containsKey("vip"));
        assertFalse(b.config.userGrades.containsKey(STEVE));

        b.config.grades.put("vip", grade("vip", "again"));
        assertNull(b.sync.publish(), "A removed holder can be created again");
        a.poll();
        assertEquals(Set.of("again"), a.config.grades.get("vip").permissions);
    }

    @Test
    void twoServersChangingTheSameGradeTheSecondIsRefusedAndShownTheFirst() throws Exception {
        Node a = new Node(store(), "a");
        Node b = new Node(store(), "b");
        a.config.grades.put("vip", grade("vip"));
        a.sync.start();
        b.sync.start();

        a.config.grades.get("vip").permissions.add("from.a");
        b.config.grades.get("vip").permissions.add("from.b");
        assertNull(a.sync.publish());
        String refusal = b.sync.publish();
        assertNotNull(refusal);
        assertTrue(refusal.contains("grade:vip") && refusal.contains("changed on a"), refusal);
        assertEquals(Set.of("from.a"), b.config.grades.get("vip").permissions, "B must show A's change, not its own");
    }

    @Test
    void twoServersChangingDifferentGradesDoNotConflict() throws Exception {
        Node a = new Node(store(), "a");
        Node b = new Node(store(), "b");
        a.config.grades.put("vip", grade("vip"));
        a.config.grades.put("mod", grade("mod"));
        a.sync.start();
        b.sync.start();

        a.config.grades.get("vip").permissions.add("x");
        b.config.grades.get("mod").permissions.add("y");
        assertNull(a.sync.publish());
        assertNull(b.sync.publish());
        a.poll();
        b.poll();
        assertEquals(CODEC.split(a.config), CODEC.split(b.config));
        assertEquals(Set.of("y"), a.config.grades.get("mod").permissions);
    }

    @Test
    void anOwnWriteNumberedLaterNeverHidesAnEarlierOne() throws Exception {
        Node a = new Node(store(), "a");
        Node b = new Node(store(), "b");
        a.sync.start();
        b.sync.start();
        b.config.grades.put("first", grade("first"));
        assertNull(b.sync.publish());
        a.config.grades.put("second", grade("second"));
        assertNull(a.sync.publish());
        a.poll();
        assertTrue(a.config.grades.containsKey("first"), "B's earlier write must not be skipped by A's own later one");
        assertTrue(a.config.grades.containsKey("second"));
    }

    @Test
    void anUnreachableStoreUndoesTheChange() throws Exception {
        Node a = new Node(store(), "a");
        a.config.grades.put("vip", grade("vip"));
        a.sync.start();
        setDown(true);
        a.config.grades.get("vip").permissions.add("lost");
        assertThrows(PartSync.Unreachable.class, a.sync::publish);
        assertTrue(a.config.grades.get("vip").permissions.isEmpty(), "Refused while unreachable, the change is undone");
        setDown(false);
        a.config.grades.get("vip").permissions.add("kept");
        assertNull(a.sync.publish());
    }

    @Test
    void ownRowsComingBackChangeNothing() throws Exception {
        Node a = new Node(store(), "a");
        a.sync.start();
        a.config.grades.put("vip", grade("vip"));
        assertNull(a.sync.publish());
        int before = a.changes.size();
        assertFalse(a.sync.apply(a.sync.fetch()));
        assertEquals(before, a.changes.size());
        assertNull(a.sync.publish(), "Nothing left to write");
    }
}
