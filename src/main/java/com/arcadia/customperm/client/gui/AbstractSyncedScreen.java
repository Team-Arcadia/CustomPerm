package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.arcadia.customperm.network.RequestGuiSyncPayload;
import com.tesseraui.TesseraLabel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared plumbing for the CustomPerm TesseraUI screens (Grades/Aliases/Status, H2.1): requests
 * a {@link GuiSyncPayload} on open and re-renders once the reply arrives. All CustomPerm data
 * lives server-side, so every screen starts in a "Loading..." state.
 */
public abstract class AbstractSyncedScreen extends TesseraScreen {

    /** Preferred window size; the actual panel is capped to the current screen (see panelW/panelH). */
    private static final int MAX_W = 360;
    private static final int MAX_H = 280;
    static final int WINDOW_BG = 0xE6141824;      // dark, ~90% opaque — looks like a window
    static final int WINDOW_BORDER = 0xFF3A3A4C;

    private TesseraPanel root;
    protected GuiSyncPayload latest;

    protected AbstractSyncedScreen(Component title) {
        super(title);
    }

    /** Panel width, never wider than the screen (avoids off-screen content at high GUI scale). */
    protected int panelW() {
        return Math.min(this.width - 16, MAX_W);
    }

    /** Panel height, never taller than the screen (a fixed height would push the panel off the top). */
    protected int panelH() {
        return Math.min(this.height - 16, MAX_H);
    }

    protected int originX() {
        return (this.width - panelW()) / 2;
    }

    protected int originY() {
        return (this.height - panelH()) / 2;
    }

    /** A centered, screen-capped window panel with a background + border for subclasses to fill. */
    protected TesseraPanel newRoot() {
        return TesseraPanel.column(originX(), originY(), panelW(), panelH())
                .background(WINDOW_BG)
                .border(1, WINDOW_BORDER)
                .padding(10)
                .gap(6);
    }

    @Override
    protected void init() {
        root = newRoot().add(new TesseraLabel(0, 0, panelW(), 20, "Loading..."));
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

    /** Returns to the {@link HubScreen} landing menu. */
    protected final void openHub() {
        Minecraft.getInstance().setScreen(new HubScreen());
    }

    protected abstract TesseraPanel buildPanel(GuiSyncPayload payload);

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }

    // TesseraScreen provides neither render() nor mouseClicked(); a concrete screen MUST draw its
    // panel and forward clicks to it (see TesseraUI's own dev example screens), otherwise the
    // screen opens but stays blank and unresponsive.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (root != null) {
            root.render(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root != null && root.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
