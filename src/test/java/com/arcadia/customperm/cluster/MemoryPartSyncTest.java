/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

class MemoryPartSyncTest extends PartSyncContract {

    private final MemoryStore memory = new MemoryStore();

    @Override
    protected ClusterStore store() {
        return memory;
    }

    @Override
    protected void setDown(boolean down) {
        memory.setDown(down);
    }
}
