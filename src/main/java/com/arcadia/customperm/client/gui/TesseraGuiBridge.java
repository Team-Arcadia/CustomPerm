package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
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
            default -> mc.setScreen(new HubScreen());
        }
    }

    /** Feeds a fresh server snapshot to the currently-open CustomPerm screen, if any. */
    public static void deliverSync(GuiSyncPayload payload) {
        if (Minecraft.getInstance().screen instanceof AbstractSyncedScreen synced) {
            synced.onSync(payload);
        }
    }
}
