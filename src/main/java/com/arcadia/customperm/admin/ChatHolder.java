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

    /** {@code context} as typed ({@code world=the_nether}), null or blank for everywhere. */
    AdminResult add(boolean suffix, int priority, String text, long seconds, String context);

    AdminResult remove(boolean suffix, int priority, String context);

    AdminResult clear(boolean suffix, String context);

    static ChatHolder grade(MinecraftServer server, String gradeName) {
        return new ChatHolder() {
            @Override
            public AdminResult add(boolean suffix, int priority, String text, long seconds, String context) {
                return GradeAdmin.addChat(server, gradeName, suffix, priority, text, seconds, context);
            }

            @Override
            public AdminResult remove(boolean suffix, int priority, String context) {
                return GradeAdmin.removeChat(server, gradeName, suffix, priority, context);
            }

            @Override
            public AdminResult clear(boolean suffix, String context) {
                return GradeAdmin.clearChat(server, gradeName, suffix, context);
            }
        };
    }

    static ChatHolder player(MinecraftServer server, UUID uuid, String displayName) {
        return new ChatHolder() {
            @Override
            public AdminResult add(boolean suffix, int priority, String text, long seconds, String context) {
                return UserAdmin.addChat(server, uuid, displayName, suffix, priority, text, seconds, context);
            }

            @Override
            public AdminResult remove(boolean suffix, int priority, String context) {
                return UserAdmin.removeChat(server, uuid, displayName, suffix, priority, context);
            }

            @Override
            public AdminResult clear(boolean suffix, String context) {
                return UserAdmin.clearChat(server, uuid, displayName, suffix, context);
            }
        };
    }
}
