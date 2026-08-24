/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.gui.TesseraGuiBridge;
import com.arcadia.customperm.network.GuiSyncPayload;
import com.arcadia.customperm.network.lp.LpEditResultPayload;
import com.arcadia.customperm.network.lp.LpSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for {@link GuiSyncPayload}. Reached via {@code NetworkHandler.dispatchGuiSync}
 * from inside an {@code FMLEnvironment.dist.isClient()} branch, so it is never loaded on a dedicated
 * server.
 *
 * <p>This class must NOT reference any TesseraUI type in its own bytecode: it may be loaded on a
 * client that has CustomPerm but not TesseraUI, and verification of a screen reference would fail
 * to resolve {@code com.tesseraui.TesseraScreen}. All screen interaction is therefore delegated to
 * {@link TesseraGuiBridge} (a lazily-loaded class) behind the {@link CustomPerm#isTesseraUiPresent()}
 * guard, via a plain static call the verifier does not follow.</p>
 */
public final class ClientNetworkHandler {

    private ClientNetworkHandler() {
    }

    public static void handleGuiSync(GuiSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!CustomPerm.isTesseraUiPresent()) return;
            TesseraGuiBridge.deliverSync(payload);
        });
    }

    public static void handleLpSync(LpSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!CustomPerm.isTesseraUiPresent()) return;
            TesseraGuiBridge.deliverLpSync(payload.snapshot());
        });
    }

    public static void handleLpEditResult(LpEditResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!CustomPerm.isTesseraUiPresent()) return;
            TesseraGuiBridge.deliverLpEditResult(payload.success(), payload.message());
        });
    }
}
