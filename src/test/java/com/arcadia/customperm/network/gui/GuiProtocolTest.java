/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name-based parts of the admin interface protocol. A client sends page ids and action names as
 * strings, so resolution must be exact: no case folding, no partial match. Codec round trips need
 * netty and Minecraft on the classpath and run as GameTests (GuiPayloadCodecGameTest).
 */
class GuiProtocolTest {

    @Test
    void pagesAndActionsResolveOnlyByExactName() {
        for (GuiPage page : GuiPage.values()) assertEquals(page, GuiPage.fromId(page.id()));
        assertNull(GuiPage.fromId("DASHBOARD"));
        assertNull(GuiPage.fromId(""));
        for (GuiAction action : GuiAction.values()) assertEquals(action, GuiAction.fromName(action.name()));
        assertNotNull(GuiAction.fromName("RELOAD"));
        assertNull(GuiAction.fromName("reload"));
    }

    @Test
    void everyActionFitsInOnePacket() {
        for (GuiAction action : GuiAction.values()) {
            assertTrue(action.arity() <= GuiCodecs.CLIENT_ARGS_MAX, action + " takes more arguments than a packet carries");
        }
    }

    @Test
    void areaBitsAreDistinct() {
        int seen = 0;
        for (GuiArea area : GuiArea.values()) {
            assertEquals(0, seen & area.bit(), area + " shares a bit");
            seen |= area.bit();
            assertTrue(area.node().startsWith("customperm.manage."), area + " node outside the manage namespace");
        }
    }
}
