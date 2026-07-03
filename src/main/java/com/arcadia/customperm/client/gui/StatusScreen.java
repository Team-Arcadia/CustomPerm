package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.tesseraui.TesseraButton;
import com.tesseraui.TesseraLabel;
import com.tesseraui.TesseraPanel;
import net.minecraft.network.chat.Component;

/**
 * Read-only TesseraUI counterpart of {@code /customperm status}.
 */
public final class StatusScreen extends AbstractSyncedScreen {

    public StatusScreen() {
        super(Component.literal("CustomPerm - Status"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        TesseraPanel root = newRoot();

        root.add(new TesseraLabel(0, 0, panelW(), 20, "Backend: " + payload.backendLabel()));
        root.add(new TesseraLabel(0, 0, panelW(), 20, "LP fallback mode: " + payload.lpFallbackMode()));
        root.add(new TesseraLabel(0, 0, panelW(), 20,
                "Dispatcher commands: " + payload.dispatcherCommandCount() + " (vanilla + mods + aliases)"));
        root.add(new TesseraLabel(0, 0, panelW(), 20, "Exposed commands: " + payload.exposedCommandCount()));
        root.add(new TesseraLabel(0, 0, panelW(), 20, "Custom aliases: " + payload.aliases().size()));

        if (payload.luckPermsActive()) {
            root.add(new TesseraLabel(0, 0, panelW(), 20, "(Grades & user perms managed by /lp)"));
        } else {
            root.add(new TesseraLabel(0, 0, panelW(), 20, "Internal grades: " + payload.grades().size()));
            root.add(new TesseraLabel(0, 0, panelW(), 20, "Users with grade: " + payload.userGradeCount()));
        }

        root.add(TesseraPanel.row(0, 0, panelW(), 20).gap(6)
                .add(new TesseraButton(0, 0, 80, 20).label("< Menu").onClick(this::openHub))
                .add(new TesseraButton(0, 0, 80, 20).label("Refresh").onClick(this::requestRefresh)));
        return root;
    }
}
