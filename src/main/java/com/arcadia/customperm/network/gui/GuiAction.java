/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

/**
 * Every change the admin interface can ask the server for. This enum is the whole write surface
 * of the interface: {@link GuiRequestHandler} switches over it and nothing else, so an operation
 * that is not listed here cannot be performed from a packet.
 *
 * <p>Arguments travel as a list of strings, checked against {@link #arity()} before anything runs.
 * Each value documents its arguments. {@link #area()} names the write node required on top of op
 * level 2; {@code null} means the action changes no configuration and needs op level 2 only, like
 * its text command.
 */
public enum GuiAction {

    /** {@code []} Re-reads every config file from disk, like {@code /customperm reload}. */
    RELOAD(0, null),

    /** {@code [command]} Exposes a root command, like {@code /customperm command add}. */
    COMMAND_EXPOSE(1, GuiArea.COMMANDS),
    /** {@code [command]} Stops exposing a root command, like {@code /customperm command remove}. */
    COMMAND_HIDE(1, GuiArea.COMMANDS),
    /** {@code [command, "true"|"false"]} Keeps or drops the command's original requirement. */
    COMMAND_KEEP_ORIGINAL(2, GuiArea.COMMANDS),

    /** {@code [alias, firstStep]} Creates an alias; refused when the name is taken. */
    ALIAS_CREATE(2, GuiArea.ALIASES),
    /** {@code [alias]} Deletes an alias, restoring a command it shadowed. */
    ALIAS_DELETE(1, GuiArea.ALIASES),
    /** {@code [alias, command]} Appends a step. */
    ALIAS_STEP_ADD(2, GuiArea.ALIASES),
    /** {@code [alias, index, command]} Replaces one step (0-based index). */
    ALIAS_STEP_SET(3, GuiArea.ALIASES),
    /** {@code [alias, from, to]} Moves one step (0-based indexes). */
    ALIAS_STEP_MOVE(3, GuiArea.ALIASES),
    /** {@code [alias, index]} Removes one step; the alias is deleted with its last step. */
    ALIAS_STEP_REMOVE(2, GuiArea.ALIASES);

    private final int arity;
    private final GuiArea area;

    GuiAction(int arity, GuiArea area) {
        this.arity = arity;
        this.area = area;
    }

    public int arity() {
        return arity;
    }

    public GuiArea area() {
        return area;
    }

    /** Resolves an action name received from a client; {@code null} for an unknown name. */
    public static GuiAction fromName(String name) {
        for (GuiAction action : values()) {
            if (action.name().equals(name)) return action;
        }
        return null;
    }
}
