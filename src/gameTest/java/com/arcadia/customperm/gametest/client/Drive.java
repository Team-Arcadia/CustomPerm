/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import com.arcadia.customperm.client.gui.admin.AdminScreen;
import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.CpScreen;
import com.arcadia.customperm.client.gui.kit.Completer;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static com.arcadia.customperm.gametest.client.StepQueue.SECOND;

/**
 * Driving the admin screens the way a player does: pages asked for like the sidebar asks, buttons pressed by their
 * label, lists and fields found by their narration. What a page keeps private (a tab, the status line, a hidden list)
 * is read by reflection, and a clear message names the member when a rename breaks it.
 */
public final class Drive {
    /** Server limit is 40 page requests per 10 s; one every 8 ticks stays under it. */
    public static final int PAGE_GAP = 8;

    /** Window size and GUI scale, giving the GUI sizes 427x240 (the usual narrow panel), 480x270, 640x360, 1280x720. */
    public record Size(int width, int height, int scale) {
        public String label() {
            return (int) Math.ceil(width / (double) scale) + "x" + (int) Math.ceil(height / (double) scale);
        }
    }

    public static final List<Size> SIZES = List.of(new Size(1280, 720, 3), new Size(960, 540, 2),
            new Size(1280, 720, 2), new Size(1280, 720, 1));

    /** A page, or a view inside one, and how to reach it once the page shows. */
    public record View(String name, GuiPage page, Runnable open) {
    }

    private Drive() {
    }

    // ------------------------------------------------------------------ steps

    public static void open(StepQueue run, View view) {
        run.add("page " + view.name(), PAGE_GAP, () -> PacketDistributor.sendToServer(new GuiRequestPayload(view.page().id())));
        run.add("page " + view.name() + " shown", 0, () -> screen() instanceof AdminScreen s && s.page() == view.page(), () -> { });
        run.add("view " + view.name(), 2, view.open());
    }

    public static void size(StepQueue run, Size size) {
        run.add("size " + size.label(), SECOND, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(size.scale());
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size.width(), size.height());
            mc.resizeDisplay();
        });
        run.await("V03", "window at " + size.label(), 5, () -> guiSize().equals(size.label()),
                () -> "GUI " + guiSize() + ", window " + Minecraft.getInstance().getWindow().getWidth() + "x"
                        + Minecraft.getInstance().getWindow().getHeight());
    }

    /** V02 for one view: V02 checks it draws, then V03 at every size with a screenshot for a person to look at. */
    public static void drawsEverywhere(StepQueue run, List<View> views) {
        size(run, SIZES.get(2));
        for (View view : views) {
            open(run, view);
            run.add("V02 " + view.name(), 2, () -> run.check("V02", view.name() + " opens and draws", () -> drawn()));
        }
        for (Size size : SIZES) {
            size(run, size);
            for (View view : views) {
                open(run, view);
                run.add("V03 " + view.name(), 3, () -> {
                    run.check("V03", view.name() + " at " + size.label(), Drive::layout);
                    screenshot(view.name() + "-" + size.label());
                });
            }
        }
        size(run, SIZES.get(2));
    }

    public static String guiSize() {
        var window = Minecraft.getInstance().getWindow();
        return window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight();
    }

    // ------------------------------------------------------------------ judging a screen

    public static String drawn() {
        CpScreen screen = cpScreen();
        long widgets = screen.children().size();
        if (widgets == 0) throw new AssertionError("no widget");
        LayoutProbe.Findings findings = LayoutProbe.inspect(screen);
        if (findings.texts() == 0) throw new AssertionError("nothing written");
        for (GuiEventListener child : screen.children()) {
            if (child instanceof CpList<?> list && list.visible) {
                list.mouseScrolled(list.getX() + 2, list.getY() + 2, 0, -3);
                list.mouseScrolled(list.getX() + 2, list.getY() + 2, 0, 3);
            }
        }
        return widgets + " widgets, " + findings.texts() + " texts";
    }

    public static String layout() {
        LayoutProbe.Findings findings = LayoutProbe.inspect(cpScreen());
        if (!findings.problems().isEmpty()) {
            throw new AssertionError(findings.problems().size() + " problem(s): " + String.join("; ", findings.problems()));
        }
        return findings.texts() + " texts" + (findings.cutRows() > 0 ? ", " + findings.cutRows() + " list rows cut to fit" : "")
                + (findings.warnings().isEmpty() ? "" : ", warning: " + String.join("; ", findings.warnings()));
    }

    // ------------------------------------------------------------------ widgets

    public static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    public static CpScreen cpScreen() {
        if (!(screen() instanceof CpScreen s)) throw new AssertionError("no CustomPerm screen open but " + screen());
        return s;
    }

    public static Stream<? extends GuiEventListener> widgets() {
        return screen() == null ? Stream.empty() : screen().children().stream();
    }

    @SuppressWarnings("unchecked")
    public static <T> CpList<T> optionalList(String narration) {
        return (CpList<T>) widgets().filter(w -> w instanceof CpList<?> l && l.getMessage().getString().equals(narration))
                .findFirst().orElse(null);
    }

    public static <T> CpList<T> list(String narration) {
        CpList<T> list = optionalList(narration);
        if (list == null) throw new IllegalStateException("No list " + narration + " on " + screen());
        return list;
    }

    public static CpEditBox field(String narration) {
        return (CpEditBox) widgets().filter(w -> w instanceof CpEditBox b && b.getMessage().getString().equals(narration))
                .findFirst().orElseThrow(() -> new IllegalStateException("No field " + narration + " on " + screen()));
    }

    public static void press(String label) {
        widgets().filter(w -> w instanceof CpButton b && b.getMessage().getString().equals(label) && b.active)
                .map(CpButton.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("No active button " + label + " on " + screen())).onPress();
    }

    /** A selection made from outside rebuilds the widgets the way a click does. */
    public static void rebuild() {
        Screen s = screen();
        s.resize(Minecraft.getInstance(), s.width, s.height);
    }

    /** What typing {@code typed} into the field offers, read from the field's own proposal. */
    public static List<String> candidates(String narration, String typed) {
        CpEditBox box = field(narration);
        screen().setFocused(box);
        box.setValue("");
        for (char c : typed.toCharArray()) box.charTyped(c, 0);
        try {
            Field proposal = CpEditBox.class.getDeclaredField("proposal");
            proposal.setAccessible(true);
            List<String> found = ((Completer.Proposal) proposal.get(box)).candidates();
            box.setValue("");
            return found;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CpEditBox.proposal moved; update Drive", e);
        }
    }

    // ------------------------------------------------------------------ private state

    public static void setTab(String tab) {
        try {
            Class<?> screenClass = screen().getClass();
            Class<?> tabClass = Stream.of(screenClass.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Tab")).findFirst()
                    .orElseThrow(() -> new IllegalStateException(screenClass.getSimpleName() + " has no Tab"));
            Object value = Stream.of(tabClass.getEnumConstants()).filter(c -> ((Enum<?>) c).name().equals(tab)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No tab " + tab));
            Method set = screenClass.getDeclaredMethod("setTab", tabClass);
            set.setAccessible(true);
            set.invoke(screen(), value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("setTab moved; update Drive", e);
        }
    }

    public static String status() {
        try {
            Field field = CpScreen.class.getDeclaredField("statusText");
            field.setAccessible(true);
            return screen() instanceof CpScreen s ? (String) field.get(s) : "";
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CpScreen.statusText moved; update Drive", e);
        }
    }

    /** Sends an action exactly as the page's own button would. */
    public static void act(GuiAction action, String... args) {
        try {
            Method act = AdminScreen.class.getDeclaredMethod("act", GuiAction.class, String[].class);
            act.setAccessible(true);
            act.invoke(screen(), action, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AdminScreen.act moved; update Drive", e);
        }
    }

    /** A private field of the open screen. */
    @SuppressWarnings("unchecked")
    public static <T> T screenField(String name) {
        Class<?> type = screen().getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(screen());
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException(screen().getClass().getSimpleName() + " has no field " + name + "; update the smoke run");
    }

    public static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "smoke-" + name + ".png", mc.getMainRenderTarget(), message -> { });
    }
}
