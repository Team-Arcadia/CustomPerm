/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.notify;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The conditions an admin must hear about, one active message per condition. Pure Java so the
 * de-duplication rule is unit-testable: raising an alert that is already active with the same
 * message reports "nothing new", which is what keeps a failure hit on every permission check from
 * turning into a chat message per check.
 */
public final class AdminAlerts {

    public enum Key {
        /** LuckPerms is installed but CustomPerm stopped using it until restart. */
        LUCKPERMS_UNAVAILABLE,
        /** A config file could not be loaded; saves are suspended. */
        CONFIG_LOAD_FAILED,
        /** Cluster mode is switched on but this server runs alone, or its store is unreachable; the message says which. */
        CLUSTER_UNAVAILABLE,
        /** Another running server of the cluster uses this server's name. */
        CLUSTER_NAME_TAKEN
    }

    private final Map<Key, String> active = new ConcurrentHashMap<>();

    /**
     * Activates {@code key} with {@code message}.
     *
     * @return true when admins should be told: the alert was not active, or its message changed
     */
    public boolean raise(Key key, String message) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
        return !message.equals(active.put(key, message));
    }

    /** @return true when the alert was active and is now resolved */
    public boolean clear(Key key) {
        return active.remove(key) != null;
    }

    public boolean isActive(Key key) {
        return active.containsKey(key);
    }

    /** Active alerts in declaration order, detached from later changes. */
    public Map<Key, String> snapshot() {
        Map<Key, String> copy = new EnumMap<>(Key.class);
        copy.putAll(active);
        return copy;
    }
}
