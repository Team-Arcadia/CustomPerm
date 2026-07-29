/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.perm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResyncCoordinatorTest {

    @Test
    void coalescesEventsUntilScheduledWorkCompletes() {
        ResyncCoordinator coordinator = new ResyncCoordinator();
        UUID uuid = UUID.randomUUID();

        assertTrue(coordinator.schedule(uuid));
        assertFalse(coordinator.schedule(uuid));
        assertTrue(coordinator.complete(uuid));
        assertTrue(coordinator.schedule(uuid));
    }

    @Test
    void closingLifecycleInvalidatesQueuedWork() {
        ResyncCoordinator coordinator = new ResyncCoordinator();
        UUID uuid = UUID.randomUUID();

        assertTrue(coordinator.schedule(uuid));
        coordinator.close();

        assertFalse(coordinator.complete(uuid));
        assertFalse(coordinator.schedule(uuid));
    }

    @Test
    void oldLifecycleCannotBlockSamePlayerOnNewLifecycle() {
        UUID uuid = UUID.randomUUID();
        ResyncCoordinator oldCoordinator = new ResyncCoordinator();

        assertTrue(oldCoordinator.schedule(uuid));
        oldCoordinator.close();

        ResyncCoordinator newCoordinator = new ResyncCoordinator();
        assertTrue(newCoordinator.schedule(uuid));
    }
}
