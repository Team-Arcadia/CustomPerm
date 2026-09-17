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
import com.arcadia.customperm.network.gui.AliasesData;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiCodecs;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Alias editor: the list of aliases with a creation form on the left, the selected alias's steps on
 * the right. Steps are reordered with Up and Down, replaced from the edit field, appended, or
 * removed; deleting an alias (or removing its last step, which deletes it) asks for confirmation.
 */
public final class AliasesScreen extends AdminScreen {

    private static final int ROW = 16;
    private static final int STEP_ROW = 14;
    private static final int FIELD = Atlas.INPUT_HEIGHT;
    private static final int GAP = 6;

    /** One step of the selected alias, with its position. */
    private record Step(int index, String command) {
    }

    private AliasesData data;

    // Kept across rebuilds so typing, selection and scroll survive refreshes.
    private final CpEditBox search;
    private final CpList<AliasesData.Alias> aliasList;
    private final CpEditBox newName;
    private final CpEditBox newStep;
    private final CpList<Step> stepList;
    private final CpEditBox stepEdit;
    /** Step to select once the next refresh lands, e.g. where a moved step ended up. */
    private int pendingStep = -1;
    /** Alias to select once the next refresh lands, after creating it. */
    private String pendingAlias;

    public AliasesScreen(GuiContext context, AliasesData data) {
        super(Component.literal("Aliases"), context);
        this.data = data;
        this.stepEdit = new CpEditBox(Component.literal("Step command"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("step command"))
                .onSubmit(this::appendStep);
        this.search = new CpEditBox(Component.literal("Search aliases"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .onChange(text -> refilter());
        this.aliasList = new CpList<AliasesData.Alias>(Component.literal("Aliases"), ROW)
                .renderer(this::renderAlias)
                .label(alias -> "/" + alias.name() + ", " + alias.steps().size() + " steps")
                .identity(AliasesData.Alias::name)
                .emptyText("No alias yet: create one below.")
                .onSelect(alias -> {
                    stepEdit.setValue("");
                    fillSteps();
                    rebuild();
                })
                .onActivate(alias -> setFocused(stepEdit));
        this.newName = new CpEditBox(Component.literal("New alias name"), 64)
                .hint(Component.literal("name"));
        this.newStep = new CpEditBox(Component.literal("First step of the new alias"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("first step"))
                .onSubmit(this::create);
        this.stepList = new CpList<Step>(Component.literal("Steps"), STEP_ROW)
                .renderer(this::renderStep)
                .label(step -> "Step " + step.index() + ": " + step.command())
                .identity(Step::index)
                .emptyText("Select an alias.")
                .onSelect(step -> {
                    stepEdit.setValue(step.command());
                    rebuild();
                })
                .onActivate(step -> setFocused(stepEdit));
        refilter();
        fillSteps();
    }

    @Override
    public GuiPage page() {
        return GuiPage.ALIASES;
    }

    @Override
    protected Icon titleIcon() {
        return Icon.ALIAS;
    }

    @Override
    protected CpEditBox searchBox() {
        return search;
    }

    @Override
    protected void apply(GuiPageData newData) {
        this.data = (AliasesData) newData;
        refilter();
        if (pendingAlias != null && aliasList.selectByKey(pendingAlias)) stepEdit.setValue("");
        pendingAlias = null;
        fillSteps();
        if (pendingStep >= 0 && pendingStep < stepList.items().size()) {
            stepList.selectByKey(pendingStep);
            Step step = stepList.getSelected();
            if (step != null) stepEdit.setValue(step.command());
        }
        pendingStep = -1;
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        AliasesData.Alias before = aliasList.getSelected();
        aliasList.setItems(data.aliases().stream()
                .filter(a -> query.isEmpty() || a.name().toLowerCase(Locale.ROOT).contains(query))
                .toList());
        if (layout != null && !Objects.equals(name(before), name(aliasList.getSelected()))) {
            stepEdit.setValue("");
            fillSteps();
            rebuild();
        }
    }

    private static String name(AliasesData.Alias alias) {
        return alias == null ? null : alias.name();
    }

    private void fillSteps() {
        AliasesData.Alias alias = aliasList.getSelected();
        List<Step> steps = new ArrayList<>();
        if (alias != null) {
            for (int i = 0; i < alias.steps().size(); i++) steps.add(new Step(i, alias.steps().get(i)));
        }
        stepList.setItems(steps);
        stepList.emptyText(alias == null ? "Select an alias." : "No steps.");
    }

    // ------------------------------------------------------------------ layout

    private Rect left() {
        Rect c = layout.content();
        return c.left(c.w() * 40 / 100);
    }

    private Rect right() {
        return layout.content().afterLeft(left().w() + GAP);
    }

    /** The two creation rows at the bottom of the left column. */
    private Rect createArea() {
        return left().bottom(FIELD * 2 + 4);
    }

    private Rect rightInner() {
        return right().inset(8);
    }

    /** Space under the alias header for the step list. */
    private Rect stepsArea() {
        Rect inner = rightInner();
        int top = 36;
        int bottom = FIELD + 4 + FIELD + 4 + Atlas.BUTTON_HEIGHT + 4;
        return new Rect(inner.x(), inner.y() + top, inner.w(), inner.h() - top - bottom);
    }

    @Override
    protected void buildPage() {
        boolean editable = canEdit(GuiArea.ALIASES);
        Rect left = left();
        addRenderableWidget(search.at(left.top(FIELD)));
        Rect create = createArea();
        addRenderableWidget(aliasList.at(new Rect(left.x(), left.y() + FIELD + 4, left.w(),
                create.y() - 14 - left.y() - FIELD - 4)));

        Rect nameRow = create.top(FIELD);
        CpButton createButton = CpButton.accent(Component.literal("Create"), this::create).icon(Icon.PLUS)
                .enabled(editable);
        int createW = createButton.preferredWidth(font, 6);
        addRenderableWidget(newName.at(nameRow.beforeRight(createW + 4)));
        addRenderableWidget(createButton.at(nameRow.right(createW)));
        addRenderableWidget(newStep.at(create.bottom(FIELD)));
        newName.setEditable(editable);
        newStep.setEditable(editable);

        buildStepEditor(editable);
    }

    private void buildStepEditor(boolean editable) {
        AliasesData.Alias alias = aliasList.getSelected();
        if (alias == null) return;
        Step step = stepList.getSelected();
        Rect steps = stepsArea();
        addRenderableWidget(stepList.at(steps));

        Rect tools = new Rect(steps.x(), steps.bottom() + 4, steps.w(), FIELD);
        addRenderableWidget(CpButton.neutral(Component.literal("Move step up"), () -> move(step, -1)).iconOnly(Icon.UP)
                .enabled(editable && step != null && step.index() > 0)
                .at(tools.left(22)));
        addRenderableWidget(CpButton.neutral(Component.literal("Move step down"), () -> move(step, 1)).iconOnly(Icon.DOWN)
                .enabled(editable && step != null && step.index() < alias.steps().size() - 1)
                .at(new Rect(tools.x() + 26, tools.y(), 22, tools.h())));
        CpButton remove = CpButton.danger(Component.literal("Remove step"), () -> removeStep(alias, step)).icon(Icon.MINUS)
                .enabled(editable && step != null);
        addRenderableWidget(remove.at(tools.right(remove.preferredWidth(font, 6))));

        Rect edit = new Rect(steps.x(), tools.bottom() + 4, steps.w(), FIELD);
        addRenderableWidget(stepEdit.at(edit));
        stepEdit.setEditable(editable);

        Rect bottom = new Rect(steps.x(), edit.bottom() + 4, steps.w(), Atlas.BUTTON_HEIGHT);
        CpButton replace = CpButton.neutral(Component.literal("Replace"), this::replaceStep).icon(Icon.EDIT)
                .enabled(editable && step != null);
        CpButton add = CpButton.accent(Component.literal("Add"), this::appendStep).icon(Icon.PLUS).enabled(editable);
        int replaceW = replace.preferredWidth(font, 6);
        addRenderableWidget(replace.at(bottom.left(replaceW)));
        addRenderableWidget(add.at(new Rect(bottom.x() + replaceW + 4, bottom.y(), add.preferredWidth(font, 6), bottom.h())));
        CpButton delete = CpButton.danger(Component.literal("Delete alias"), () -> confirmDelete(alias))
                .iconOnly(Icon.TRASH).enabled(editable);
        addRenderableWidget(delete.at(bottom.right(Atlas.BUTTON_HEIGHT)));
    }

    // ------------------------------------------------------------------ actions

    private void create() {
        String name = newName.getValue().trim();
        String first = newStep.getValue().trim();
        if (name.isEmpty() || first.isEmpty()) {
            status("A new alias needs a name and a first step.", false);
            return;
        }
        act(GuiAction.ALIAS_CREATE, name, stripSlash(first));
        newName.setValue("");
        newStep.setValue("");
        search.setValue("");
        pendingAlias = name;
    }

    private void appendStep() {
        AliasesData.Alias alias = aliasList.getSelected();
        String command = stepEdit.getValue().trim();
        if (alias == null || command.isEmpty()) return;
        pendingStep = alias.steps().size();
        act(GuiAction.ALIAS_STEP_ADD, alias.name(), stripSlash(command));
    }

    private void replaceStep() {
        AliasesData.Alias alias = aliasList.getSelected();
        Step step = stepList.getSelected();
        String command = stepEdit.getValue().trim();
        if (alias == null || step == null || command.isEmpty()) return;
        pendingStep = step.index();
        act(GuiAction.ALIAS_STEP_SET, alias.name(), String.valueOf(step.index()), stripSlash(command));
    }

    private void move(Step step, int delta) {
        AliasesData.Alias alias = aliasList.getSelected();
        if (alias == null || step == null) return;
        int to = step.index() + delta;
        pendingStep = to;
        act(GuiAction.ALIAS_STEP_MOVE, alias.name(), String.valueOf(step.index()), String.valueOf(to));
    }

    private void removeStep(AliasesData.Alias alias, Step step) {
        if (step == null) return;
        Runnable remove = () -> act(GuiAction.ALIAS_STEP_REMOVE, alias.name(), String.valueOf(step.index()));
        if (alias.steps().size() == 1) {
            confirm("Remove the last step",
                    "/" + alias.name() + " has no other step: removing this one deletes the alias.",
                    "Delete /" + alias.name(), remove);
        } else {
            pendingStep = Math.max(0, step.index() - 1);
            remove.run();
        }
    }

    private void confirmDelete(AliasesData.Alias alias) {
        confirm("Delete /" + alias.name(),
                "The alias and its " + alias.steps().size() + " step(s) are removed"
                        + (alias.shadows() ? ", and the command it shadowed comes back." : "."),
                "Delete /" + alias.name(),
                () -> act(GuiAction.ALIAS_DELETE, alias.name()));
    }

    /** Steps are stored without a leading slash, like {@code /customperm alias add}. */
    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    // ------------------------------------------------------------------ rendering

    private void renderAlias(GuiGraphics g, Font font, AliasesData.Alias alias, Rect r, boolean hovered, boolean selected) {
        int right = r.right() - 4;
        if (alias.shadows()) {
            int w = font.width("SHADOW") + 6;
            Skin.badge(g, font, "SHADOW", right - w, r.centerY(), Palette.WARN);
            right -= w + 3;
        }
        if (alias.hasLimit()) {
            int w = font.width("LIMIT") + 6;
            Skin.badge(g, font, "LIMIT", right - w, r.centerY(), alias.limitEnabled() ? Palette.WARN : Palette.TEXT_MUTE);
            right -= w + 3;
        }
        String count = String.valueOf(alias.steps().size());
        int cw = font.width(count);
        Skin.text(g, font, count, right - cw, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        Skin.text(g, font, "/" + alias.name(), r.x() + 6, r.y() + (r.h() - 8) / 2, right - cw - r.x() - 12, Palette.TEXT);
    }

    private void renderStep(GuiGraphics g, Font font, Step step, Rect r, boolean hovered, boolean selected) {
        String index = "#" + step.index();
        Skin.text(g, font, index, r.x() + 5, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        int x = r.x() + 5 + font.width("#00") + 6;
        Skin.text(g, font, "/" + step.command(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect panel = right();
        Skin.panel(g, panel);
        Rect inner = rightInner();
        AliasesData.Alias alias = aliasList.getSelected();
        Rect create = createArea();
        Skin.text(g, font, "NEW ALIAS", create.x(), create.y() - 10, create.w(), Palette.TEXT_MUTE);

        if (alias == null) {
            paragraph(g, "Select an alias to edit its steps. An alias runs its steps in order, at op level 4, "
                    + "for players holding customperm.alias.<name>.", inner, inner.y(), Palette.TEXT_MUTE);
            if (!canEdit(GuiArea.ALIASES)) {
                paragraph(g, "Read-only: editing aliases needs " + GuiArea.ALIASES.node() + ".", inner, inner.y() + 40,
                        Palette.TEXT_MUTE);
            }
            return;
        }
        Skin.text(g, font, "/" + alias.name(), inner.x(), inner.y(), inner.w(), Palette.TEXT);
        String info = "customperm.alias." + alias.name();
        if (alias.hasLimit()) {
            info += "  |  limit " + alias.limitMax() + " per " + alias.limitWindow() + "s"
                    + (alias.limitEnabled() ? "" : " (disabled)");
        }
        Skin.text(g, font, info, inner.x(), inner.y() + 11, inner.w(), Palette.TEXT_DIM);
        String warning = alias.shadows()
                ? "Shadows a real command of the same name."
                : canEdit(GuiArea.ALIASES) ? "Steps run at op level 4." : "Read-only: needs " + GuiArea.ALIASES.node() + ".";
        Skin.text(g, font, warning, inner.x(), inner.y() + 22, inner.w(), alias.shadows() ? Palette.WARN : Palette.TEXT_MUTE);
    }
}
