/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.cluster.ClusterGate.State;
import com.arcadia.customperm.config.SettingsConfig;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class ClusterGateTest {

    private static final BooleanSupplier CONNECTED = () -> true;
    private static final BooleanSupplier MUST_NOT_ASK = () -> { throw new AssertionError("database asked too early"); };

    @Test
    void offUnlessEnabled() {
        assertEquals(State.OFF, ClusterGate.decide(false, true, false, null, MUST_NOT_ASK));
        assertNull(ClusterGate.reason(State.OFF, null));
    }

    @Test
    void conditionsAreReportedInOrder() {
        assertEquals(State.LUCKPERMS, ClusterGate.decide(true, true, false, null, MUST_NOT_ASK));
        assertEquals(State.SINGLEPLAYER, ClusterGate.decide(true, false, false, "1.3.0", MUST_NOT_ASK));
        assertEquals(State.NO_ARCADIA_LIB, ClusterGate.decide(true, false, true, null, MUST_NOT_ASK));
        assertEquals(State.ARCADIA_LIB_TOO_OLD, ClusterGate.decide(true, false, true, "1.2.15", MUST_NOT_ASK));
        assertEquals(State.NO_DATABASE, ClusterGate.decide(true, false, true, "1.3.0", () -> false));
        assertEquals(State.READY, ClusterGate.decide(true, false, true, "1.3.0", CONNECTED));
        assertEquals(State.READY, ClusterGate.decide(true, false, true, "1.4.2", CONNECTED));
    }

    @Test
    void aChangedArcadiaLibApiIsReportedNotThrown() {
        State state = ClusterGate.decide(true, false, true, "1.9.0", () -> { throw new NoSuchMethodError("isDatabaseActive"); });
        assertEquals(State.ARCADIA_LIB_INCOMPATIBLE, state);
        assertTrue(ClusterGate.reason(state, "1.9.0").contains("1.9.0"));
    }

    @Test
    void everyStateThatKeepsTheServerAloneSaysWhy() {
        for (State state : State.values()) {
            if (state == State.OFF || state == State.READY) continue;
            String reason = ClusterGate.reason(state, "1.2.0");
            assertNotNull(reason, state.name());
            assertFalse(reason.isBlank(), state.name());
        }
        assertTrue(ClusterGate.reason(State.ARCADIA_LIB_TOO_OLD, "1.2.14").contains("1.2.14"));
    }

    @Test
    void settingsWithoutClusterGetItOffByDefault() {
        SettingsConfig settings = new Gson().fromJson("{\"gateAllCommands\": false}", SettingsConfig.class);
        settings.cluster = null;
        settings.normalize();
        assertNotNull(settings.cluster);
        assertFalse(settings.cluster.enabled);
        assertTrue(settings.cluster.share.grades);
        assertFalse(settings.cluster.share.rateLimitCounters);
        assertEquals(SettingsConfig.Cluster.WHEN_LOST_LAST_KNOWN, settings.cluster.whenDatabaseLost);
    }

    @Test
    void clusterSettingsAreClampedAndCompleted() {
        SettingsConfig settings = new Gson().fromJson(
                "{\"cluster\": {\"enabled\": true, \"pollSeconds\": 0, \"share\": null, \"whenDatabaseLost\": \"nope\"}}",
                SettingsConfig.class);
        settings.normalize();
        assertTrue(settings.cluster.enabled);
        assertEquals(SettingsConfig.Cluster.POLL_SECONDS_MIN, settings.cluster.pollSeconds);
        assertNotNull(settings.cluster.share);
        assertEquals(SettingsConfig.Cluster.WHEN_LOST_LAST_KNOWN, settings.cluster.whenDatabaseLost);

        settings.cluster.pollSeconds = 600;
        settings.normalize();
        assertEquals(SettingsConfig.Cluster.POLL_SECONDS_MAX, settings.cluster.pollSeconds);
    }
}
