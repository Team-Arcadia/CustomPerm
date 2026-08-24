/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.client.gui.lp.AbstractLpScreen;
import com.arcadia.customperm.client.gui.lp.LpGroupsScreen;
import com.arcadia.customperm.client.gui.lp.LpTracksScreen;
import com.arcadia.customperm.client.gui.lp.LpUsersScreen;
import com.arcadia.customperm.network.GuiSyncPayload;
import com.arcadia.customperm.network.lp.LpDto;
import net.minecraft.client.Minecraft;

/**
 * All code that references TesseraUI types (the screens, which extend {@code TesseraScreen})
 * lives here. This class is loaded LAZILY — only when one of its static methods is actually
 * invoked, which callers do exclusively behind an {@code isTesseraUiPresent()} guard.
 *
 * <p><strong>Why this matters.</strong> Always-loaded client classes ({@code CustomPermClientCommands},
 * registered via {@code @EventBusSubscriber(Dist.CLIENT)}, and {@code ClientNetworkHandler}) must NOT
 * reference screen types in their own bytecode. If they did, JVM verification of those classes at
 * mod-load time would eagerly resolve the screens' supertype {@code com.tesseraui.TesseraScreen} and,
 * on a client that has CustomPerm but not TesseraUI, fail with {@code NoClassDefFoundError} — making
 * the whole mod fail to load. By routing every screen interaction through plain static calls into
 * this separate class, the verifier never loads TesseraUI types for the always-loaded classes, and
 * this bridge is only ever linked on a client where TesseraUI is present.</p>
 */
public final class TesseraGuiBridge {

    private TesseraGuiBridge() {
    }

    /** Opens the requested screen (or the landing hub for any unknown/absent key). */
    public static void open(String screen) {
        Minecraft mc = Minecraft.getInstance();
        switch (screen) {
            case "grades" -> mc.setScreen(new GradesScreen());
            case "aliases" -> mc.setScreen(new AliasesScreen());
            case "status" -> mc.setScreen(new StatusScreen());
            case "luckperms", "groups" -> mc.setScreen(new LpGroupsScreen());
            case "players" -> mc.setScreen(new LpUsersScreen());
            case "tracks" -> mc.setScreen(new LpTracksScreen());
            default -> mc.setScreen(new HubScreen());
        }
    }

    /** Feeds a fresh server snapshot to the currently-open CustomPerm screen, if any. */
    public static void deliverSync(GuiSyncPayload payload) {
        if (Minecraft.getInstance().screen instanceof AbstractSyncedScreen synced) {
            synced.onSync(payload);
        }
    }

    /** Feeds a LuckPerms snapshot to the currently-open editor screen, if any. */
    public static void deliverLpSync(LpDto.Snapshot snapshot) {
        if (Minecraft.getInstance().screen instanceof AbstractLpScreen editor) {
            editor.onLpSync(snapshot);
        }
    }

    /** Reports the outcome of an edit on the editor screen that requested it, if still open. */
    public static void deliverLpEditResult(boolean success, String message) {
        if (Minecraft.getInstance().screen instanceof AbstractLpScreen editor) {
            editor.onLpEditResult(success, message);
        }
    }
}
