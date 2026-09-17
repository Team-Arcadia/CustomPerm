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
    RELOAD(0, null);

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
