package com.arcadia.customperm.client.gui;

import com.tesseraui.TesseraButton;
import com.tesseraui.TesseraLabel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Landing menu for the CustomPerm TesseraUI panel, opened by {@code /customperm gui} (no
 * argument). Purely client-side navigation — three buttons that open the Grades, Aliases and
 * Status screens. It carries no server data, so unlike {@link AbstractSyncedScreen} it does not
 * request a sync.
 */
public final class HubScreen extends TesseraScreen {

    private static final int MAX_W = 240;
    private static final int MAX_H = 190;

    private TesseraPanel root;

    public HubScreen() {
        super(Component.literal("CustomPerm - Admin"));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_W);
        int h = Math.min(this.height - 16, MAX_H);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int btnW = w - 24;
        root = TesseraPanel.column(x, y, w, h)
                .background(AbstractSyncedScreen.WINDOW_BG)
                .border(1, AbstractSyncedScreen.WINDOW_BORDER)
                .padding(12)
                .gap(8)
                .add(new TesseraLabel(0, 0, w, 22, "CustomPerm — Administration"))
                .add(new TesseraButton(0, 0, btnW, 26).label("Grades")
                        .onClick(() -> Minecraft.getInstance().setScreen(new GradesScreen())))
                .add(new TesseraButton(0, 0, btnW, 26).label("Aliases")
                        .onClick(() -> Minecraft.getInstance().setScreen(new AliasesScreen())))
                .add(new TesseraButton(0, 0, btnW, 26).label("Status")
                        .onClick(() -> Minecraft.getInstance().setScreen(new StatusScreen())));
        root.layout();
    }

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }

    // TesseraScreen provides neither render() nor mouseClicked() — the concrete screen must draw
    // its panel and forward clicks, or it opens blank and unresponsive.
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
