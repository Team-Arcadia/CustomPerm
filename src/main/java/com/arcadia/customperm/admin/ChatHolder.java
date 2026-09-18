/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * A grade or a player as something that carries prefixes and suffixes, so the commands and the interface
 * write one path for both.
 */
public interface ChatHolder {

    AdminResult add(boolean suffix, int priority, String text, long seconds);

    AdminResult remove(boolean suffix, int priority);

    AdminResult clear(boolean suffix);

    static ChatHolder grade(MinecraftServer server, String gradeName) {
        return new ChatHolder() {
            @Override
            public AdminResult add(boolean suffix, int priority, String text, long seconds) {
                return GradeAdmin.addChat(server, gradeName, suffix, priority, text, seconds);
            }

            @Override
            public AdminResult remove(boolean suffix, int priority) {
                return GradeAdmin.removeChat(server, gradeName, suffix, priority);
            }

            @Override
            public AdminResult clear(boolean suffix) {
                return GradeAdmin.clearChat(server, gradeName, suffix);
            }
        };
    }

    static ChatHolder player(MinecraftServer server, UUID uuid, String displayName) {
        return new ChatHolder() {
            @Override
            public AdminResult add(boolean suffix, int priority, String text, long seconds) {
                return UserAdmin.addChat(server, uuid, displayName, suffix, priority, text, seconds);
            }

            @Override
            public AdminResult remove(boolean suffix, int priority) {
                return UserAdmin.removeChat(server, uuid, displayName, suffix, priority);
            }

            @Override
            public AdminResult clear(boolean suffix) {
                return UserAdmin.clearChat(server, uuid, displayName, suffix);
            }
        };
    }
}
