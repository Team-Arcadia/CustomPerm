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
import com.arcadia.customperm.network.gui.ClusterView;
import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiPageData;
import com.arcadia.customperm.network.gui.RateLimitsData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Rate limit editor. The left column lists the rules; the right panel edits the selected rule or a
 * new one: target, uses per window, enforcement and when usage history is saved. Exposed commands and
 * aliases without a rule are listed under the form, a click fills the target. The Levels view lists and edits
 * what adjusts the rule: a server's own limit, a grade's or a player's value, each with the last word over the
 * one before it.
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
    private final CpEditBox scope;
    private final CpList<String> unlimitedList;
    private final CpList<RateLimitsData.Level> levelList;
    private final CpEditBox levelHolder;
    private final CpEditBox levelValue;
    private final CpEditBox levelContext;
    /** Whether the servers view replaces the lower part of the form; kept while moving between rules. */
    private boolean serversView;
    /** Whether the levels view replaces the lower part of the form; never together with the servers view. */
    private boolean levelsView;
    /** SERVER, GRADE or PLAYER: who the level being typed belongs to. */
    private String levelKind = "GRADE";
    /** Rule to select once the next refresh lands, after saving a new one. */
    private String pendingRule;
    /** Right edge of the buttons on the save row, so the summary is only drawn where it fits. */
    private int saveRowRight;

    public RateLimitsScreen(GuiContext context, RateLimitsData data) {
        super(Component.literal("Rate limits"), context);
        this.data = data;
        this.target = new CpEditBox(Component.literal("Command or alias"), 64).hint(Component.literal("command or alias"))
                .completes(Completions.commandsAndAliases());
        this.max = new CpEditBox(Component.literal("Maximum uses per window"), MAX_DIGITS).hint(Component.literal("uses"));
        this.window = new CpEditBox(Component.literal("Window in seconds"), MAX_DIGITS).hint(Component.literal("seconds"))
                .onSubmit(this::save);
        max.setFilter(RateLimitsScreen::digitsOnly);
        window.setFilter(RateLimitsScreen::digitsOnly);
        this.scope = new CpEditBox(Component.literal("Who shares the budget"), 200)
                .hint(Component.literal("server, network or hub,survival"))
                // server and network stand alone; after a comma only server names make sense.
                .completes(text -> (text.contains(",") ? Completions.servers(List.of())
                        : Completions.servers(List.of("server", "network"))).propose(text))
                .onSubmit(this::applyScope);
        this.search = new CpEditBox(Component.literal("Search rate limits"), 64)
                .hint(Component.literal("Search (Ctrl+F)"))
                .completes(Completions.search(() -> this.data.rules().stream().map(RateLimitsData.Rule::name).toList()))
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
        this.levelHolder = new CpEditBox(Component.literal("Server, grade or player"), 64)
                .completes(text -> (switch (levelKind) {
                    case "SERVER" -> Completions.servers(List.of("here"));
                    case "PLAYER" -> Completions.players();
                    default -> Completions.grades();
                }).propose(text));
        this.levelValue = new CpEditBox(Component.literal("Limit"), 32)
                .completes(text -> Completions.search(this::valueExamples).propose(text))
                .onSubmit(this::setLevel);
        this.levelContext = new CpEditBox(Component.literal("Where it applies"), 128)
                .hint(Component.literal("everywhere, or server=hub"))
                .completes(Completions.contexts())
                .onSubmit(this::setLevel);
        this.levelList = new CpList<RateLimitsData.Level>(Component.literal("Levels of this limit"), ROW)
                .renderer(this::renderLevel)
                .label(level -> kindLabel(level.kind()) + " " + level.holder() + ", " + level.value()
                        + (level.context().isEmpty() ? "" : ", " + level.context()))
                .identity(level -> level.kind() + "|" + level.holder() + "|" + level.context())
                .emptyText("Nothing adjusts this limit: every player gets the rule's numbers.")
                .onSelect(level -> {
                    levelKind = level.kind();
                    levelHolder.setValue(level.holder());
                    levelValue.setValue(level.value());
                    levelContext.setValue(level.context());
                    rebuild();
                });
        refilter();
    }

    /** What the value field proposes: the rule's numbers doubled and multiplied by five, and unlimited. */
    private List<String> valueExamples() {
        RateLimitsData.Rule rule = ruleList.getSelected();
        if (rule == null) return List.of();
        String window = formatWindow(rule.windowSeconds());
        List<String> examples = new java.util.ArrayList<>(List.of(rule.max() * 2 + "/" + window, rule.max() * 5 + "/" + window));
        if (!levelKind.equals("SERVER")) examples.add("unlimited");
        return examples;
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
        levelList.setItems(after == null ? List.of() : after.levels());
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

    private int scopeY() {
        return togglesY() + BUTTON + 4;
    }

    /** Top of the server toggles in the servers view, below their title, where the toggle rows usually start. */
    private int serversY() {
        return togglesY() + 12;
    }

    /** Whether the lower part of the form shows the selected rule's servers instead of its other settings. */
    private boolean showingServers() {
        RateLimitsData.Rule rule = ruleList.getSelected();
        return serversView && rule != null && showsServers(rule.servers());
    }

    /** Whether the lower part of the form shows what adjusts the selected rule. */
    private boolean showingLevels() {
        return levelsView && ruleList.getSelected() != null;
    }

    /** Top of the levels list, under the kind row and the two field rows. */
    private int levelsListY() {
        return togglesY() + (BUTTON + 4) * 3 + 14;
    }

    private Rect unlimitedArea() {
        Rect in = inner();
        int top = scopeY() + BUTTON + 18;
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
            if (showsServers(rule.servers())) {
                // A view switch rather than more rows: the form already fills a small window.
                CpButton servers = CpButton.neutral(Component.literal("Servers"), () -> {
                            serversView = !serversView;
                            if (serversView) levelsView = false;
                            rebuild();
                        })
                        .icon(Icon.HOME).selected(serversView)
                        .tooltip(Component.literal("The cluster members this limit is enforced on. Click again to go back."));
                int serversW = servers.preferredWidth(font, 6);
                if (saveRowRight + 4 + serversW > in.right() - BUTTON - 4) {
                    servers.iconOnly(Icon.HOME);
                    serversW = BUTTON;
                }
                addRenderableWidget(servers.at(new Rect(saveRowRight + 4, saveY(), serversW, BUTTON)));
                saveRowRight += 4 + serversW;
            }
            CpButton levels = CpButton.neutral(Component.literal("Levels"), () -> {
                        levelsView = !levelsView;
                        if (levelsView) serversView = false;
                        rebuild();
                    })
                    .icon(Icon.USER).selected(levelsView)
                    .tooltip(Component.literal("A server's own limit, a grade's or a player's value. Click again to go back."));
            int levelsW = levels.preferredWidth(font, 6);
            if (saveRowRight + 4 + levelsW > in.right() - BUTTON - 4) {
                levels.iconOnly(Icon.USER);
                levelsW = BUTTON;
            }
            addRenderableWidget(levels.at(new Rect(saveRowRight + 4, saveY(), levelsW, BUTTON)));
            saveRowRight += 4 + levelsW;
            CpButton remove = CpButton.danger(Component.literal("Remove limit"), () -> confirmRemove(rule))
                    .iconOnly(Icon.TRASH).enabled(editable);
            addRenderableWidget(remove.at(new Rect(in.right() - BUTTON, saveY(), BUTTON, BUTTON)));

            if (showingLevels()) {
                buildLevels(rule, editable, in);
                return;
            }
            if (showingServers()) {
                buildServerToggles(new Rect(in.x(), serversY(), in.w(),
                                serverTogglesHeight(in.w(), rule.servers(), ClusterView.RATE_LIMITS)), rule.servers(),
                        ClusterView.RATE_LIMITS, "rate limits", editable,
                        list -> act(GuiAction.RATELIMIT_SERVERS, rule.name(), list));
                return;
            }
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
            Rect[] scopeRow = new Rect(in.x(), scopeY(), in.w(), BUTTON).split(4, 60);
            addRenderableWidget(scope.at(new Rect(scopeRow[0].x(), scopeY() + (BUTTON - FIELD) / 2, scopeRow[0].w(), FIELD)));
            scope.setEditable(editable);
            if (!scope.isFocused()) scope.setValue(rule.scope());
            addRenderableWidget(CpButton.neutral(Component.literal("Share"), this::applyScope).enabled(editable)
                    .tooltip(Component.literal("In cluster mode, who counts against one budget. server: each server counts "
                            + "its own. network: one budget for every server. hub,survival: those servers share one, the "
                            + "others count on their own. Outside a cluster every server counts its own."))
                    .at(scopeRow[1]));
        }
        Rect unlimited = unlimitedArea();
        if (unlimited.h() >= 24) addRenderableWidget(unlimitedList.at(unlimited));
    }

    /** Kind row, holder and value, context and the buttons, then the levels already set. */
    private void buildLevels(RateLimitsData.Rule rule, boolean editable, Rect in) {
        boolean serverAllowed = !"network".equals(rule.scope());
        boolean holdersAllowed = !data.holdersInLuckPerms();
        if (levelKind.equals("SERVER") && !serverAllowed || !levelKind.equals("SERVER") && !holdersAllowed) {
            levelKind = serverAllowed ? "SERVER" : "GRADE";
        }
        int third = (in.w() - 8) / 3;
        String[][] kinds = {{"SERVER", "Server"}, {"GRADE", "Grade"}, {"PLAYER", "Player"}};
        for (int i = 0; i < kinds.length; i++) {
            String kind = kinds[i][0];
            boolean allowed = kind.equals("SERVER") ? serverAllowed : holdersAllowed;
            String why = kind.equals("SERVER")
                    ? (serverAllowed ? "A limit of its own for one cluster member that counts its uses alone."
                            : "Every member shares this counter, and a shared counter means one limit.")
                    : (holdersAllowed ? "A value for everyone holding the grade, or for one player: it wins over the servers'."
                            : "Under LuckPerms, set it there as the meta customperm.ratelimit." + rule.name().toLowerCase(Locale.ROOT) + ".");
            int x = in.x() + i * (third + 4);
            int w = i == kinds.length - 1 ? in.right() - x : third;
            addRenderableWidget(CpButton.neutral(Component.literal(kinds[i][1]), () -> {
                        levelKind = kind;
                        rebuild();
                    })
                    .selected(levelKind.equals(kind)).enabled(allowed)
                    .tooltip(Component.literal(why))
                    .at(new Rect(x, togglesY(), w, BUTTON)));
        }
        boolean server = levelKind.equals("SERVER");
        levelHolder.hint(Component.literal(server ? "server, or here" : levelKind.equals("PLAYER") ? "player" : "grade"));
        levelValue.hint(Component.literal(server ? "10/1h" : "10/1h, 10 or unlimited"));
        int fieldsY = togglesY() + BUTTON + 4;
        Rect[] fields = new Rect(in.x(), fieldsY + (BUTTON - FIELD) / 2, in.w(), FIELD).split(GAP, in.w() * 45 / 100);
        addRenderableWidget(levelHolder.at(fields[0]));
        addRenderableWidget(levelValue.at(fields[1]));
        levelHolder.setEditable(editable);
        levelValue.setEditable(editable);

        int actionsY = fieldsY + BUTTON + 4;
        RateLimitsData.Level selected = levelList.getSelected();
        CpButton set = CpButton.accent(Component.literal("Set"), this::setLevel).icon(Icon.CHECK).enabled(editable);
        int setW = set.preferredWidth(font, 8);
        int right = in.right();
        if (selected != null) {
            addRenderableWidget(CpButton.danger(Component.literal("Remove level"), () -> act(GuiAction.RATELIMIT_LEVEL_CLEAR,
                            rule.name(), selected.kind(), selected.holder(), selected.context()))
                    .iconOnly(Icon.TRASH).enabled(editable)
                    .at(new Rect(right - BUTTON, actionsY, BUTTON, BUTTON)));
            right -= BUTTON + 4;
        }
        addRenderableWidget(set.at(new Rect(right - setW, actionsY, setW, BUTTON)));
        if (!server) {
            addRenderableWidget(levelContext.at(new Rect(in.x(), actionsY + (BUTTON - FIELD) / 2, right - setW - GAP - in.x(), FIELD)));
            levelContext.setEditable(editable);
        }

        Rect list = new Rect(in.x(), levelsListY(), in.w(), in.bottom() - levelsListY());
        if (list.h() >= 24) addRenderableWidget(levelList.at(list));
    }

    // ------------------------------------------------------------------ actions

    private void setLevel() {
        RateLimitsData.Rule rule = ruleList.getSelected();
        if (rule == null) return;
        String holder = levelHolder.getValue().trim();
        String value = levelValue.getValue().trim();
        if (holder.isEmpty() || value.isEmpty()) {
            status("A level needs " + (levelKind.equals("SERVER") ? "a server" : levelKind.equals("PLAYER") ? "a player" : "a grade")
                    + " and a limit, such as 10/1h.", false);
            return;
        }
        String context = levelKind.equals("SERVER") ? "" : levelContext.getValue().trim();
        act(GuiAction.RATELIMIT_LEVEL_SET, rule.name(), levelKind, holder, value, context);
    }

    private void applyScope() {
        RateLimitsData.Rule rule = ruleList.getSelected();
        String typed = scope.getValue().trim();
        if (rule == null || typed.isEmpty() || typed.equalsIgnoreCase(rule.scope())) return;
        act(GuiAction.RATELIMIT_SCOPE, rule.name(), typed);
    }

    private void startNew() {
        serversView = false;
        levelsView = false;
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
        // One badge for "not in effect here": disabled, or limited to other cluster members.
        if (!rule.enabled() || elsewhereOnly(rule.servers())) {
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
                rule.enabled() && !elsewhereOnly(rule.servers()) ? Palette.TEXT : Palette.TEXT_MUTE);
    }

    private void renderLevel(GuiGraphics g, Font font, RateLimitsData.Level level, Rect r, boolean hovered, boolean selected) {
        int y = r.y() + (r.h() - 8) / 2;
        String badge = kindLabel(level.kind());
        int bw = font.width(badge) + 6;
        Skin.badge(g, font, badge, r.x() + 4, r.centerY(), switch (level.kind()) {
            case "SERVER" -> Palette.TEXT_DIM;
            case "PLAYER" -> Palette.WARN;
            default -> Palette.TEXT_MUTE;
        });
        String right = level.value() + (level.context().isEmpty() ? "" : "  " + level.context())
                + (level.secondsLeft() > 0 ? "  " + formatWindow((int) Math.min(Integer.MAX_VALUE, level.secondsLeft())) + " left" : "");
        int rw = Math.min(font.width(right), (r.w() - bw - 16) / 2);
        Skin.text(g, font, right, r.right() - 4 - rw, y, rw, Palette.TEXT_DIM);
        Skin.text(g, font, level.holder(), r.x() + 8 + bw, y, r.w() - bw - rw - 18, Palette.TEXT);
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "SERVER" -> "SERVER";
            case "PLAYER" -> "PLAYER";
            default -> "GRADE";
        };
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
        if (showingServers()) {
            Skin.text(g, font, "ENFORCED ON", in.x(), serversY() - 11, in.w(), Palette.TEXT_MUTE);
            return;
        }
        if (showingLevels()) {
            Skin.text(g, font, "RULE, THEN SERVER, THEN GRADE, THEN PLAYER", in.x(), togglesY() - 11, in.w(), Palette.TEXT_MUTE);
            int listY = levelsListY();
            String title = data.holdersInLuckPerms() ? "SET HERE (GRADES AND PLAYERS: IN LUCKPERMS)" : "SET HERE";
            if (in.bottom() - listY >= 24) Skin.text(g, font, title, in.x(), listY - 11, in.w(), Palette.TEXT_MUTE);
            return;
        }
        Rect unlimited = unlimitedArea();
        if (unlimited.h() >= 24) {
            Skin.text(g, font, "WITHOUT A LIMIT", unlimited.x(), unlimited.y() - 11, unlimited.w(), Palette.TEXT_MUTE);
        }
    }
}
