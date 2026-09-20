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
    /** {@code [alias, name, type]} Declares an argument at the end of the alias's list. */
    ALIAS_PARAM_ADD(3, GuiArea.ALIASES),
    /** {@code [alias, name]} Drops an argument. */
    ALIAS_PARAM_REMOVE(2, GuiArea.ALIASES),
    /** {@code [alias, name, index]} Moves an argument (0-based index). */
    ALIAS_PARAM_MOVE(3, GuiArea.ALIASES),
    /**
     * {@code [alias, name, field, value]} Sets one property of an argument. Fields: {@code optional}
     * and {@code selectors} take true or false, {@code default} a value or nothing to clear it,
     * {@code range} {@code <min>..<max>} or nothing to clear it, {@code choices} a comma-separated
     * list or nothing to clear it.
     */
    ALIAS_PARAM_EDIT(4, GuiArea.ALIASES),

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
    /** {@code [name, scope]} Who shares the rule's budget in cluster mode: server, network or server names. */
    RATELIMIT_SCOPE(2, GuiArea.RATE_LIMITS),

    /** {@code [grade]} Creates an internal grade. */
    GRADE_CREATE(1, GuiArea.GRADES),
    /** {@code [grade]} Deletes a grade and unassigns it from every player. */
    GRADE_DELETE(1, GuiArea.GRADES),
    /**
     * {@code [grade, node, "allow"|"deny", duration, context]} Adds an ALLOW or a DENY node; an empty duration
     * is permanent, an empty context applies everywhere, {@code world=the_nether} in that world only.
     */
    GRADE_NODE_ADD(5, GuiArea.GRADES),
    /** {@code [grade, node, "allow"|"deny", context]} Removes an ALLOW or a DENY node, from that context. */
    GRADE_NODE_REMOVE(4, GuiArea.GRADES),
    /** {@code [grade, weight]} Sets the tie-break weight, which decides between grades at the same specificity. */
    GRADE_WEIGHT_SET(2, GuiArea.GRADES),
    /** {@code [grade, text]} Sets the name pages and listings show for the grade; empty text clears it. */
    GRADE_DISPLAYNAME_SET(2, GuiArea.GRADES),
    /**
     * {@code [grade, parent, duration, context]} Makes the grade inherit another; a cycle is refused. Empty
     * duration: for good; empty context: everywhere.
     */
    GRADE_PARENT_ADD(4, GuiArea.GRADES),
    /** {@code [grade, parent, context]} Stops inheriting it, in that context. */
    GRADE_PARENT_REMOVE(3, GuiArea.GRADES),
    /** {@code [playerName, grade, duration, context]} Assigns a grade to a player online or known to the server. */
    GRADE_ASSIGN(4, GuiArea.GRADES),
    /** {@code [playerUuid, grade, context]} Unassigns a grade, by UUID so unnamed entries can be cleaned up. */
    GRADE_UNASSIGN(3, GuiArea.GRADES),
    /** {@code [grade, parent, duration, context]} Refuses a grade wherever this one would inherit it. */
    GRADE_PARENT_DENY(4, GuiArea.GRADES),
    /** {@code [grade, parent, context]} Stops refusing it, in that context. */
    GRADE_PARENT_ALLOW(3, GuiArea.GRADES),
    /**
     * {@code [playerName, grade, duration, context]} Makes a player refuse a grade, wherever one of theirs
     * would bring it.
     */
    GRADE_REFUSE(4, GuiArea.GRADES),
    /** {@code [playerUuid, grade, context]} Stops refusing it, by UUID like unassigning. */
    GRADE_ACCEPT(3, GuiArea.GRADES),
    /** {@code [grade]} Makes a grade apply to every player; an empty name clears the default grade. */
    GRADE_DEFAULT(1, GuiArea.GRADES),

    /**
     * {@code [playerName, node, "allow"|"deny", duration, context]} Adds a node the player carries themselves,
     * above their grades.
     */
    USER_NODE_ADD(5, GuiArea.GRADES),
    /**
     * {@code [uuid, nickname]} Sets the name a player is shown under; empty clears it. Allowed under LuckPerms
     * too: a nickname is not a permission.
     */
    USER_NICK_SET(2, GuiArea.GRADES),
    /** {@code [playerUuid, node, "allow"|"deny", context]} Removes one, by UUID so an unnamed entry can be cleaned up. */
    USER_NODE_REMOVE(4, GuiArea.GRADES),
    /**
     * {@code [playerName, track, context]} Moves a player one rung up a track, like {@code /customperm track
     * promote}; with a context, among the grades held there only. No area: {@code customperm.manage.grades} or
     * {@code customperm.track.<track>} is checked for the track named, like the text command.
     */
    TRACK_PROMOTE(3, null),
    /** {@code [playerName, track, context]} Moves a player one rung down, off the track from its first rung. */
    TRACK_DEMOTE(3, null),

    /**
     * {@code ["true"|"false"]} Reads LuckPerms and returns what an import would do, writing nothing. The
     * expose flag says whether a translated {@code minecraft.command} node also exposes its command.
     */
    IMPORT_PREVIEW(1, GuiArea.GRADES),
    /** {@code ["merge"|"replace"]} Applies what this admin previewed, and only that. */
    IMPORT_APPLY(1, GuiArea.GRADES),
    /** {@code []} Reads the grades and returns what an export to LuckPerms would write, writing nothing. */
    EXPORT_PREVIEW(0, GuiArea.LUCKPERMS),
    /** {@code ["merge"|"replace"]} Writes what this admin previewed into LuckPerms, in the background. */
    EXPORT_APPLY(1, GuiArea.LUCKPERMS),

    /**
     * {@code [grade, "prefix"|"suffix", priority, text, duration, context]} Gives a grade a prefix or suffix
     * at that priority, replacing one already there; an empty duration is for good, an empty context everywhere.
     */
    GRADE_CHAT_ADD(6, GuiArea.GRADES),
    /** {@code [grade, "prefix"|"suffix", priority, context]} Removes the one at that priority, in that context. */
    GRADE_CHAT_REMOVE(4, GuiArea.GRADES),
    /** {@code [playerName, "prefix"|"suffix", priority, text, duration, context]} One player's own; by name. */
    USER_CHAT_ADD(6, GuiArea.GRADES),
    /** {@code [playerName, "prefix"|"suffix", priority, context]} Removes one player's own at that priority. */
    USER_CHAT_REMOVE(4, GuiArea.GRADES),
    /** {@code [grade, key, value, duration, context]} Sets a grade's meta; an empty duration is for good, an empty context everywhere. */
    GRADE_META_SET(5, GuiArea.GRADES),
    /** {@code [grade, key, context]} Removes a grade's meta of that key, in that context. */
    GRADE_META_UNSET(3, GuiArea.GRADES),
    /** {@code [playerName, key, value, duration, context]} One player's own meta; by name. */
    USER_META_SET(5, GuiArea.GRADES),
    /** {@code [playerName, key, context]} Removes one player's own meta of that key. */
    USER_META_UNSET(3, GuiArea.GRADES),
    /** {@code ["highest"|"stacked"]} Shows one prefix and suffix, or several in a row, like {@code names stack both}. */
    NAMES_STACK(1, GuiArea.CONFIG),
    /** {@code ["true"|"false"]} Decorates names with their prefix and suffix, like {@code /customperm names}. */
    NAMES_DECORATE(1, GuiArea.CONFIG),

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
