/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.lp;

import com.arcadia.customperm.client.gui.HubScreen;
import com.arcadia.customperm.network.lp.LpDto;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.LpEditPayload;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import com.tesseraui.TesseraInputState;
import com.tesseraui.TesseraLabel;
import com.tesseraui.TesseraModel;
import com.tesseraui.TesseraPanel;
import com.tesseraui.TesseraScreen;
import com.tesseraui.TesseraTemplate;
import com.tesseraui.TesseraTemplateRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared plumbing for the in-game LuckPerms editor screens. Each subclass declares a scope
 * ({@code groups}, {@code group:admin}, {@code user:<uuid>}, ...), fills a flat string model and
 * a handler map, and lets an HTML template do the layout — the same arrangement as
 * {@code AbstractSyncedScreen}, extended with the two things an editor needs that a read-only
 * screen does not: text inputs and a write path.
 *
 * <p><strong>Input state outlives a rebuild.</strong> Every successful edit triggers a fresh
 * snapshot from the server, which rebuilds the panel. The {@link TesseraInputState} map is kept
 * on the screen rather than recreated per build, so half-typed text and the caret survive that
 * rebuild; recreating it would wipe the field the admin is still filling in the moment an
 * unrelated edit lands.
 *
 * <p><strong>Scope matching.</strong> A snapshot is only accepted when its scope equals the one
 * this screen asked for. Replies are asynchronous and the admin can navigate away while one is
 * in flight; without this check a late reply for the previous screen would repaint the current one.
 */
public abstract class AbstractLpScreen extends TesseraScreen {

    /** Wider than the CustomPerm screens: permission nodes are long, and wrapping them hurts. */
    private static final int MAX_W = 420;
    private static final int MAX_H = 300;

    protected LpDto.Snapshot latest = LpDto.Snapshot.EMPTY;

    /** Last edit outcome, rendered as a status line inside the panel. */
    private String status = "";
    private boolean statusError;

    private TesseraPanel root;

    /**
     * Keyed by the {@code id} attribute of the template's {@code <input>} elements. Mutable and
     * shared with the renderer, which registers any input this screen never reads itself.
     */
    private final Map<String, TesseraInputState> inputs = new HashMap<>();

    protected AbstractLpScreen(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------ contract

    /** One of the {@code RequestLpSyncPayload.SCOPE_*} constants. */
    protected abstract String scope();

    /** Group name, track name or user UUID; empty for the list scopes. */
    protected String target() {
        return "";
    }

    /** Resource path of the HTML template backing this screen. */
    protected abstract String templateId();

    /**
     * Fills the template model. Handlers registered in {@code handlers} are reachable from
     * {@code onclick}; {@code submits} handles {@code onsubmit} on text inputs.
     */
    protected abstract void fill(LpDto.Snapshot snapshot, Map<String, String> data,
                                Map<String, Runnable> handlers, Map<String, Consumer<String>> submits);

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        root = TesseraPanel.column(originX(), originY(), panelW(), panelH())
                .padding(10)
                .add(new TesseraLabel(0, 0, panelW(), 20, "Loading..."));
        root.layout();
        requestRefresh();
    }

    /** Invoked on the client thread when a snapshot arrives; ignores replies for other screens. */
    public final void onLpSync(LpDto.Snapshot snapshot) {
        if (!snapshot.scope().equals(scopeKey())) return;
        this.latest = snapshot;
        rebuild();
    }

    /** Invoked on the client thread when the server reports the outcome of an edit. */
    public final void onLpEditResult(boolean success, String message) {
        this.status = message;
        this.statusError = !success;
        rebuild();
    }

    private void rebuild() {
        Map<String, String> data = new LinkedHashMap<>();
        Map<String, Runnable> handlers = new HashMap<>();
        Map<String, Consumer<String>> submits = new HashMap<>();

        data.put("canEdit", String.valueOf(latest.canEdit()));
        data.put("readOnly", String.valueOf(!latest.canEdit()));
        // Two mutually exclusive flags rather than one "has status" plus a colour: the template
        // renders the line twice, once per style, and a single flag would print both.
        boolean hasStatus = !status.isEmpty();
        data.put("statusOk", String.valueOf(hasStatus && !statusError));
        data.put("statusError", String.valueOf(hasStatus && statusError));
        data.put("status", status);

        handlers.put("back", this::openHub);
        handlers.put("refresh", this::requestRefresh);
        handlers.put("openGroups", () -> open(new LpGroupsScreen()));
        handlers.put("openUsers", () -> open(new LpUsersScreen()));
        handlers.put("openTracks", () -> open(new LpTracksScreen()));

        fill(latest, data, handlers, submits);

        TesseraTemplate template = TesseraTemplate.load(templateId());
        root = TesseraTemplateRenderer.build(template, TesseraModel.of(data), handlers, submits, inputs,
                originX(), originY(), panelW(), panelH());
        root.layout();
    }

    // ------------------------------------------------------------------ network

    protected final void requestRefresh() {
        PacketDistributor.sendToServer(new RequestLpSyncPayload(scope(), target()));
    }

    /**
     * Sends one mutation. The current scope rides along so the server can push the updated
     * screen back without the client having to ask for it a second time.
     */
    protected final void edit(LpEditOp op, String... args) {
        PacketDistributor.sendToServer(new LpEditPayload(op.name(), List.of(args), scope(), target()));
    }

    /** Mirrors {@code RequestLpSyncPayload.scopeKey()} for matching replies against this screen. */
    private String scopeKey() {
        return target().isEmpty() ? scope() : scope() + ":" + target();
    }

    // ------------------------------------------------------------------ inputs

    /** The live state of one template input, created on first use. */
    protected final TesseraInputState input(String id) {
        return inputs.computeIfAbsent(id, key -> new TesseraInputState());
    }

    /** Current text of a template input, never null. */
    protected final String value(String id) {
        String text = input(id).text;
        return text == null ? "" : text.trim();
    }

    /** Clears an input after a successful submit, so a create field does not stay filled. */
    protected final void clearInput(String id) {
        TesseraInputState state = input(id);
        state.text = "";
        state.cursor = 0;
        state.selStart = 0;
    }

    // ------------------------------------------------------------------ navigation

    protected final void open(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    protected final void openHub() {
        Minecraft.getInstance().setScreen(new HubScreen());
    }

    // ------------------------------------------------------------------ geometry

    protected int panelW() {
        return Math.min(this.width - 16, MAX_W);
    }

    protected int panelH() {
        return Math.min(this.height - 16, MAX_H);
    }

    protected int originX() {
        return (this.width - panelW()) / 2;
    }

    protected int originY() {
        return (this.height - panelH()) / 2;
    }

    // ------------------------------------------------------------------ screen wiring

    @Override
    protected TesseraPanel tesseraRoot() {
        return root;
    }

    // TesseraScreen supplies keyPressed but neither render(), mouseClicked(), charTyped() nor
    // mouseScrolled(). The first two are the same omission AbstractSyncedScreen works around;
    // the last two matter only here, because these are the first CustomPerm screens with text
    // fields and scrollable lists — without them typing produces nothing and long lists are
    // unreachable.
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

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (root != null && root.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (root != null && root.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
