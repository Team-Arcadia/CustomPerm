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
 * TesseraUI counterpart of {@code /customperm grade ...}. Every action reuses the exact same
 * server-side command (via a prefilled {@link ChatScreen}) instead of duplicating CRUD/
 * permission logic client-side.
 */
public final class GradesScreen extends AbstractSyncedScreen {

    public GradesScreen() {
        super(Component.literal("CustomPerm - Grades"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        TesseraPanel root = newRoot();

        if (payload.luckPermsActive()) {
            root.add(new TesseraLabel(0, 0, panelW(), 40,
                    "Grades are managed by LuckPerms (/lp) while it is active."));
            root.add(new TesseraButton(0, 0, 80, 20).label("< Menu").onClick(this::openHub));
            return root;
        }

        root.add(TesseraPanel.row(0, 0, panelW(), 20).gap(6)
                .add(new TesseraButton(0, 0, 70, 20).label("< Menu").onClick(this::openHub))
                .add(new TesseraButton(0, 0, 110, 20).label("Create grade")
                        .onClick(() -> openChat("/customperm grade create ")))
                .add(new TesseraButton(0, 0, 80, 20).label("Refresh")
                        .onClick(this::requestRefresh)));

        TesseraScrollList list = new TesseraScrollList(0, 0, panelW(), panelH() - 40, 24);
        List<TesseraPanel> rows = new ArrayList<>();
        for (Map.Entry<String, GuiSyncPayload.GradeDto> entry : payload.grades().entrySet()) {
            String name = entry.getKey();
            GuiSyncPayload.GradeDto grade = entry.getValue();
            rows.add(TesseraPanel.row(0, 0, panelW(), 22).gap(4)
                    .add(new TesseraLabel(0, 0, 140, 20, name + " (" + grade.permissions().size()
                            + " perm, " + grade.deniedPermissions().size() + " deny)"))
                    .add(new TesseraButton(0, 0, 60, 20).label("+Perm")
                            .onClick(() -> openChat("/customperm grade addperm " + name + " ")))
                    .add(new TesseraButton(0, 0, 60, 20).label("-Perm")
                            .onClick(() -> openChat("/customperm grade removeperm " + name + " ")))
                    .add(new TesseraButton(0, 0, 60, 20).label("Delete")
                            .onClick(() -> openChat("/customperm grade delete " + name))));
        }
        list.setItems(rows);
        root.add(list);
        return root;
    }

    private static void openChat(String prefilled) {
        Minecraft.getInstance().setScreen(new ChatScreen(prefilled));
    }
}
