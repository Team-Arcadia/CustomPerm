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
import com.arcadia.customperm.network.gui.ClusterView;
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
 * Alias editor: the list of aliases with a creation form on the left, the selected alias on the
 * right, on two tabs, three in a cluster. Steps are reordered with Up and Down, replaced from the edit field, appended,
 * or removed; deleting an alias (or removing its last step, which deletes it) asks for confirmation.
 * The Args tab declares what the alias takes, which its steps then reach with {@code ${name}}. The Servers tab,
 * in a cluster, picks the members the alias exists on.
 */
public final class AliasesScreen extends AdminScreen {

    private static final int ROW = 16;
    private static final int STEP_ROW = 14;
    private static final int FIELD = Atlas.INPUT_HEIGHT;
    private static final int GAP = 6;

    private enum Tab { STEPS, ARGS, SERVERS }

    /** The four argument types, in the order the Add button cycles through them. */
    private static final String[] TYPES = {"word", "player", "integer", "text"};

    /** One step of the selected alias, with its position. */
    private record Step(int index, String command) {
    }

    private AliasesData data;
    private Tab tab = Tab.STEPS;

    // Kept across rebuilds so typing, selection and scroll survive refreshes.
    private final CpEditBox search;
    private final CpList<AliasesData.Alias> aliasList;
    private final CpEditBox newName;
    private final CpEditBox newStep;
    private final CpList<Step> stepList;
    private final CpEditBox stepEdit;
    private final CpList<AliasesData.Param> paramList;
    private final CpEditBox paramName;
    private final CpEditBox paramValue;
    /** The type the Add button gives a new argument; cycled by its own button. */
    private int newType;
    /** Step to select once the next refresh lands, e.g. where a moved step ended up. */
    private int pendingStep = -1;
    /** Argument to select once the next refresh lands. */
    private String pendingParam;
    /** Alias to select once the next refresh lands, after creating it. */
    private String pendingAlias;

    public AliasesScreen(GuiContext context, AliasesData data) {
        super(Component.literal("Aliases"), context);
        this.data = data;
        this.stepEdit = new CpEditBox(Component.literal("Step command"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("step command"))
                .completes(Completions.commandLine(this::stepArguments))
                .onSubmit(this::appendStep);
        // Before the lists: their selection handlers clear it, and a blank final cannot be read first.
        this.paramValue = new CpEditBox(Component.literal("Argument value"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("default, 1..5, or a,b,c"))
                .completes(Completions.search(this::paramValueCandidates));
        this.search = new CpEditBox(Component.literal("Search aliases"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .completes(Completions.search(() -> this.data.aliases().stream().map(AliasesData.Alias::name).toList()))
                .onChange(text -> refilter());
        this.aliasList = new CpList<AliasesData.Alias>(Component.literal("Aliases"), ROW)
                .renderer(this::renderAlias)
                .label(alias -> "/" + alias.name() + ", " + alias.steps().size() + " steps")
                .identity(AliasesData.Alias::name)
                .emptyText("No alias yet: create one below.")
                .onSelect(alias -> {
                    stepEdit.setValue("");
                    paramValue.setValue("");
                    fillSteps();
                    fillParams();
                    rebuild();
                })
                .onActivate(alias -> setFocused(stepEdit));
        this.newName = new CpEditBox(Component.literal("New alias name"), 64)
                .hint(Component.literal("name"));
        this.newStep = new CpEditBox(Component.literal("First step of the new alias"), GuiCodecs.CLIENT_ARG_MAX)
                .hint(Component.literal("first step"))
                .completes(Completions.commandLine(List::of))
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
        this.paramName = new CpEditBox(Component.literal("New argument name"), 16)
                .hint(Component.literal("argument"))
                .onSubmit(this::addParam);
        this.paramList = new CpList<AliasesData.Param>(Component.literal("Arguments"), STEP_ROW)
                .renderer(this::renderParam)
                .label(param -> param.slot() + ", " + param.type())
                .identity(AliasesData.Param::name)
                .emptyText("Select an alias.")
                .onSelect(param -> {
                    paramValue.setValue("");
                    rebuild();
                })
                .onActivate(param -> setFocused(paramValue));
        refilter();
        fillSteps();
        fillParams();
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
        fillParams();
        if (pendingParam != null) paramList.selectByKey(pendingParam);
        pendingParam = null;
    }

    /** Player names for the default of a player argument; the other types take a free value or a range. */
    private List<String> paramValueCandidates() {
        AliasesData.Param param = paramList.getSelected();
        return param != null && param.type().equals("player") ? Completions.vocabulary().players() : List.of();
    }

    /** What a step's arguments may be: the selected alias's own arguments, then player names. */
    private List<String> stepArguments() {
        AliasesData.Alias alias = aliasList.getSelected();
        return alias == null ? List.of() : alias.params().stream().map(param -> "${" + param.name() + "}").toList();
    }

    private void refilter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        AliasesData.Alias before = aliasList.getSelected();
        aliasList.setItems(data.aliases().stream()
                .filter(a -> query.isEmpty() || a.name().toLowerCase(Locale.ROOT).contains(query))
                .toList());
        if (layout != null && !Objects.equals(name(before), name(aliasList.getSelected()))) {
            stepEdit.setValue("");
            paramValue.setValue("");
            fillSteps();
            fillParams();
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

    private void fillParams() {
        AliasesData.Alias alias = aliasList.getSelected();
        paramList.setItems(alias == null ? List.of() : alias.params());
        paramList.emptyText(alias == null ? "Select an alias."
                : "No argument: /" + alias.name() + " takes none.");
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

    /** The tab row, under the alias header. */
    private Rect tabsRow() {
        Rect inner = rightInner();
        return new Rect(inner.x(), inner.y() + 36, inner.w(), FIELD);
    }

    /** Space under the tab row for the step list. */
    private Rect stepsArea() {
        Rect inner = rightInner();
        int top = 36 + FIELD + 4;
        int bottom = FIELD + 4 + FIELD + 4 + Atlas.BUTTON_HEIGHT + 4;
        return new Rect(inner.x(), inner.y() + top, inner.w(), inner.h() - top - bottom);
    }

    /** Space under the tab row for the argument list; one row lower than the steps, having one more. */
    private Rect paramsArea() {
        Rect steps = stepsArea();
        return new Rect(steps.x(), steps.y(), steps.w(), steps.h() - FIELD - 4);
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

        AliasesData.Alias selected = aliasList.getSelected();
        if (selected == null) return;
        List<CpButton> tabs = new ArrayList<>(List.of(
                CpButton.ghost(Component.literal("Steps (" + selected.steps().size() + ")"), () -> setTab(Tab.STEPS))
                        .icon(Icon.ALIAS).selected(tab == Tab.STEPS),
                CpButton.ghost(Component.literal("Args (" + selected.params().size() + ")"), () -> setTab(Tab.ARGS))
                        .icon(Icon.EDIT).selected(tab == Tab.ARGS)));
        // Only where a list means something: in a cluster, or when one was set before leaving it.
        boolean servers = showsServers(selected.servers());
        if (servers) {
            tabs.add(CpButton.ghost(Component.literal("Servers"), () -> setTab(Tab.SERVERS))
                    .icon(Icon.HOME).selected(tab == Tab.SERVERS));
        } else if (tab == Tab.SERVERS) {
            tab = Tab.STEPS;
        }
        placeButtonRow(tabsRow(), 8, false, tabs);
        if (tab == Tab.ARGS) buildParamEditor(editable);
        else if (tab == Tab.SERVERS) buildServerToggles(new Rect(stepsArea().x(), stepsArea().y() + serversIntroHeight(selected), stepsArea().w(),
                        serverTogglesHeight(stepsArea().w(), selected.servers(), ClusterView.ALIASES)),
                selected.servers(), ClusterView.ALIASES, "aliases", editable,
                list -> act(GuiAction.ALIAS_SERVERS, selected.name(), list));
        else buildStepEditor(editable);
    }

    private static String serversIntro(AliasesData.Alias alias) {
        return "Where /" + alias.name() + " is open to its node. Elsewhere only a grade or a player naming that server "
                + "opens it, and a command of the same name stays. None picked: every member.";
    }

    /** Height of the intro above the server toggles, so they start under its last line at any width. */
    private int serversIntroHeight(AliasesData.Alias alias) {
        return font.split(Component.literal(serversIntro(alias)), stepsArea().w()).size() * 10 + 6;
    }

    private void setTab(Tab wanted) {
        this.tab = wanted;
        rebuild();
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

    private void buildParamEditor(boolean editable) {
        AliasesData.Alias alias = aliasList.getSelected();
        if (alias == null) return;
        AliasesData.Param param = paramList.getSelected();
        int index = param == null ? -1 : alias.params().indexOf(param);
        Rect params = paramsArea();
        addRenderableWidget(paramList.at(params));

        Rect tools = new Rect(params.x(), params.bottom() + 4, params.w(), FIELD);
        addRenderableWidget(CpButton.neutral(Component.literal("Move argument up"), () -> moveParam(index - 1))
                .iconOnly(Icon.UP).enabled(editable && index > 0).at(tools.left(22)));
        addRenderableWidget(CpButton.neutral(Component.literal("Move argument down"), () -> moveParam(index + 1))
                .iconOnly(Icon.DOWN).enabled(editable && index >= 0 && index < alias.params().size() - 1)
                .at(new Rect(tools.x() + 26, tools.y(), 22, tools.h())));
        Rect toggles = tools.afterLeft(52);
        placeButtonRow(toggles, 6, true, List.of(
                CpButton.ghost(Component.literal(param != null && param.optional() ? "Optional" : "Required"),
                                () -> editParam("optional", param == null || !param.optional() ? "true" : "false"))
                        .enabled(editable && param != null).selected(param != null && param.optional()),
                CpButton.ghost(Component.literal("Selectors"), () -> editParam("selectors",
                                param != null && param.allowSelectors() ? "false" : "true"))
                        .enabled(editable && param != null && selectable(param))
                        .selected(param != null && param.allowSelectors()),
                CpButton.danger(Component.literal("Remove"), () -> removeParam(param)).icon(Icon.MINUS)
                        .enabled(editable && param != null)));

        Rect value = new Rect(params.x(), tools.bottom() + 4, params.w(), FIELD);
        addRenderableWidget(paramValue.at(value));
        paramValue.setEditable(editable && param != null);

        Rect apply = new Rect(params.x(), value.bottom() + 4, params.w(), Atlas.BUTTON_HEIGHT);
        placeButtonRow(apply, 6, false, List.of(
                CpButton.neutral(Component.literal("Default"), () -> editParam("default", paramValue.getValue().trim()))
                        .enabled(editable && param != null),
                CpButton.neutral(Component.literal("Range"), () -> editParam("range", paramValue.getValue().trim()))
                        .enabled(editable && param != null && "integer".equals(param.type())),
                CpButton.neutral(Component.literal("Choices"), () -> editParam("choices", paramValue.getValue().trim()))
                        .enabled(editable && param != null && "word".equals(param.type()))));

        Rect add = new Rect(params.x(), apply.bottom() + 4, params.w(), FIELD);
        CpButton addButton = CpButton.accent(Component.literal("Add"), this::addParam).icon(Icon.PLUS)
                .enabled(editable);
        CpButton typeButton = CpButton.ghost(Component.literal(TYPES[newType]), this::cycleType).enabled(editable);
        int addW = addButton.preferredWidth(font, 6);
        int typeW = typeButton.preferredWidth(font, 6);
        addRenderableWidget(paramName.at(add.beforeRight(addW + typeW + 8)));
        addRenderableWidget(typeButton.at(new Rect(add.right() - addW - typeW - 4, add.y(), typeW, add.h())));
        addRenderableWidget(addButton.at(add.right(addW)));
        paramName.setEditable(editable);
    }

    /** Only a text argument can carry a selector; a word refuses the at sign, a player is resolved. */
    private static boolean selectable(AliasesData.Param param) {
        return "text".equals(param.type());
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

    private void addParam() {
        AliasesData.Alias alias = aliasList.getSelected();
        String name = paramName.getValue().trim().toLowerCase(Locale.ROOT);
        if (alias == null || name.isEmpty()) {
            status("A new argument needs a name.", false);
            return;
        }
        pendingParam = name;
        act(GuiAction.ALIAS_PARAM_ADD, alias.name(), name, TYPES[newType]);
        paramName.setValue("");
    }

    private void cycleType() {
        newType = (newType + 1) % TYPES.length;
        rebuild();
    }

    private void moveParam(int to) {
        AliasesData.Alias alias = aliasList.getSelected();
        AliasesData.Param param = paramList.getSelected();
        if (alias == null || param == null) return;
        pendingParam = param.name();
        act(GuiAction.ALIAS_PARAM_MOVE, alias.name(), param.name(), String.valueOf(to));
    }

    private void editParam(String field, String value) {
        AliasesData.Alias alias = aliasList.getSelected();
        AliasesData.Param param = paramList.getSelected();
        if (alias == null || param == null) return;
        pendingParam = param.name();
        act(GuiAction.ALIAS_PARAM_EDIT, alias.name(), param.name(), field, value);
        paramValue.setValue("");
    }

    private void removeParam(AliasesData.Param param) {
        AliasesData.Alias alias = aliasList.getSelected();
        if (alias == null || param == null) return;
        Runnable remove = () -> act(GuiAction.ALIAS_PARAM_REMOVE, alias.name(), param.name());
        boolean used = alias.steps().stream().anyMatch(step -> step.contains("${" + param.name() + "}"));
        if (used) {
            confirm("Remove " + param.slot(),
                    "Steps of /" + alias.name() + " use ${" + param.name() + "}. Removing the argument leaves"
                            + " that text in them as typed.",
                    "Remove " + param.name(), remove);
        } else {
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
        // Not registered on this server: a short badge, so the name keeps its room on a narrow list.
        boolean away = elsewhereOnly(alias.servers());
        if (away) {
            int w = font.width("OFF") + 6;
            Skin.badge(g, font, "OFF", right - w, r.centerY(), Palette.TEXT_MUTE);
            right -= w + 3;
        }
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
        Skin.text(g, font, "/" + alias.name(), r.x() + 6, r.y() + (r.h() - 8) / 2, right - cw - r.x() - 12,
                away ? Palette.TEXT_MUTE : Palette.TEXT);
    }

    private void renderStep(GuiGraphics g, Font font, Step step, Rect r, boolean hovered, boolean selected) {
        String index = "#" + step.index();
        Skin.text(g, font, index, r.x() + 5, r.y() + (r.h() - 8) / 2, Palette.TEXT_MUTE);
        int x = r.x() + 5 + font.width("#00") + 6;
        Skin.text(g, font, "/" + step.command(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT);
    }

    private void renderParam(GuiGraphics g, Font font, AliasesData.Param param, Rect r, boolean hovered, boolean selected) {
        String slot = param.slot();
        Skin.text(g, font, slot, r.x() + 5, r.y() + (r.h() - 8) / 2, Palette.TEXT);
        int x = r.x() + 5 + font.width(slot) + 6;
        StringBuilder detail = new StringBuilder(param.type());
        if (!param.range().isEmpty()) detail.append(' ').append(param.range());
        if (!param.choices().isEmpty()) detail.append(" of ").append(String.join("|", param.choices()));
        if (!param.defaultValue().isEmpty()) detail.append(" = ").append(param.defaultValue());
        if (param.allowSelectors()) detail.append(" @");
        Skin.text(g, font, detail.toString(), x, r.y() + (r.h() - 8) / 2, r.right() - x - 4, Palette.TEXT_MUTE);
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
            // The read-only note first: it is what this admin needs, and the text under it wraps to any height.
            int y = inner.y();
            if (!canEdit(GuiArea.ALIASES)) {
                y = paragraph(g, "Read-only: editing aliases needs " + GuiArea.ALIASES.node() + ".", inner, y,
                        Palette.WARN) + 6;
            }
            paragraph(g, "Select an alias to edit its steps. An alias runs its steps in order, at op level 4, "
                    + "for players holding customperm.alias.<name>. A step reaches an argument with ${name}.",
                    inner, y, Palette.TEXT_MUTE);
            return;
        }
        String title = "/" + alias.name() + (alias.params().isEmpty() ? "" : " " + alias.usage());
        Skin.text(g, font, title, inner.x(), inner.y(), inner.w(), Palette.TEXT);
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
        if (tab == Tab.SERVERS && showsServers(alias.servers())) {
            Rect area = stepsArea();
            paragraph(g, serversIntro(alias), new Rect(area.x(), area.y(), area.w(), inner.bottom() - area.y()), area.y(),
                    Palette.TEXT_DIM);
        }
    }
}
