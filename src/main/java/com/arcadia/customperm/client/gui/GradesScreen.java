package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TesseraUI counterpart of {@code /customperm grade ...}, rendered from the
 * {@code customperm:ui/grades} HTML template. Every action reuses the exact same server-side
 * command (via a prefilled {@link ChatScreen}) instead of duplicating CRUD/permission logic
 * client-side. Per-row actions are wired by giving each grade item a unique handler key.
 */
public final class GradesScreen extends AbstractSyncedScreen {

    public GradesScreen() {
        super(Component.literal("CustomPerm - Grades"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        Map<String, String> data = new LinkedHashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        boolean lp = payload.luckPermsActive();
        data.put("luckPermsActive", String.valueOf(lp));
        data.put("internalMode", String.valueOf(!lp));
        data.put("noGrades", String.valueOf(payload.grades().isEmpty()));

        handlers.put("back", this::openHub);
        handlers.put("refresh", this::requestRefresh);
        handlers.put("createGrade", () -> openChat("/customperm grade create "));

        // v-for count key, then one indexed entry per field (grade.<field>.<i>).
        data.put("grades", String.valueOf(payload.grades().size()));
        int i = 0;
        for (Map.Entry<String, GuiSyncPayload.GradeDto> entry : payload.grades().entrySet()) {
            String name = entry.getKey();
            GuiSyncPayload.GradeDto grade = entry.getValue();
            data.put("grade.name." + i, name);
            data.put("grade.permCount." + i, String.valueOf(grade.permissions().size()));
            data.put("grade.denyCount." + i, String.valueOf(grade.deniedPermissions().size()));

            String addKey = "grade.addperm." + i;
            String remKey = "grade.removeperm." + i;
            String delKey = "grade.delete." + i;
            data.put("grade.addPermAction." + i, addKey);
            data.put("grade.removePermAction." + i, remKey);
            data.put("grade.deleteAction." + i, delKey);
            handlers.put(addKey, () -> openChat("/customperm grade addperm " + name + " "));
            handlers.put(remKey, () -> openChat("/customperm grade removeperm " + name + " "));
            handlers.put(delKey, () -> openChat("/customperm grade delete " + name));
            i++;
        }

        TesseraTemplate tpl = TesseraTemplate.load("customperm:ui/grades");
        return TesseraTemplateRenderer.build(tpl, TesseraModel.of(data), handlers,
                originX(), originY(), panelW(), panelH());
    }

    private static void openChat(String prefilled) {
        Minecraft.getInstance().setScreen(new ChatScreen(prefilled));
    }
}
