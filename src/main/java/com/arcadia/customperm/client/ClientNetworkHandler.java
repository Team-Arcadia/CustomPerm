package com.arcadia.customperm.client;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.gui.AbstractSyncedScreen;
import com.arcadia.customperm.network.GuiSyncPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for {@link GuiSyncPayload}. Registered unconditionally by
 * {@code NetworkHandler} (it's plain NeoForge/vanilla wiring), but every line that touches a
 * TesseraUI type is behind {@link CustomPerm#isTesseraUiPresent()} — same lazy-classloading
 * discipline as {@code LuckPermsService}: the JVM never needs to resolve {@code TesseraScreen}
 * on a client that doesn't have the jar, because this branch never runs there (a client without
 * TesseraUI never opens a Tessera screen in the first place, so no sync ever arrives while one
 * is open — this check is a defensive second gate, not the primary one).
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
