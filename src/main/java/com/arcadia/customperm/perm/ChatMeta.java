/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

/**
 * The chat prefix and suffix a backend gives one player, each {@code null} when there is none. Raw text
 * with {@code &} colour codes, as an admin typed it; {@code chat/LegacyText} turns it into a component.
 */
public record ChatMeta(String prefix, String suffix) {

    public static final ChatMeta NONE = new ChatMeta(null, null);

    public boolean isEmpty() {
        return (prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty());
    }
}
