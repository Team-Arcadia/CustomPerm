package com.arcadia.customperm.client;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.gui.AbstractSyncedScreen;
import com.arcadia.customperm.network.GuiSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for {@link GuiSyncPayload}. This class is only ever reached via
 * {@code NetworkHandler.dispatchGuiSync}, from inside an {@code FMLEnvironment.dist.isClient()}
 * branch — it is never loaded on a dedicated server, so referencing {@link Minecraft} here is
 * safe. The {@link CustomPerm#isTesseraUiPresent()} check below is a second, independent guard
 * for the case where a client is running without the TesseraUI jar: {@code AbstractSyncedScreen}
 * extends TesseraUI's {@code TesseraScreen}, so this method must not touch it unless TesseraUI
 * is actually loaded (a client without TesseraUI never opens a Tessera screen in the first
 * place, so no sync ever arrives while one is open — but this check makes that explicit rather
 * than relying on it).
 */
public final class ClientNetworkHandler {

    private ClientNetworkHandler() {
    }

    public static void handleGuiSync(GuiSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!CustomPerm.isTesseraUiPresent()) return;
            if (Minecraft.getInstance().screen instanceof AbstractSyncedScreen synced) {
                synced.onSync(payload);
            }
        });
    }
}
