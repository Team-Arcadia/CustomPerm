package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.tesseraui.TesseraButton;
import com.tesseraui.TesseraLabel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScrollList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TesseraUI counterpart of {@code /customperm alias ...}. Actions reuse the exact same
 * server-side command (via a prefilled {@link ChatScreen}) instead of duplicating alias
 * CRUD logic client-side.
 */
public final class AliasesScreen extends AbstractSyncedScreen {

    public AliasesScreen() {
        super(Component.literal("CustomPerm - Aliases"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        TesseraPanel root = newRoot();

        root.add(TesseraPanel.row(0, 0, panelW(), 20).gap(6)
                .add(new TesseraButton(0, 0, 70, 20).label("< Menu").onClick(this::openHub))
                .add(new TesseraButton(0, 0, 110, 20).label("Create alias")
                        .onClick(() -> openChat("/customperm alias add ")))
                .add(new TesseraButton(0, 0, 80, 20).label("Refresh")
                        .onClick(this::requestRefresh)));

        TesseraScrollList list = new TesseraScrollList(0, 0, panelW(), panelH() - 40, 24);
        List<TesseraPanel> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : payload.aliases().entrySet()) {
            String name = entry.getKey();
            int stepCount = entry.getValue();
            rows.add(TesseraPanel.row(0, 0, panelW(), 22).gap(4)
                    .add(new TesseraLabel(0, 0, 140, 20, name + " (" + stepCount + " step" + (stepCount > 1 ? "s" : "") + ")"))
                    .add(new TesseraButton(0, 0, 60, 20).label("+Step")
                            .onClick(() -> openChat("/customperm alias addstep " + name + " ")))
                    .add(new TesseraButton(0, 0, 60, 20).label("Steps")
                            .onClick(() -> openChat("/customperm alias steps " + name)))
                    .add(new TesseraButton(0, 0, 60, 20).label("Remove")
                            .onClick(() -> openChat("/customperm alias remove " + name))));
        }
        list.setItems(rows);
        root.add(list);
        return root;
    }

    private static void openChat(String prefilled) {
        Minecraft.getInstance().setScreen(new ChatScreen(prefilled));
    }
}
