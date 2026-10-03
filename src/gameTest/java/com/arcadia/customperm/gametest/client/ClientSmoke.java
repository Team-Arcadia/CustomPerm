/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import com.arcadia.customperm.client.gui.admin.AdminScreen;
import com.arcadia.customperm.client.gui.admin.CommandsScreen;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.gui.CommandsData;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.RateLimitsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.arcadia.customperm.gametest.client.Drive.*;
import static com.arcadia.customperm.gametest.client.StepQueue.SECOND;

/**
 * The "real client" steps of the test procedure that a singleplayer world can reach, played by themselves
 * ({@code ./gradlew runClientSmoke}): every page and view opens and draws (V02), at four window sizes nothing is cut,
 * past the window or on top of something else (V03), the decorated name in chat and in the tab list (V04), completion
 * following a grant and a removal without relogging (V05), completion in the fields (V08), rate limit levels (V10) and
 * who may use a command (V11). Cluster views (V06, V07) need members and live in the cluster bench.
 *
 * <p>Verdict in {@code run/clientsmoke/smoke-report.txt}, screenshots in {@code run/clientsmoke/screenshots} for a
 * person to look at; nothing compares pixels.</p>
 */
@EventBusSubscriber(modid = "customperm", value = Dist.CLIENT)
public final class ClientSmoke {
    static final boolean ACTIVE = Boolean.getBoolean("customperm.clientSmoke");
    private static final String WORLD = "customperm-smoke";
    private static final String ME = "SmokeAdmin";
    private static final String GRADE = "smoke_vip";
    private static final String PREFIX = "[Smoke] ";
    private static final String NICK = "Smokey";
    private static final String NODE = "customperm.smoke.node";
    private static final String LIMITED = "weather";
    private static final StepQueue RUN = new StepQueue("smoke");
    private static final List<String> CHAT = new CopyOnWriteArrayList<>();
    private static boolean opened;

    private ClientSmoke() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIVE) CHAT.add(event.getMessage().getString());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        if (!opened && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) {
            opened = true;
            createWorld(mc);
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (!RUN.started()) {
            plan();
            RUN.start();
        }
        RUN.tick();
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    // ------------------------------------------------------------------ plan

    private static void plan() {
        RUN.server("seed", 3 * SECOND, ClientSmoke::seed);
        decoration();
        completionFollowsGrants();
        Drive.drawsEverywhere(RUN, views());
        fields();
        levels();
        who();
        RUN.add("close", SECOND, () -> Minecraft.getInstance().setScreen(null));
        RUN.add("report", SECOND, () -> RUN.finish(Minecraft.getInstance().gameDirectory.toPath().resolve("smoke-report.txt")));
    }

    private static void seed(MinecraftServer server) {
        UUID me = Minecraft.getInstance().player.getUUID();
        List<String> commands = List.of(
                "customperm names on",
                "customperm grade create " + GRADE,
                "customperm grade create smoke_staff",
                "customperm grade prefix " + GRADE + " add 5 " + PREFIX,
                "customperm grade addperm " + GRADE + " " + NODE,
                "customperm grade addperm " + GRADE + " customperm.command." + LIMITED,
                "customperm command add " + LIMITED,
                "customperm ratelimit set " + LIMITED + " 3 3600",
                "customperm alias add smoke_alias say smoke",
                "customperm grade assign " + ME + " " + GRADE,
                "customperm user nick " + ME + " set " + NICK);
        for (String command : commands) {
            List<String> out = ServerCommands.run(server, command);
            RUN.info("seed /" + command + " -> " + String.join(" | ", out));
        }
        Grants.allow(me, "customperm.*");
        for (int i = 1; i <= 5; i++) {
            try (TestPlayer player = TestPlayer.join(server.overworld(), "smoke_p" + i, 0)) {
                ServerCommands.run(server, "customperm grade assign smoke_p" + i + " smoke_staff");
                player.drain();
            }
        }
    }

    // ------------------------------------------------------------------ V04, V05

    private static void decoration() {
        RUN.add("V04 chat", SECOND, () -> {
            CHAT.clear();
            Minecraft.getInstance().player.connection.sendChat("hello smoke");
        });
        RUN.await("V04", "chat line carries the decorated name and the message untouched", 0,
                () -> CHAT.stream().anyMatch(ClientSmoke::decoratedChat),
                () -> "received " + CHAT);
        RUN.await("V04", "tab list shows the decorated name", 0, () -> {
            String tab = tabName();
            return tab != null && tab.contains(PREFIX.trim()) && tab.contains(NICK);
        }, () -> "tab list name " + tabName());
    }

    private static boolean decoratedChat(String line) {
        int body = line.lastIndexOf("hello smoke");
        return body > 0 && line.endsWith("hello smoke") && line.substring(0, body).contains(PREFIX.trim())
                && line.substring(0, body).contains(NICK);
    }

    private static String tabName() {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(Minecraft.getInstance().player.getUUID());
        return info == null || info.getTabListDisplayName() == null ? null : info.getTabListDisplayName().getString();
    }

    private static void completionFollowsGrants() {
        RUN.await("V05", "the exposed command is offered while its node is held", 0, () -> offered(LIMITED), () -> "/" + LIMITED);
        RUN.server("V05 deny", 0, s -> ServerCommands.run(s, "customperm user adddeny " + ME + " customperm.command." + LIMITED));
        int[] start = {0};
        RUN.add("V05 clock", 0, () -> start[0] = Minecraft.getInstance().player.tickCount);
        RUN.await("V05", "a denial takes the command out of completion without relogging", 0, () -> !offered(LIMITED),
                () -> "after " + (Minecraft.getInstance().player.tickCount - start[0]) + " ticks");
        RUN.server("V05 allow", 0, s -> ServerCommands.run(s, "customperm user removedeny " + ME + " customperm.command." + LIMITED));
        RUN.add("V05 clock again", 0, () -> start[0] = Minecraft.getInstance().player.tickCount);
        RUN.await("V05", "removing it brings the command back", 0, () -> offered(LIMITED),
                () -> "after " + (Minecraft.getInstance().player.tickCount - start[0]) + " ticks");
    }

    private static boolean offered(String command) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getCommands().getRoot().getChild(command) != null;
    }

    // ------------------------------------------------------------------ views

    private static List<View> views() {
        List<View> views = new ArrayList<>();
        views.add(new View("dashboard", GuiPage.DASHBOARD, () -> { }));
        views.add(new View("commands", GuiPage.COMMANDS, () -> { }));
        views.add(new View("commands-who", GuiPage.COMMANDS, () -> {
            press("Exposed");
            list("Commands").selectByKey(LIMITED);
            press("Who");
        }));
        views.add(new View("aliases", GuiPage.ALIASES, () -> list("Aliases").selectByKey("smoke_alias")));
        views.add(new View("ratelimits", GuiPage.RATE_LIMITS, () -> list("Rate limits").selectByKey(LIMITED)));
        views.add(new View("ratelimits-levels", GuiPage.RATE_LIMITS, () -> {
            list("Rate limits").selectByKey(LIMITED);
            press("Levels");
        }));
        for (String tab : List.of("NODES", "PARENTS", "PLAYERS", "CHAT", "META")) {
            views.add(new View("grades-" + tab.toLowerCase(), GuiPage.GRADES, () -> {
                list("Grades").selectByKey(GRADE);
                setTab(tab);
            }));
        }
        for (String tab : List.of("NODES", "CHAT", "META", "TRACKS")) {
            views.add(new View("players-" + tab.toLowerCase(), GuiPage.PLAYERS, () -> {
                list("Players").selectByKey(Minecraft.getInstance().player.getUUID().toString());
                setTab(tab);
            }));
        }
        // Import only exists while LuckPerms runs, which it does not in singleplayer: V09 is the server smoke's.
        views.add(new View("logs", GuiPage.LOGS, () -> { }));
        views.add(new View("help", GuiPage.HELP, () -> { }));
        return views;
    }

    // ------------------------------------------------------------------ V08

    private static void fields() {
        open(RUN, new View("grades-nodes", GuiPage.GRADES, () -> {
            list("Grades").selectByKey(GRADE);
            setTab("NODES");
        }));
        completes("Permission node", "customperm.smo", NODE);
        completes("World", "overw", "overworld");
        open(RUN, new View("grades-parents", GuiPage.GRADES, () -> {
            list("Grades").selectByKey(GRADE);
            setTab("PARENTS");
        }));
        completes("Parent grade", "smoke_st", "smoke_staff");
        completesNothing("Parent grade", "zzqx");
        open(RUN, new View("grades-players", GuiPage.GRADES, () -> {
            list("Grades").selectByKey(GRADE);
            setTab("PLAYERS");
        }));
        completes("Player name", "smoke_p", "smoke_p1");
        open(RUN, new View("ratelimits", GuiPage.RATE_LIMITS, () -> { }));
        completes("Command or alias", "weat", LIMITED);
        completes("Command or alias", "smoke_al", "smoke_alias");
        open(RUN, new View("commands-who", GuiPage.COMMANDS, () -> {
            press("Exposed");
            list("Commands").selectByKey(LIMITED);
            press("Who");
        }));
        completes("Grade or player", "smoke_v", GRADE);
    }

    private static void completes(String field, String typed, String expected) {
        RUN.add("V08 " + field + " " + typed, 2, () -> RUN.check("V08", field + " completes \"" + typed + "\" with " + expected, () -> {
            List<String> candidates = candidates(field, typed);
            if (candidates.stream().noneMatch(c -> c.equals(expected) || c.endsWith("=" + expected) || c.endsWith(":" + expected))) {
                throw new AssertionError("offered " + candidates);
            }
            return candidates.size() + " candidate(s)";
        }));
    }

    private static void completesNothing(String field, String typed) {
        RUN.add("V08 " + field + " " + typed, 2, () -> RUN.check("V08", field + " invents nothing for \"" + typed + "\"", () -> {
            List<String> candidates = candidates(field, typed);
            if (!candidates.isEmpty()) throw new AssertionError("offered " + candidates);
            return "no candidate";
        }));
    }

    // ------------------------------------------------------------------ V10

    private static void levels() {
        open(RUN, new View("ratelimits-levels", GuiPage.RATE_LIMITS, () -> {
            list("Rate limits").selectByKey(LIMITED);
            press("Levels");
        }));
        RUN.add("V10 grade level", 2, () -> {
            press("Grade");
            field("Server, grade or player").setValue(GRADE);
            field("Limit").setValue("10/1h");
            press("Set");
        });
        RUN.await("V10", "a grade level set from the page is listed", 0, () -> hasLevel("GRADE", GRADE), ClientSmoke::levelList);
        RUN.add("V10 player level", 2, () -> {
            press("Player");
            field("Server, grade or player").setValue(ME);
            field("Limit").setValue("unlimited");
            press("Set");
        });
        RUN.await("V10", "a player level set from the page is listed", 0, () -> hasLevel("PLAYER", ME), ClientSmoke::levelList);
        RUN.server("V10 show", 0, s -> RUN.check("V10", "ratelimit show lists the same levels", () -> {
            String out = String.join(" | ", ServerCommands.run(s, "customperm ratelimit show " + LIMITED));
            if (!out.contains(GRADE) || !out.contains(ME)) throw new AssertionError(out);
            return out;
        }));
        RUN.add("V10 remove", 2, () -> {
            CpList<RateLimitsData.Level> levels = list("Levels of this limit");
            levels.selectByKey("GRADE|" + GRADE + "|");
            if (levels.getSelected() == null) {
                levels.items().stream().filter(l -> l.kind().equals("GRADE")).findFirst()
                        .ifPresent(l -> levels.selectByKey(l.kind() + "|" + l.holder() + "|" + l.context()));
            }
            rebuild();
            press("Remove level");
        });
        RUN.await("V10", "the bin removes the selected level", 0, () -> !hasLevel("GRADE", GRADE), ClientSmoke::levelList);
    }

    private static boolean hasLevel(String kind, String holder) {
        if (!(screen() instanceof AdminScreen s) || s.page() != GuiPage.RATE_LIMITS) return false;
        CpList<RateLimitsData.Level> levels = optionalList("Levels of this limit");
        return levels != null && levels.items().stream().anyMatch(l -> l.kind().equals(kind) && l.holder().equalsIgnoreCase(holder));
    }

    private static String levelList() {
        CpList<RateLimitsData.Level> levels = optionalList("Levels of this limit");
        return (levels == null ? "no level list" : levels.items().toString()) + ", status \"" + status() + "\"";
    }

    // ------------------------------------------------------------------ V11

    private static void who() {
        open(RUN, new View("commands-who", GuiPage.COMMANDS, () -> {
            press("Exposed");
            list("Commands").selectByKey(LIMITED);
            press("Who");
        }));
        RUN.await("V11", "Who lists the grade whose entries name the node", 2, () -> holder(GRADE) != null, ClientSmoke::holders);
        RUN.add("V11 select", 2, () -> {
            selectHolder(holder(GRADE));
            rebuild();
        });
        String[] seen = {""};
        // Allowed, then denied, then no entry at all, then allowed again: the three states of the toggle.
        for (int click = 1; click <= 3; click++) {
            int n = click;
            RUN.add("V11 click " + n, 2, () -> {
                seen[0] = selectedHolder() == null ? "?" : selectedHolder().everywhere();
                press("everywhere");
            });
            RUN.await("V11", "click " + n + " on everywhere moves the grade on from its state and says so", 0,
                    () -> selectedHolder() != null && !Objects.equals(selectedHolder().everywhere(), seen[0])
                            && status().contains(GRADE),
                    () -> "before \"" + seen[0] + "\", now " + holders() + ", status \"" + status() + "\"");
            RUN.add("V11 same holder " + n, 0, () -> RUN.check("V11", "the view stays on the grade after click " + n, () -> {
                CommandsData.Holder selected = selectedHolder();
                if (selected == null || !selected.id().equals(GRADE)) throw new AssertionError("selected " + selected);
                if (optionalList("Who decides") != null) throw new AssertionError("the view went back to the list");
                return "\"" + seen[0] + "\" -> \"" + selected.everywhere() + "\"";
            }));
        }
        RUN.server("V11 grade list", 0, s -> RUN.check("V11", "grade list agrees with the page after the round trip", () -> {
            String out = String.join(" | ", ServerCommands.run(s, "customperm grade list " + GRADE));
            if (!out.contains("customperm.command." + LIMITED)) throw new AssertionError(out);
            return out;
        }));
    }

    /** The holders of the Who view, read from the screen: the list widget itself is hidden while one is selected. */
    @SuppressWarnings("unchecked")
    private static CpList<CommandsData.Holder> holderList() {
        if (!(screen() instanceof CommandsScreen s)) return null;
        try {
            Field field = CommandsScreen.class.getDeclaredField("holderList");
            field.setAccessible(true);
            return (CpList<CommandsData.Holder>) field.get(s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CommandsScreen.holderList moved; update ClientSmoke", e);
        }
    }

    private static CommandsData.Holder selectedHolder() {
        CpList<CommandsData.Holder> list = holderList();
        return list == null ? null : list.getSelected();
    }

    private static CommandsData.Holder holder(String id) {
        CpList<CommandsData.Holder> list = holderList();
        return list == null ? null : list.items().stream().filter(h -> !h.player() && h.id().equals(id)).findFirst().orElse(null);
    }

    private static String holders() {
        CpList<CommandsData.Holder> list = holderList();
        return list == null ? "no Who view" : list.items().stream().map(h -> h.id() + "=" + h.everywhere()).collect(Collectors.joining(", "));
    }

    private static void selectHolder(CommandsData.Holder holder) {
        if (holder == null) throw new IllegalStateException("no holder " + GRADE + " in " + holders());
        try {
            Method key = CommandsScreen.class.getDeclaredMethod("holderKey", CommandsData.Holder.class);
            key.setAccessible(true);
            holderList().selectByKey(key.invoke(null, holder));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CommandsScreen.holderKey moved; update ClientSmoke", e);
        }
    }
}
