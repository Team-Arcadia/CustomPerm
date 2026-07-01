package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.arcadia.customperm.network.RequestGuiSyncPayload;
import com.tesseraui.TesseraLabel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared plumbing for the CustomPerm TesseraUI screens (Grades/Aliases/Status, H2.1): requests
 * a {@link GuiSyncPayload} on open and re-renders once the reply arrives. All CustomPerm data
 * lives server-side, so every screen starts in a "Loading..." state.
 */
public abstract class AbstractSyncedScreen extends TesseraScreen {

    protected static final int PANEL_W = 360;
    protected static final int PANEL_H = 280;

    private TesseraPanel root;
    protected GuiSyncPayload latest;

    protected AbstractSyncedScreen(Component title) {
        super(title);
    }

    protected int originX() {
        return this.width / 2 - PANEL_W / 2;
    }

    protected int originY() {
        return this.height / 2 - PANEL_H / 2;
    }

    @Override
    protected void init() {
        root = TesseraPanel.column(originX(), originY(), PANEL_W, PANEL_H)
                .padding(10)
                .gap(6)
                .add(new TesseraLabel(0, 0, PANEL_W, 20, "Loading..."));
        root.layout();
        requestRefresh();
    }

    /** Invoked on the client thread whenever a fresh snapshot arrives from the server. */
    public final void onSync(GuiSyncPayload payload) {
        this.latest = payload;
        this.root = buildPanel(payload);
        this.root.layout();
    }

    protected final void requestRefresh() {
        PacketDistributor.sendToServer(new RequestGuiSyncPayload());
    }

    protected abstract TesseraPanel buildPanel(GuiSyncPayload payload);

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }
}
