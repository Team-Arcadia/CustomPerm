/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.CpScreen;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.WindowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * What a screen actually draws, read by rendering it once more into a {@link GuiGraphics} that records every string.
 * No pixel is compared: the probe judges what a person would notice on a narrow window, the V03 step of the test
 * procedure. A label or a sentence cut with an ellipsis outside a list, text running past the window, widgets on top
 * of each other or outside the window are findings; a list row cut to fit its column is expected and only counted.
 */
public final class LayoutProbe {
    /** Texts that end with "..." on purpose. */
    private static final Set<String> LITERAL_ELLIPSES = Set.of("Loading...", "Loading player...", "Working...");

    public record Text(String text, int x, int y, int width, boolean clipped) {
    }

    /** Problems fail the check; warnings (a field's hint cut to fit) are reported without failing it. */
    public record Findings(List<String> problems, List<String> warnings, int cutRows, int texts) {
    }

    private LayoutProbe() {
    }

    /** Renders the current screen into the recorder, then judges the result. Render thread only. */
    public static Findings inspect(CpScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Recorder recorder = new Recorder(mc, buffers);
        screen.render(recorder, -1, -1, 0);
        recorder.flush();

        WindowLayout layout = layout(screen);
        Rect window = layout.window();
        List<String> problems = new ArrayList<>();
        List<Rect> lists = new ArrayList<>();
        List<Rect> fields = new ArrayList<>();
        List<AbstractWidget> widgets = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w && w.visible && w.getWidth() > 0 && w.getHeight() > 0) {
                widgets.add(w);
                if (w instanceof CpList<?>) lists.add(new Rect(w.getX(), w.getY(), w.getWidth(), w.getHeight()));
                if (w instanceof CpEditBox) fields.add(new Rect(w.getX(), w.getY(), w.getWidth(), w.getHeight()));
            }
        }

        int cutRows = 0;
        List<String> warnings = new ArrayList<>();
        for (Text t : recorder.texts) {
            boolean cut = t.text().endsWith("...") && t.text().length() > 3 && !LITERAL_ELLIPSES.contains(t.text());
            boolean inList = lists.stream().anyMatch(r -> contains(r, t.x(), t.y()));
            boolean inField = fields.stream().anyMatch(r -> contains(r, t.x(), t.y()));
            if (cut && inList) {
                cutRows++;
            } else if (cut && inField) {
                warnings.add("hint cut \"" + t.text() + "\"");
            } else if (cut) {
                problems.add("cut text \"" + t.text() + "\" at " + t.x() + "," + t.y());
            }
            if (!t.clipped() && (t.x() + t.width() > window.right() + 1 || t.x() < window.x() - 1)) {
                problems.add("text past the window \"" + t.text() + "\" " + t.x() + ".." + (t.x() + t.width())
                        + " for a window " + window.x() + ".." + window.right());
            }
        }

        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget a = widgets.get(i);
            if (a.getX() < window.x() || a.getY() < window.y() || a.getX() + a.getWidth() > window.right()
                    || a.getY() + a.getHeight() > window.bottom()) {
                problems.add("widget outside the window: " + describe(a));
            }
            if (a instanceof CpButton b && !b.isIconOnly() && b.preferredWidth(Minecraft.getInstance().font, CpButton.MIN_PADDING) > b.getWidth()
                    && Minecraft.getInstance().font.width(b.getMessage()) > b.getWidth() - 2 * CpButton.MIN_PADDING) {
                problems.add("label wider than its button: " + describe(a));
            }
            for (int j = i + 1; j < widgets.size(); j++) {
                AbstractWidget b = widgets.get(j);
                if (overlap(a, b)) problems.add("overlap: " + describe(a) + " and " + describe(b));
            }
        }
        return new Findings(problems, warnings, cutRows, recorder.texts.size());
    }

    public static WindowLayout layout(CpScreen screen) {
        try {
            Field field = CpScreen.class.getDeclaredField("layout");
            field.setAccessible(true);
            return (WindowLayout) field.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CpScreen.layout moved; update LayoutProbe", e);
        }
    }

    private static boolean overlap(AbstractWidget a, AbstractWidget b) {
        return a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight();
    }

    private static boolean contains(Rect r, int x, int y) {
        return x >= r.x() && x < r.right() && y >= r.y() && y < r.bottom();
    }

    private static String describe(AbstractWidget w) {
        return w.getClass().getSimpleName() + " \"" + w.getMessage().getString() + "\" [" + w.getX() + "," + w.getY() + " "
                + w.getWidth() + "x" + w.getHeight() + "]";
    }

    /** Draws as usual and remembers each string, and whether a scissor was cutting it. */
    private static final class Recorder extends GuiGraphics {
        private final List<Text> texts = new ArrayList<>();
        private final Deque<Boolean> scissors = new ArrayDeque<>();

        Recorder(Minecraft mc, MultiBufferSource.BufferSource buffers) {
            super(mc, buffers);
        }

        @Override
        public void enableScissor(int minX, int minY, int maxX, int maxY) {
            scissors.push(true);
            super.enableScissor(minX, minY, maxX, maxY);
        }

        @Override
        public void disableScissor() {
            scissors.poll();
            super.disableScissor();
        }

        @Override
        public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
            if (text != null && !text.isEmpty()) texts.add(new Text(text, x, y, font.width(text), !scissors.isEmpty()));
            return super.drawString(font, text, x, y, color, shadow);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            StringBuilder plain = new StringBuilder();
            text.accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            if (!plain.isEmpty()) texts.add(new Text(plain.toString(), x, y, font.width(text), !scissors.isEmpty()));
            return super.drawString(font, text, x, y, color, shadow);
        }
    }
}
