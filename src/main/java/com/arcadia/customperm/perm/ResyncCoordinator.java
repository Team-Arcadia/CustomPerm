/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks command-tree resyncs for one server lifecycle.
 *
 * <p>All operations are synchronized so closing a lifecycle cannot race with
 * an event adding work after the pending set has been cleared.</p>
 */
final class ResyncCoordinator {

    private final Set<UUID> pending = new HashSet<>();
    private boolean active = true;

    synchronized boolean schedule(UUID uuid) {
        return active && pending.add(uuid);
    }

    synchronized boolean complete(UUID uuid) {
        pending.remove(uuid);
        return active;
    }

    synchronized void close() {
        active = false;
        pending.clear();
    }
}
