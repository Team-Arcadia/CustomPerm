/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.spark;

import com.arcadia.customperm.client.gui.admin.AdminScreen;
import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.CpEditBox;
import com.arcadia.customperm.client.gui.kit.CpList;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Scenarios S01 to S03 of the 2.0.0 test procedure, played by themselves ({@code ./gradlew runSparkScenario}).
 * spark must be in {@code run/spark/mods}, never in the build. Every report is saved to a file and never
 * uploaded; the verdict of each expectation, with the numbers behind it, goes to {@code run/spark/spark-report.txt}.
 *
 * <p>Singleplayer, so one JVM holds both sides: a heap summary counts client and server classes at once, and the
 * S01 "admin disconnects" check uses a simulated admin (the local player cannot leave without stopping the server).
 * The Import page needs LuckPerms, which does not run in singleplayer, so S01 visits every other page.</p>
 */
@EventBusSubscriber(modid = "customperm", value = Dist.CLIENT)
public final class SparkScenario {
    static final boolean ACTIVE = Boolean.getBoolean("customperm.spark");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "customperm-spark";
    private static final int SECOND = 20;
    private static final int KNOWN_PLAYERS = 300;
    private static final int GUI_CYCLES = 50;
    /** The server allows 40 page requests per 10 s; one every 8 ticks stays under it. */
    private static final int PAGE_GAP = 8;
    private static final int STEP_TIMEOUT = 60 * SECOND;
    private static final int RELOADS = 10;
    private static final String CLIENT = "com.arcadia.customperm.client";
    private static final String LOG_ENTRY = "com.arcadia.customperm.log.LogEntry";
    private static final String GRADE_LIMIT_TEXT = SparkLoad.GRADE_LIMIT + "/1d";
    /** Which scenarios to play, {@code -PsparkScenarios=s03} for one; all three by default. */
    private static final List<String> ONLY = List.of(System.getProperty("customperm.spark.scenarios", "s01,s02,s03").split(","));

    private record Step(String name, int delay, BooleanSupplier ready, Runnable action) {
    }

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static final List<String> CHAT = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final Map<String, Path> FILES = new HashMap<>();
    private static final Map<String, Integer> NUMBERS = new TreeMap<>();
    private static boolean opened;
    private static boolean planned;
    private static int wait;
    private static int waited;
    private static CompletableFuture<?> serverWork = CompletableFuture.completedFuture(null);
    private static Set<Path> sparkFilesBefore = Set.of();

    private SparkScenario() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent.System event) {
        if (ACTIVE) CHAT.add(event.getMessage().getString());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ACTIVE) return;
        Minecraft mc = Minecraft.getInstance();
        // A first launch shows the accessibility screen before the title screen.
        if (!opened && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)) {
            opened = true;
            createWorld(mc);
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (!planned) {
            planned = true;
            plan();
            wait = STEPS.peek().delay();
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.peek();
        if (step == null) return;
        if (!step.ready().getAsBoolean()) {
            if (++waited < STEP_TIMEOUT) return;
            FAILURES.add("step " + step.name() + " never became ready");
            LOGGER.error("[spark] step {} timed out", step.name());
        }
        waited = 0;
        STEPS.poll();
        LOGGER.info("[spark] {}", step.name());
        try {
            step.action().run();
        } catch (RuntimeException e) {
            FAILURES.add("step " + step.name() + " threw " + e);
            LOGGER.error("[spark] step {} failed", step.name(), e);
        }
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delay();
    }

    // A fresh flat world each time: the Gradle task deletes the previous one and the CustomPerm config.
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
        server("seed", 10 * SECOND, s -> SparkLoad.seed(s, Minecraft.getInstance().player.getUUID(), KNOWN_PLAYERS));

        if (ONLY.contains("s01")) s01Plan();
        if (ONLY.contains("s02")) s02Plan();
        if (ONLY.contains("s03")) s03Plan();
        add("report", 5 * SECOND, SparkScenario::report);
    }

    private static void s01Plan() {
        // S01: the interface opened and closed 50 times, a completed field typed in each time. One cycle first, so
        // the baseline already holds what loading the classes creates once (enum constants, lambda singletons).
        guiCycle();
        add("s01.warmup.close", 2, () -> Minecraft.getInstance().setScreen(null));
        heap("s01.before", 5 * SECOND);
        for (int i = 0; i < GUI_CYCLES; i++) guiCycle();
        add("s01.close", SECOND, () -> Minecraft.getInstance().setScreen(null));
        server("s01.admin", SECOND, SparkLoad::adminVisit);
        heap("s01.after", 5 * SECOND);
        server("s01.vocabulary", 0, s -> NUMBERS.put("s01.vocabulary", SparkLoad.sentVocabularies()));
    }

    private static void s02Plan() {
        // S02: the Players page over 300 known players, its node completion open. First idle, then in use.
        page(GuiPage.PLAYERS, SECOND);
        add("s02.pick", 10, () -> list("Players").selectByKey(firstKey(list("Players"))));
        add("s02.type", 10, () -> type(field("Permission node"), "customperm."));
        profile("s02.idle", "sparkc", 30);
        add("s02.active.start", SECOND, () -> command("sparkc profiler start"));
        for (int i = 0; i < 60; i++) {
            int n = i;
            add("s02.use", SECOND / 2, () -> useFor(n));
            add("s02.scroll", SECOND / 2, () -> scroll(list("Players"), n));
        }
        profileStop("s02.active", "sparkc");
        add("s02.close", SECOND, () -> Minecraft.getInstance().setScreen(null));
    }

    private static void s03Plan() {
        // S03: the limited command hammered by eight players for five minutes, then ten reloads.
        heap("s03.before", 2 * SECOND);
        server("s03.players", SECOND, SparkLoad::startHammering);
        add("s03.profile.start", SECOND, () -> command("spark profiler start"));
        add("s03.tps", 290 * SECOND, () -> command("spark tps"));
        profileStop("s03.profile", "spark");
        server("s03.players.stop", SECOND, s -> {
            SparkLoad.stopHammering();
            NUMBERS.put("s03.typed", SparkLoad.typed());
            NUMBERS.put("s03.refused", SparkLoad.refused());
            NUMBERS.put("s03.history.load", SparkLoad.historySize());
        });
        for (int i = 0; i < RELOADS; i++) server("s03.reload", 2 * SECOND, s -> SparkLoad.run(s, "customperm reload"));
        server("s03.history", 2 * SECOND, s -> NUMBERS.put("s03.history", SparkLoad.historySize()));
        heap("s03.after", 5 * SECOND);
    }

    /** One S01 cycle: every page, the Who and Levels views, a completed field typed in, then closed. */
    private static void guiCycle() {
        page(GuiPage.GRADES, PAGE_GAP);
        page(GuiPage.PLAYERS, PAGE_GAP);
        add("s01.players.pick", 2, () -> list("Players").selectByKey(firstKey(list("Players"))));
        add("s01.players.type", 2, () -> type(field("Permission node"), "customperm.sp"));
        page(GuiPage.COMMANDS, PAGE_GAP);
        add("s01.commands.pick", 2, () -> list("Commands").selectByKey(SparkLoad.LIMITED));
        add("s01.commands.who", 2, () -> press("Who"));
        page(GuiPage.RATE_LIMITS, PAGE_GAP);
        add("s01.limits.pick", 2, () -> list("Rate limits").selectByKey(SparkLoad.LIMITED));
        add("s01.limits.levels", 2, () -> press("Levels"));
        page(GuiPage.ALIASES, PAGE_GAP);
        add("s01.cycle.close", 2, () -> Minecraft.getInstance().setScreen(null));
    }

    // ------------------------------------------------------------------ steps

    private static void add(String name, int delay, Runnable action) {
        STEPS.add(new Step(name, delay, () -> true, action));
    }

    private static void add(String name, int delay, BooleanSupplier ready, Runnable action) {
        STEPS.add(new Step(name, delay, ready, action));
    }

    /** Asks for a page the way the sidebar does, then waits until it shows. */
    private static void page(GuiPage page, int delay) {
        add("page." + page.id(), delay, () -> PacketDistributor.sendToServer(new GuiRequestPayload(page.id())));
        add("page." + page.id() + ".shown", 0, () -> Minecraft.getInstance().screen instanceof AdminScreen s && s.page() == page,
                () -> { });
    }

    /** Work on the server thread; the next step waits for it to finish. */
    private static void server(String name, int delay, Consumer<MinecraftServer> work) {
        add(name, delay, () -> true, () -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            serverWork = server.submit(() -> work.accept(server));
        });
        add(name + ".done", 0, () -> serverWork.isDone(), () -> {
            if (serverWork.isCompletedExceptionally()) {
                serverWork.exceptionally(e -> {
                    FAILURES.add("step " + name + " threw " + e);
                    return null;
                });
            }
        });
    }

    /** A heap summary saved to a file (spark takes it after a full GC, so only live objects count). */
    private static void heap(String name, int delay) {
        add(name, delay, () -> {
            sparkFilesBefore = sparkFiles();
            command("spark heapsummary --save-to-file");
        });
        awaitFile(name, ".sparkheap");
    }

    private static void profile(String name, String spark, int seconds) {
        add(name + ".start", SECOND, () -> command(spark + " profiler start"));
        add(name + ".wait", seconds * SECOND, () -> { });
        profileStop(name, spark);
    }

    private static void profileStop(String name, String spark) {
        add(name + ".stop", 0, () -> {
            sparkFilesBefore = sparkFiles();
            command(spark + " profiler stop --save-to-file");
        });
        awaitFile(name, ".sparkprofile");
    }

    /** Waits until spark has written a new file of that kind and it has stopped growing. */
    private static void awaitFile(String name, String extension) {
        long[] lastSize = {-1};
        add(name + ".saved", SECOND, () -> {
            Path file = newSparkFile(extension);
            if (file == null) return false;
            try {
                long size = Files.size(file);
                boolean stable = size > 0 && size == lastSize[0];
                lastSize[0] = size;
                return stable;
            } catch (IOException e) {
                return false;
            }
        }, () -> FILES.put(name, newSparkFile(extension)));
    }

    // ------------------------------------------------------------------ driving the screens

    private static Stream<? extends GuiEventListener> widgets() {
        return Minecraft.getInstance().screen == null ? Stream.empty() : Minecraft.getInstance().screen.children().stream();
    }

    @SuppressWarnings("unchecked")
    private static <T> CpList<T> list(String narration) {
        return (CpList<T>) widgets().filter(w -> w instanceof CpList<?> l && l.getMessage().getString().equals(narration))
                .findFirst().orElseThrow(() -> new IllegalStateException("No list " + narration));
    }

    private static CpEditBox field(String narration) {
        return (CpEditBox) widgets().filter(w -> w instanceof CpEditBox b && b.getMessage().getString().equals(narration))
                .findFirst().orElseThrow(() -> new IllegalStateException("No field " + narration));
    }

    private static void press(String label) {
        widgets().filter(w -> w instanceof CpButton b && b.getMessage().getString().equals(label))
                .map(CpButton.class::cast).findFirst()
                .orElseThrow(() -> new IllegalStateException("No button " + label)).onPress();
    }

    private static Object firstKey(CpList<?> list) {
        if (list.items().isEmpty()) throw new IllegalStateException("Empty list " + list.getMessage().getString());
        Object item = list.items().get(0);
        if (item instanceof com.arcadia.customperm.network.gui.PlayersData.Player p) return p.uuid();
        throw new IllegalStateException("No key for " + item);
    }

    /** Types like a keyboard, one character at a time, into a focused field. */
    private static void type(CpEditBox box, String text) {
        Minecraft.getInstance().screen.setFocused(box);
        box.setValue("");
        for (char c : text.toCharArray()) box.charTyped(c, 0);
    }

    /** S02 in use: every other second a few characters typed into the node field, the rest browsing it. */
    private static void useFor(int n) {
        CpEditBox box = field("Permission node");
        if (n % 2 == 0) {
            type(box, n % 4 == 0 ? "customperm.a" : "customperm.");
        } else {
            box.keyPressed(264, 0, 0);
        }
    }

    private static void scroll(CpList<?> list, int n) {
        double x = list.getX() + list.getWidth() / 2.0;
        double y = list.getY() + list.getHeight() / 2.0;
        list.mouseScrolled(x, y, 0, n % 10 < 5 ? -3 : 3);
    }

    private static void command(String command) {
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    // ------------------------------------------------------------------ files

    private static Path sparkDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("spark");
    }

    private static Set<Path> sparkFiles() {
        try (Stream<Path> files = Files.isDirectory(sparkDir()) ? Files.list(sparkDir()) : Stream.empty()) {
            return new HashSet<>(files.toList());
        } catch (IOException e) {
            return Set.of();
        }
    }

    private static Path newSparkFile(String extension) {
        return sparkFiles().stream().filter(p -> p.toString().endsWith(extension) && !sparkFilesBefore.contains(p))
                .findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ verdict

    private static void report() {
        List<String> lines = new ArrayList<>();
        lines.add("CustomPerm spark scenario, S01 to S03 of TEST_PROCEDURE_v2.0.0");
        lines.add("Files: " + sparkDir());
        FILES.forEach((name, file) -> lines.add("  " + name + " = " + (file == null ? "missing" : file.getFileName())));
        lines.add("");
        try {
            if (ONLY.contains("s01")) s01(lines);
            if (ONLY.contains("s02")) s02(lines);
            if (ONLY.contains("s03")) s03(lines);
        } catch (IOException | RuntimeException e) {
            FAILURES.add("verdict failed: " + e);
            LOGGER.error("[spark] verdict failed", e);
        }
        lines.add("");
        if (FAILURES.isEmpty()) {
            lines.add("Run: every step completed.");
        } else {
            lines.add("Run problems:");
            FAILURES.forEach(f -> lines.add("  " + f));
        }
        lines.add("");
        lines.add("spark chat:");
        CHAT.forEach(c -> lines.add("  " + c));
        Path out = Minecraft.getInstance().gameDirectory.toPath().resolve("spark-report.txt");
        try {
            Files.write(out, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[spark] could not write the report", e);
        }
        lines.forEach(l -> LOGGER.info("[spark] {}", l));
        Minecraft.getInstance().stop();
    }

    private static void s01(List<String> lines) throws IOException {
        SparkData.Heap before = SparkData.heap(require("s01.before"));
        SparkData.Heap after = SparkData.heap(require("s01.after"));
        lines.add("S01 Open and close the interface " + GUI_CYCLES + " times");
        Predicate<String> screens = t -> t.startsWith(CLIENT + ".gui.admin.") && t.endsWith("Screen") && !t.contains("$");
        List<String> alive = after.instances().keySet().stream().filter(screens).filter(t -> after.count(t) > 0).sorted()
                .map(t -> simple(t) + " " + after.count(t)).toList();
        check(lines, "no admin screen alive once closed", alive.isEmpty(), alive.isEmpty() ? "0 instances" : String.join(", ", alive));
        for (String type : List.of(CLIENT + ".gui.kit.CpList", CLIENT + ".gui.kit.CpEditBox")) {
            check(lines, simple(type) + " back to its first value", after.count(type) <= before.count(type),
                    before.count(type) + " -> " + after.count(type));
        }
        Predicate<String> completers = t -> t.startsWith(CLIENT + ".gui") && (t.contains("Completer") || t.contains("Completions"));
        check(lines, "completers back to their first value", after.count(completers) <= before.count(completers),
                before.count(completers) + " -> " + after.count(completers));
        String vocabulary = "com.arcadia.customperm.network.gui.GuiVocabulary";
        int entries = NUMBERS.getOrDefault("s01.vocabulary", -1);
        check(lines, "GuiVocabulary entries no more than connected admins (1)", entries >= 0 && entries <= 1,
                entries + " entries; instances in the JVM " + before.count(vocabulary) + " -> " + after.count(vocabulary)
                        + " (the empty default, the client's copy, and weak-map values purged on next access)");
        String admin = "com.arcadia.customperm.gametest.support.TestPlayer$1";
        check(lines, "disconnected admin's ServerPlayer collected", after.count(admin) == 0, after.count(admin) + " left");
        lines.add("");
    }

    private static void s02(List<String> lines) throws IOException {
        lines.add("S02 Frame time with the interface open (" + KNOWN_PLAYERS + " known players)");
        SparkData.Sampled idle = SparkData.profile(require("s02.idle")).thread("Render thread");
        SparkData.Sampled active = SparkData.profile(require("s02.active")).thread("Render thread");
        if (idle == null || active == null) throw new IllegalStateException("No Render thread in the S02 profiles");
        // The screen's own render entry holds the whole page, vanilla drawing included: every method below it is
        // judged on its own, summed over all its calls (every button's icon, every row).
        Predicate<SparkData.Frame> mod = f -> f.className().startsWith(CLIENT)
                && !(f.className().endsWith(".CpScreen") && f.method().startsWith("render"));
        Predicate<SparkData.Frame> match = f -> f.className().startsWith(CLIENT + ".gui.kit.Completer") && f.method().equals("match");
        List<Map.Entry<String, Double>> heaviest = active.byMethod(mod).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(5).toList();
        double top = heaviest.isEmpty() ? 0 : heaviest.get(0).getValue() / active.ms();
        // "A few percent": every drawString flushes on its own in vanilla, so the text of a whole page sums to about
        // 5-6% whatever the mod does; a method above 8% is doing something of its own (icons were 13% before batching).
        check(lines, "no CustomPerm client method above 8% of the render thread, in use", top < 0.08,
                heaviest.stream().map(e -> e.getKey() + " " + percent(e.getValue() / active.ms()))
                        .reduce((a, b) -> a + ", " + b).orElse("none"));
        lines.add("  info: whole page render (CpScreen.render, vanilla drawing included) "
                + percent(active.share(f -> f.className().endsWith(".CpScreen") && f.method().equals("render"))));
        check(lines, "Completer.match idle (nothing typed for 30 s), under 0.5% of frames", idle.share(match) < 0.005,
                percent(idle.share(match)) + ", in use " + percent(active.share(match)));
        lines.add("");
    }

    private static void s03(List<String> lines) throws IOException {
        lines.add("S03 Rate limit history under load (" + SparkLoad.HAMMER_PLAYERS + " players, 5 min, "
                + RELOADS + " reloads)");
        SparkData.Heap before = SparkData.heap(require("s03.before"));
        SparkData.Heap after = SparkData.heap(require("s03.after"));
        int history = NUMBERS.getOrDefault("s03.history", -1);
        int bound = SparkLoad.HAMMER_PLAYERS * SparkLoad.GRADE_LIMIT;
        int underLoad = NUMBERS.getOrDefault("s03.history.load", -1);
        int typed = NUMBERS.getOrDefault("s03.typed", 0);
        int refused = NUMBERS.getOrDefault("s03.refused", 0);
        check(lines, "the limit applied (" + GRADE_LIMIT_TEXT + " per player)", refused > 0 && typed - refused <= bound,
                typed + " typed, " + refused + " refused");
        check(lines, "history at most one timestamp per counted use (" + bound + ")", underLoad > 0 && underLoad <= bound,
                underLoad + " timestamps after the load");
        check(lines, "history kept across " + RELOADS + " reloads, not one per reload", history == underLoad,
                underLoad + " -> " + history);
        String config = "com.arcadia.customperm.config.RateLimitsConfig";
        check(lines, "RateLimitsConfig instances not growing with reloads", after.count(config) <= Math.max(1, before.count(config)),
                before.count(config) + " -> " + after.count(config));
        List<String> grown = after.instances().keySet().stream()
                .filter(t -> t.startsWith("com.arcadia.customperm") && !t.startsWith("com.arcadia.customperm.gametest"))
                // Every reload is itself an entry of the activity log, which keeps the latest entries by design.
                .filter(t -> !t.equals(LOG_ENTRY))
                .filter(t -> after.count(t) - before.count(t) >= RELOADS)
                .sorted().map(t -> simple(t) + " " + before.count(t) + " -> " + after.count(t)).toList();
        check(lines, "no CustomPerm class grown by " + RELOADS + "+ instances across the reloads", grown.isEmpty(),
                grown.isEmpty() ? "none" : String.join(", ", grown));
        int logCap = com.arcadia.customperm.log.ActivityLog.MEMORY_MAX * com.arcadia.customperm.log.LogKind.values().length;
        check(lines, "activity log entries within its cap (" + logCap + ")", after.count(LOG_ENTRY) <= logCap,
                before.count(LOG_ENTRY) + " -> " + after.count(LOG_ENTRY) + ", one per logged action");
        lines.add("  info: ArrayDeque " + before.count("java.util.ArrayDeque") + " -> " + after.count("java.util.ArrayDeque")
                + ", Long " + before.count("java.lang.Long") + " -> " + after.count("java.lang.Long"));
        SparkData.Profile profile = SparkData.profile(require("s03.profile"));
        SparkData.Sampled server = profile.thread("Server thread");
        if (server == null) throw new IllegalStateException("No Server thread in the S03 profile");
        long ticks = profile.ticks() > 0 ? profile.ticks() : Math.round(server.ms() / 50);
        Predicate<SparkData.Frame> limits = f -> f.className().startsWith("com.arcadia.customperm.command.RateLimit")
                || f.className().startsWith("com.arcadia.customperm.admin.ExpirySweeper");
        double perTick = server.ms(limits) / Math.max(1, ticks);
        check(lines, "RateLimits and ExpirySweeper under 1 ms per tick", perTick < 1.0,
                String.format(Locale.ROOT, "%.4f ms per tick over %d ticks", perTick, ticks));
        CHAT.stream().filter(c -> c.contains("MSPT") || c.matches(".*\\d+(\\.\\d+)?/\\d+(\\.\\d+)?/.*"))
                .forEach(c -> lines.add("  info: " + c.strip()));
        lines.add("");
    }

    private static Path require(String name) {
        Path file = FILES.get(name);
        if (file == null) throw new IllegalStateException("spark did not save " + name);
        return file;
    }

    private static void check(List<String> lines, String what, boolean pass, String evidence) {
        lines.add("  " + (pass ? "PASS " : "FAIL ") + what + ": " + evidence);
        if (!pass) FAILURES.add("expectation failed: " + what);
    }

    private static String simple(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.3f%%", share * 100);
    }
}
