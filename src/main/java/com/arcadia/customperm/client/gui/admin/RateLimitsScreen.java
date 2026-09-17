/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.admin;

import com.arcadia.customperm.client.gui.kit.Atlas;
import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.client.gui.kit.Palette;
import com.arcadia.customperm.client.gui.kit.Rect;
import com.arcadia.customperm.client.gui.kit.Skin;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.RateLimitsData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * Rate limit editor. The left column lists the rules; the right panel edits the selected rule or a
 * new one: target, uses per window, enforcement and when usage history is saved. Exposed commands and
 * aliases without a rule are listed under the form, a click fills the target.
 */
public final class RateLimitsScreen extends AdminScreen {

    private static final int ROW = 16;
    private static final int FIELD = Atlas.INPUT_HEIGHT;
    private static final int BUTTON = Atlas.BUTTON_HEIGHT;
    private static final int GAP = 6;
    private static final int MAX_DIGITS = 9;

    private RateLimitsData data;

    private final CpEditBox search;
    private final CpList<RateLimitsData.Rule> ruleList;
    private final CpEditBox target;
    private final CpEditBox max;
    private final CpEditBox window;
    private final CpList<String> unlimitedList;
    /** Rule to select once the next refresh lands, after saving a new one. */
    private String pendingRule;
    /** Right edge of the buttons on the save row, so the summary is only drawn where it fits. */
    private int saveRowRight;

    public RateLimitsScreen(GuiContext context, RateLimitsData data) {
        super(Component.literal("Rate limits"), context);
        this.data = data;
        this.target = new CpEditBox(Component.literal("Command or alias"), 64).hint(Component.literal("command or alias"));
        this.max = new CpEditBox(Component.literal("Maximum uses per window"), MAX_DIGITS).hint(Component.literal("uses"));
        this.window = new CpEditBox(Component.literal("Window in seconds"), MAX_DIGITS).hint(Component.literal("seconds"))
                .onSubmit(this::save);
        max.setFilter(RateLimitsScreen::digitsOnly);
        window.setFilter(RateLimitsScreen::digitsOnly);
        this.search = new CpEditBox(Component.literal("Search rate limits"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .onChange(text -> refilter());
        this.ruleList = new CpList<RateLimitsData.Rule>(Component.literal("Rate limits"), ROW)
                .renderer(this::renderRule)
                .label(rule -> "/" + rule.name() + ", " + rule.max() + " per " + rule.windowSeconds() + " seconds, "
                        + (rule.enabled() ? "enabled" : "disabled"))
                .identity(RateLimitsData.Rule::name)
                .emptyText("No rate limit yet.")
                .onSelect(rule -> {
                    fillForm(rule);
                    rebuild();
                })
                .onActivate(rule -> setFocused(max));
        this.unlimitedList = new CpList<String>(Component.literal("Commands and aliases without a limit"), 12)
                .renderer((g, font, name, r, hovered, selected) ->
                        Skin.textIn(g, font, "/" + name, r, 5, hovered ? Palette.TEXT : Palette.TEXT_DIM))
                .label(name -> "/" + name)
                .emptyText("Every exposed command and alias has a limit.")
                .onSelect(name -> {
                    ruleList.clearSelection();
                    target.setValue(name);
                    max.setValue("");
                    window.setValue("");
                    setFocused(max);
                    rebuild();
                });
        refilter();
    }

    private static boolean digitsOnly(String text) {
        return text.chars().allMatch(Character::isDigit);
    }

    @Override
    public GuiPage page() {
        return GuiPage.RATE_LIMITS;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.CLOCK;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (RateLimitsData) newData;
        refilter();
        if (pendingRule != null && ruleList.selectByKey(pendingRule)) fillForm(ruleList.getSelected());
        pendingRule = null;
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        RateLimitsData.Rule before = ruleList.getSelected();
        ruleList.setItems(data.rules().stream()
                .filter(rule -> query.isEmpty() || rule.name().toLowerCase(Locale.ROOT).contains(query))
                .toList());
        unlimitedList.setItems(data.unlimited());
        RateLimitsData.Rule after = ruleList.getSelected();
        if (after != null && !after.equals(before)) fillForm(after);
        if (layout != null && !Objects.equals(before == null ? null : before.name(), after == null ? null : after.name())) {
            rebuild();
        }
    }

    private void fillForm(RateLimitsData.Rule rule) {
        if (rule == null) return;
        target.setValue(rule.name());
        max.setValue(String.valueOf(rule.max()));
        window.setValue(String.valueOf(rule.windowSeconds()));
    }

    // ------------------------------------------------------------------ layout

    private Rect left() {
        Rect c = layout.content();
        return c.left(c.w() * 40 / 100);
    }

    private Rect inner() {
        Rect c = layout.content();
        return c.afterLeft(left().w() + GAP).inset(8);
    }

    /** Vertical positions inside the right panel, top to bottom. */
    private int targetY() {
        return inner().y() + 24;
    }

    private int numbersY() {
        return targetY() + FIELD + 16;
    }

    private int saveY() {
        return numbersY() + FIELD + 6;
    }

    private int togglesY() {
        return saveY() + BUTTON + 4;
    }

    private Rect unlimitedArea() {
        Rect in = inner();
        int top = togglesY() + BUTTON + 18;
        return new Rect(in.x(), top, in.w(), in.bottom() - top);
    }

    @Override
    protected void buildPage() {
        boolean editable = canEdit(GuiArea.RATE_LIMITS);
        Rect left = left();
        addRenderableWidget(search.at(left.top(FIELD)));
        addRenderableWidget(ruleList.at(left.belowTop(FIELD + 4)));

        Rect in = inner();
        RateLimitsData.Rule rule = ruleList.getSelected();
        int half = (in.w() - GAP) / 2;
        addRenderableWidget(target.at(new Rect(in.x(), targetY(), in.w(), FIELD)));
        addRenderableWidget(max.at(new Rect(in.x(), numbersY(), half, FIELD)));
        addRenderableWidget(window.at(new Rect(in.x() + half + GAP, numbersY(), half, FIELD)));
        // The target of an existing rule is its key: renaming would be a new rule, so it is fixed.
        target.setEditable(editable && rule == null);
        max.setEditable(editable);
        window.setEditable(editable);

        CpButton save = CpButton.accent(Component.literal(rule == null ? "Add limit" : "Save"), this::save)
                .icon(rule == null ? Icon.PLUS : Icon.CHECK).enabled(editable);
        int saveW = save.preferredWidth(font, 8);
        addRenderableWidget(save.at(new Rect(in.x(), saveY(), saveW, BUTTON)));
        saveRowRight = in.x() + saveW;
        if (rule != null) {
            CpButton create = CpButton.neutral(Component.literal("New"), this::startNew).icon(Icon.PLUS);
            int createW = create.preferredWidth(font, 8);
            addRenderableWidget(create.at(new Rect(in.x() + saveW + 4, saveY(), createW, BUTTON)));
            saveRowRight += 4 + createW;
            CpButton remove = CpButton.danger(Component.literal("Remove limit"), () -> confirmRemove(rule))
                    .iconOnly(Icon.TRASH).enabled(editable);
            addRenderableWidget(remove.at(new Rect(in.right() - BUTTON, saveY(), BUTTON, BUTTON)));

            addRenderableWidget(CpButton.neutral(Component.literal(rule.enabled() ? "Enforced" : "Disabled"),
                            () -> act(rule.enabled() ? GuiAction.RATELIMIT_DISABLE : GuiAction.RATELIMIT_ENABLE, rule.name()))
                    .icon(rule.enabled() ? Icon.CHECK : Icon.CROSS).enabled(editable)
                    .tooltip(Component.literal("Disabling keeps the numbers, so the limit can be enabled again as it was."))
                    .at(new Rect(in.x(), togglesY(), half, BUTTON)));
            addRenderableWidget(CpButton.neutral(Component.literal(rule.immediate() ? "Every use" : "World save"),
                            () -> act(GuiAction.RATELIMIT_PERSISTENCE, rule.name(), rule.immediate() ? "world_save" : "immediate"))
                    .icon(Icon.CLOCK).enabled(editable)
                    .tooltip(Component.literal("When usage history is written. World save: no cost per use, uses since the last save are lost on a crash. "
                            + "Every use: nothing lost, one disk write per accepted use."))
                    .at(new Rect(in.x() + half + GAP, togglesY(), half, BUTTON)));
        }
        Rect unlimited = unlimitedArea();
        if (unlimited.h() >= 24) addRenderableWidget(unlimitedList.at(unlimited));
    }

    // ------------------------------------------------------------------ actions

    private void startNew() {
        ruleList.clearSelection();
        target.setValue("");
        max.setValue("");
        window.setValue("");
        rebuild();
        setFocused(target);
    }

    private void save() {
        String name = target.getValue().trim();
        if (name.startsWith("/")) name = name.substring(1);
        if (name.isEmpty() || max.getValue().isEmpty() || window.getValue().isEmpty()) {
            status("A limit needs a command or alias, a number of uses and a window in seconds.", false);
            return;
        }
        if (Long.parseLong(max.getValue()) < 1 || Long.parseLong(window.getValue()) < 1) {
            status("Uses and window must both be at least 1.", false);
            return;
        }
        pendingRule = name;
        act(GuiAction.RATELIMIT_SET, name, max.getValue(), window.getValue());
    }

    private void confirmRemove(RateLimitsData.Rule rule) {
        confirm("Remove the limit on /" + rule.name(),
                "Players can run /" + rule.name() + " without limit again. Disabling the limit instead keeps its numbers.",
                "Remove limit",
                () -> {
                    startNew();
                    act(GuiAction.RATELIMIT_REMOVE, rule.name());
                });
    }

    // ------------------------------------------------------------------ rendering

    private void renderRule(GuiGraphics g, Font font, RateLimitsData.Rule rule, Rect r, boolean hovered, boolean selected) {
        int right = r.right() - 4;
        if (!rule.enabled()) {
            int w = font.width("OFF") + 6;
            Skin.badge(g, font, "OFF", right - w, r.centerY(), Palette.TEXT_MUTE);
            right -= w + 3;
        }
        if (rule.target() == RateLimitsData.Target.NONE) {
            int w = font.width("NO TARGET") + 6;
            Skin.badge(g, font, "NO TARGET", right - w, r.centerY(), Palette.WARN);
            right -= w + 3;
        }
        String numbers = rule.max() + "/" + formatWindow(rule.windowSeconds());
        int nw = font.width(numbers);
        Skin.text(g, font, numbers, right - nw, r.y() + (r.h() - 8) / 2, Palette.TEXT_DIM);
        Skin.text(g, font, "/" + rule.name(), r.x() + 6, r.y() + (r.h() - 8) / 2, right - nw - r.x() - 12,
                rule.enabled() ? Palette.TEXT : Palette.TEXT_MUTE);
    }

    /** Short window label: 90s, 15m, 2h, 1d; exact seconds when not a whole unit. */
    static String formatWindow(int seconds) {
        if (seconds % 86_400 == 0) return (seconds / 86_400) + "d";
        if (seconds % 3600 == 0) return (seconds / 3600) + "h";
        if (seconds % 60 == 0) return (seconds / 60) + "m";
        return seconds + "s";
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect in = inner();
        Skin.panel(g, in.inset(-8));
        RateLimitsData.Rule rule = ruleList.getSelected();
        Skin.text(g, font, rule == null ? "New rate limit" : "/" + rule.name(), in.x(), in.y(), in.w(), Palette.TEXT);
        String subtitle;
        if (!canEdit(GuiArea.RATE_LIMITS)) {
            subtitle = "Read-only: needs " + GuiArea.RATE_LIMITS.node() + ".";
        } else if (rule == null) {
            subtitle = "Uses allowed per player within a sliding window.";
        } else {
            subtitle = switch (rule.target()) {
                case ALIAS -> "Applies to the alias /" + rule.name() + ".";
                case EXPOSED_COMMAND -> "Applies to the exposed command /" + rule.name() + ".";
                case NONE -> "Waits: /" + rule.name() + " is neither exposed nor an alias.";
            };
        }
        Skin.text(g, font, subtitle, in.x(), in.y() + 11, in.w(), rule != null && rule.target() == RateLimitsData.Target.NONE
                ? Palette.WARN : Palette.TEXT_MUTE);

        int half = (in.w() - GAP) / 2;
        Skin.text(g, font, "USES", in.x(), numbersY() - 11, half, Palette.TEXT_MUTE);
        Skin.text(g, font, "SECONDS", in.x() + half + GAP, numbersY() - 11, half, Palette.TEXT_MUTE);
        if (!max.getValue().isEmpty() && !window.getValue().isEmpty() && window.getValue().length() <= MAX_DIGITS) {
            long seconds = Long.parseLong(window.getValue());
            if (seconds > 0 && seconds <= Integer.MAX_VALUE) {
                String hint = max.getValue() + " per " + formatWindow((int) seconds);
                int w = font.width(hint);
                int x = in.right() - w;
                if (rule != null) x = in.right() - BUTTON - 6 - w;
                if (x >= saveRowRight + 6) Skin.text(g, font, hint, x, saveY() + (BUTTON - 8) / 2, Palette.TEXT_DIM);
            }
        }
        Rect unlimited = unlimitedArea();
        if (unlimited.h() >= 24) {
            Skin.text(g, font, "WITHOUT A LIMIT", unlimited.x(), unlimited.y() - 11, unlimited.w(), Palette.TEXT_MUTE);
        }
    }
}
