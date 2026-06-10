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
