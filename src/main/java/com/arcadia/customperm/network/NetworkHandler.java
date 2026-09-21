/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network;

import com.arcadia.customperm.client.ClientNetworkHandler;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestHandler;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import com.arcadia.customperm.network.gui.GuiVocabularyPayload;
import com.arcadia.customperm.network.lp.LpEditPayload;
import com.arcadia.customperm.network.lp.LpEditResultPayload;
import com.arcadia.customperm.network.lp.LpRequestHandler;
import com.arcadia.customperm.network.lp.LpSyncPayload;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the payloads of the admin interface and of the LuckPerms editor. This class is loaded
 * on BOTH sides (RegisterPayloadHandlersEvent fires on client and dedicated server), and the server
 * needs every codec registered too: it is the one sending the server-to-client payloads.
 * <p>
 * What the server must NOT do is create a direct method reference into {@link ClientNetworkHandler}
 * (e.g. {@code ClientNetworkHandler::handlePage}). A method reference resolves its target method
 * eagerly, at the point the referencing bytecode runs, unconditionally and on every side, unlike a
 * guarded method call, which only resolves its target on first actual invocation.
 * {@code ClientNetworkHandler}'s body touches {@code net.minecraft.client.Minecraft}, a class that
 * does not exist on a dedicated server. So the handlers passed to {@code playToClient} below are
 * declared in this class, and only call into {@code ClientNetworkHandler} from inside an
 * {@code FMLEnvironment.dist.isClient()} branch that a dedicated server never enters.
 * <p>
 * The registrar is also marked {@link PayloadRegistrar#optional()}: by default NeoForge treats a
 * registered channel as mandatory for the connection handshake. CustomPerm works with zero
 * client-side install; without {@code .optional()} a vanilla client would be refused just because
 * the admin interface exists.
 * <p>
 * Protocol version 2 replaced the TesseraUI-era {@code gui_sync} channel with the native interface
 * channels. A client running an older CustomPerm still connects (the channels are optional) but has
 * no interface on a server running this version, and the other way round.
 */
public final class NetworkHandler {

    public static final String PROTOCOL_VERSION = "2";

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(GuiPagePayload.TYPE, GuiPagePayload.STREAM_CODEC, NetworkHandler::dispatchPage);
        registrar.playToClient(GuiVocabularyPayload.TYPE, GuiVocabularyPayload.STREAM_CODEC,
                NetworkHandler::dispatchVocabulary);
        registrar.playToClient(GuiActionResultPayload.TYPE, GuiActionResultPayload.STREAM_CODEC,
                NetworkHandler::dispatchActionResult);
        registrar.playToServer(GuiRequestPayload.TYPE, GuiRequestPayload.STREAM_CODEC, GuiRequestHandler::handleRequest);
        registrar.playToServer(GuiActionPayload.TYPE, GuiActionPayload.STREAM_CODEC, GuiRequestHandler::handleAction);

        // LuckPerms editor channel. Registered unconditionally, like the channels above: whether
        // LuckPerms is installed is a runtime property of the server, while the handshake channel
        // list is fixed at registration time. LpRequestHandler answers a request on a LuckPerms-less
        // server with an empty snapshot rather than nothing at all.
        registrar.playToClient(LpSyncPayload.TYPE, LpSyncPayload.STREAM_CODEC, NetworkHandler::dispatchLpSync);
        registrar.playToClient(LpEditResultPayload.TYPE, LpEditResultPayload.STREAM_CODEC,
                NetworkHandler::dispatchLpEditResult);
        registrar.playToServer(RequestLpSyncPayload.TYPE, RequestLpSyncPayload.STREAM_CODEC,
                LpRequestHandler::handleSync);
        registrar.playToServer(LpEditPayload.TYPE, LpEditPayload.STREAM_CODEC,
                LpRequestHandler::handleEdit);
    }

    private static void dispatchPage(GuiPagePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ClientNetworkHandler.handlePage(payload, context);
        }
    }

    private static void dispatchVocabulary(GuiVocabularyPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ClientNetworkHandler.handleVocabulary(payload, context);
        }
    }

    private static void dispatchActionResult(GuiActionResultPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ClientNetworkHandler.handleActionResult(payload, context);
        }
    }

    private static void dispatchLpSync(LpSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ClientNetworkHandler.handleLpSync(payload, context);
        }
    }

    private static void dispatchLpEditResult(LpEditResultPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ClientNetworkHandler.handleLpEditResult(payload, context);
        }
    }
}
