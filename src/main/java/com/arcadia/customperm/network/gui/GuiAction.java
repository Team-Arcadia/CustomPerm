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
    RELOAD(0, GuiArea.CONFIG),

    /** {@code [command]} Exposes a root command, like {@code /customperm command add}. */
    COMMAND_EXPOSE(1, GuiArea.COMMANDS),
    /** {@code [command]} Stops exposing a root command, like {@code /customperm command remove}. */
    COMMAND_HIDE(1, GuiArea.COMMANDS),
    /** {@code [command, "true"|"false"]} Keeps or drops the command's original requirement. */
    COMMAND_KEEP_ORIGINAL(2, GuiArea.COMMANDS),
    /** {@code ["true"|"false"]} Whether every command reads its node, like {@code /customperm command gateall}. */
    COMMAND_GATE_ALL(1, GuiArea.COMMANDS),

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
    ALIAS_STEP_REMOVE(2, GuiArea.ALIASES),

    /** {@code [name, max, windowSeconds]} Creates or redefines a rule, enabled, keeping its persistence mode. */
    RATELIMIT_SET(3, GuiArea.RATE_LIMITS),
    /** {@code [name]} Enforces a rule again. */
    RATELIMIT_ENABLE(1, GuiArea.RATE_LIMITS),
    /** {@code [name]} Stops enforcing a rule, keeping its numbers. */
    RATELIMIT_DISABLE(1, GuiArea.RATE_LIMITS),
    /** {@code [name]} Deletes a rule. */
    RATELIMIT_REMOVE(1, GuiArea.RATE_LIMITS),
    /** {@code [name, "world_save"|"immediate"]} When the rule's usage history is written. */
    RATELIMIT_PERSISTENCE(2, GuiArea.RATE_LIMITS),

    /** {@code [grade]} Creates an internal grade. */
    GRADE_CREATE(1, GuiArea.GRADES),
    /** {@code [grade]} Deletes a grade and unassigns it from every player. */
    GRADE_DELETE(1, GuiArea.GRADES),
    /** {@code [grade, node, "allow"|"deny"]} Adds an ALLOW or a DENY node. */
    GRADE_NODE_ADD(3, GuiArea.GRADES),
    /** {@code [grade, node, "allow"|"deny"]} Removes an ALLOW or a DENY node. */
    GRADE_NODE_REMOVE(3, GuiArea.GRADES),
    /** {@code [grade, weight]} Sets the tie-break weight, which decides between grades at the same specificity. */
    GRADE_WEIGHT_SET(2, GuiArea.GRADES),
    /** {@code [grade, parent]} Makes the grade inherit another; a cycle is refused. */
    GRADE_PARENT_ADD(2, GuiArea.GRADES),
    /** {@code [grade, parent]} Stops inheriting it. */
    GRADE_PARENT_REMOVE(2, GuiArea.GRADES),
    /** {@code [playerName, grade]} Assigns a grade to a player online or known to the server. */
    GRADE_ASSIGN(2, GuiArea.GRADES),
    /** {@code [playerUuid, grade]} Unassigns a grade, by UUID so unnamed entries can be cleaned up. */
    GRADE_UNASSIGN(2, GuiArea.GRADES),
    /** {@code [grade, parent]} Refuses a grade wherever this one would inherit it. */
    GRADE_PARENT_DENY(2, GuiArea.GRADES),
    /** {@code [grade, parent]} Stops refusing it. */
    GRADE_PARENT_ALLOW(2, GuiArea.GRADES),
    /** {@code [playerName, grade]} Makes a player refuse a grade, wherever one of theirs would bring it. */
    GRADE_REFUSE(2, GuiArea.GRADES),
    /** {@code [playerUuid, grade]} Stops refusing it, by UUID like unassigning. */
    GRADE_ACCEPT(2, GuiArea.GRADES),
    /** {@code [grade]} Makes a grade apply to every player; an empty name clears the default grade. */
    GRADE_DEFAULT(1, GuiArea.GRADES),

    /** {@code [playerName, node, "allow"|"deny"]} Adds a node the player carries themselves, above their grades. */
    USER_NODE_ADD(3, GuiArea.GRADES),
    /** {@code [playerUuid, node, "allow"|"deny"]} Removes one, by UUID so an unnamed entry can be cleaned up. */
    USER_NODE_REMOVE(3, GuiArea.GRADES),

    /** {@code ["true"|"false"]} Records player commands, like {@code /customperm log record}. */
    LOG_PLAYERS(1, GuiArea.LOGS),
    /** {@code ["true"|"false"]} Masks the arguments of sensitive commands, like {@code /customperm log mask}. */
    LOG_MASK(1, GuiArea.LOGS);

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
