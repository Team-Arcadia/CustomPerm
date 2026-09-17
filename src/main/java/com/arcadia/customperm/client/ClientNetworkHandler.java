/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client;

import com.arcadia.customperm.client.gui.admin.AdminScreens;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.lp.LpEditResultPayload;
import com.arcadia.customperm.network.lp.LpSyncPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handlers of the server-to-client payloads. Reached only from inside an
 * {@code FMLEnvironment.dist.isClient()} branch in {@code NetworkHandler}, so it is never loaded on a
 * dedicated server. Work is always moved to the client thread before touching screens.
 */
public final class ClientNetworkHandler {

    private ClientNetworkHandler() {
    }

    public static void handlePage(GuiPagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AdminScreens.deliver(payload));
    }

    public static void handleActionResult(GuiActionResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AdminScreens.deliverResult(payload.success(), payload.message()));
    }

    public static void handleLpSync(LpSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AdminScreens.deliverLpSync(payload.snapshot()));
    }

    public static void handleLpEditResult(LpEditResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AdminScreens.deliverResult(payload.success(), payload.message()));
    }
}
