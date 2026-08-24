/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.lp;

/**
 * The mutations the LuckPerms editor screens are allowed to request. This is the whole write
 * surface of the GUI: {@code LuckPermsAdminService} switches over this enum and nothing else,
 * so an operation that is not listed here simply cannot be performed from a client packet.
 * <p>
 * Arguments travel as a {@code List<String>} rather than a per-op record. The alternative — one
 * payload type per operation — would mean thirty codecs and thirty registrations for what is,
 * on the wire, always a short list of strings; {@link #arity} is what keeps that honest, since
 * a packet whose argument count does not match its operation is rejected before it reaches any
 * LuckPerms call.
 * <p>
 * Conventions shared by every operation:
 * <ul>
 *   <li>groups and tracks are addressed by name, users by UUID string (see {@code LpDto.UserDto});</li>
 *   <li>{@code contexts} is the flattened {@code key=value;key=value} form, empty for global;</li>
 *   <li>{@code duration} is in seconds, {@code 0} meaning permanent;</li>
 *   <li>booleans are {@code "true"} / {@code "false"}.</li>
 * </ul>
 */
public enum LpEditOp {

    /** {@code [name]} */
    GROUP_CREATE(1),
    /** {@code [name]} */
    GROUP_DELETE(1),
    /** {@code [group, node, value, contexts, duration]} */
    GROUP_PERM_ADD(5),
    /** {@code [group, node, contexts]} */
    GROUP_PERM_REMOVE(3),
    /** {@code [group, parent, contexts]} */
    GROUP_PARENT_ADD(3),
    /** {@code [group, parent, contexts]} */
    GROUP_PARENT_REMOVE(3),
    /** {@code [group, key, value, contexts]} */
    GROUP_META_SET(4),
    /** {@code [group, key, contexts]} */
    GROUP_META_UNSET(3),
    /** {@code [group, priority, value, contexts]} */
    GROUP_PREFIX_SET(4),
    /** {@code [group, priority, contexts]} */
    GROUP_PREFIX_UNSET(3),
    /** {@code [group, priority, value, contexts]} */
    GROUP_SUFFIX_SET(4),
    /** {@code [group, priority, contexts]} */
    GROUP_SUFFIX_UNSET(3),
    /** {@code [group, weight]} — a negative weight clears the node. */
    GROUP_WEIGHT_SET(2),
    /** {@code [group, displayName]} — an empty name clears the node. */
    GROUP_DISPLAYNAME_SET(2),

    /** {@code [uuid, node, value, contexts, duration]} */
    USER_PERM_ADD(5),
    /** {@code [uuid, node, contexts]} */
    USER_PERM_REMOVE(3),
    /** {@code [uuid, group, contexts, duration]} */
    USER_PARENT_ADD(4),
    /** {@code [uuid, group, contexts]} */
    USER_PARENT_REMOVE(3),
    /** {@code [uuid, key, value, contexts]} */
    USER_META_SET(4),
    /** {@code [uuid, key, contexts]} */
    USER_META_UNSET(3),
    /** {@code [uuid, priority, value, contexts]} */
    USER_PREFIX_SET(4),
    /** {@code [uuid, priority, contexts]} */
    USER_PREFIX_UNSET(3),
    /** {@code [uuid, priority, value, contexts]} */
    USER_SUFFIX_SET(4),
    /** {@code [uuid, priority, contexts]} */
    USER_SUFFIX_UNSET(3),
    /** {@code [uuid, group]} */
    USER_PRIMARY_GROUP_SET(2),
    /** {@code [uuid, track]} */
    USER_PROMOTE(2),
    /** {@code [uuid, track]} */
    USER_DEMOTE(2),

    /** {@code [name]} */
    TRACK_CREATE(1),
    /** {@code [name]} */
    TRACK_DELETE(1),
    /** {@code [track, group]} */
    TRACK_APPEND(2),
    /** {@code [track, group, index]} */
    TRACK_INSERT(3),
    /** {@code [track, group]} */
    TRACK_REMOVE(2);

    private final int arity;

    LpEditOp(int arity) {
        this.arity = arity;
    }

    /** Exact number of arguments this operation expects. */
    public int arity() {
        return arity;
    }

    /** True when this operation targets a user, and therefore takes a UUID as its first argument. */
    public boolean isUserOp() {
        return name().startsWith("USER_");
    }

    /**
     * Resolves an operation name received from a client. Returns {@code null} for an unknown
     * name instead of throwing: a malformed packet is a rejection, not a server-side exception.
     */
    public static LpEditOp fromName(String name) {
        for (LpEditOp op : values()) {
            if (op.name().equals(name)) return op;
        }
        return null;
    }
}
