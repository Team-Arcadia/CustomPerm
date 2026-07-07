package com.arcadia.customperm.client.gui;

import com.arcadia.customperm.network.GuiSyncPayload;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only TesseraUI counterpart of {@code /customperm status}, rendered from the
 * {@code customperm:ui/status} HTML template.
 */
public final class StatusScreen extends AbstractSyncedScreen {

    public StatusScreen() {
        super(Component.literal("CustomPerm - Status"));
    }

    @Override
    protected TesseraPanel buildPanel(GuiSyncPayload payload) {
        Map<String, String> data = new LinkedHashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();

        boolean lp = payload.luckPermsActive();
        data.put("backendLabel", payload.backendLabel());
        data.put("lpFallbackMode", payload.lpFallbackMode());
        data.put("dispatcherCommandCount", String.valueOf(payload.dispatcherCommandCount()));
        data.put("exposedCommandCount", String.valueOf(payload.exposedCommandCount()));
        data.put("aliasCount", String.valueOf(payload.aliases().size()));
        data.put("gradeCount", String.valueOf(payload.grades().size()));
        data.put("userGradeCount", String.valueOf(payload.userGradeCount()));
        data.put("luckPermsActive", String.valueOf(lp));
        data.put("internalMode", String.valueOf(!lp));

        handlers.put("back", this::openHub);
        handlers.put("refresh", this::requestRefresh);

        TesseraTemplate tpl = TesseraTemplate.load("customperm:ui/status");
        return TesseraTemplateRenderer.build(tpl, TesseraModel.of(data), handlers,
                originX(), originY(), panelW(), panelH());
    }
}
