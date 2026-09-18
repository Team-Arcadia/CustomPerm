/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.log;

/**
 * One line of the activity log.
 *
 * @param time    epoch milliseconds
 * @param actor   player name, {@code Server} for the console, or the LuckPerms source name
 * @param actorId player UUID, empty when the actor is not a player
 * @param source  where it came from: {@code command}, {@code interface}, {@code luckperms-editor},
 *                {@code luckperms} (changes made with /lp or the web editor), {@code player}
 * @param action  what was done: the command typed, or the interface action and its arguments
 * @param success whether it was applied; player commands are recorded before they run and are always true
 * @param result  the message the admin saw, or the LuckPerms target; empty for player commands
 */
public record LogEntry(long time, String actor, String actorId, String source, String action, boolean success,
                       String result) {

    public static final String SOURCE_COMMAND = "command";
    public static final String SOURCE_INTERFACE = "interface";
    public static final String SOURCE_LUCKPERMS_EDITOR = "luckperms-editor";
    public static final String SOURCE_LUCKPERMS = "luckperms";
    public static final String SOURCE_PLAYER = "player";
    /** A temporary entry that ran out, removed by CustomPerm itself. */
    public static final String SOURCE_EXPIRY = "expiry";
}
