/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A client run driven one tick at a time: each step waits its delay, then until it is ready, then acts. A step that
 * never becomes ready fails the run by name instead of hanging it. Checks are written as {@code PASS id what - detail}
 * or {@code FAIL ...} lines, the id being the step of the test procedure they stand for, and the report ends with
 * {@code RESULT PASS n checks} or {@code RESULT FAIL x of n}: the Gradle task and the scripts read that line, never the
 * exit code, which is 0 even when the game failed to start.
 */
public final class StepQueue {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SECOND = 20;
    private static final int STEP_TIMEOUT = 60 * SECOND;

    private record Step(String name, int delay, BooleanSupplier ready, Runnable action) {
    }

    private final String tag;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> lines = new ArrayList<>();
    private int wait;
    private int waited;
    private int checks;
    private int failures;
    private boolean started;
    private boolean finished;
    private CompletableFuture<?> serverWork = CompletableFuture.completedFuture(null);

    public StepQueue(String tag) {
        this.tag = tag;
    }

    public void add(String name, int delay, Runnable action) {
        steps.add(new Step(name, delay, () -> true, action));
    }

    public void add(String name, int delay, BooleanSupplier ready, Runnable action) {
        steps.add(new Step(name, delay, ready, action));
    }

    /** Waits until {@code ready}, then records a check that passes when it became ready in time. */
    public void await(String id, String what, int delay, BooleanSupplier ready, Supplier<String> detail) {
        int[] ticks = {0};
        int[] limit = {STEP_TIMEOUT};
        steps.add(new Step(id + " " + what, delay, () -> ready.getAsBoolean() || ++ticks[0] >= limit[0],
                () -> check(id, what, () -> {
                    if (!ready.getAsBoolean()) throw new AssertionError("not reached after " + ticks[0] / SECOND + " s: " + detail.get());
                    return detail.get();
                })));
    }

    /** Work on the integrated server's thread; the next step waits until it has finished. */
    public void server(String name, int delay, Consumer<MinecraftServer> work) {
        add(name, delay, () -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            serverWork = server.submit(() -> work.accept(server));
        });
        add(name + ".done", 0, () -> serverWork.isDone(), () -> serverWork.exceptionally(e -> {
            fail("run", "step " + name, e.toString());
            return null;
        }));
    }

    /** Runs {@code body} now; a thrown error or a failed assertion is a FAIL line, anything else a PASS. */
    public void check(String id, String what, Supplier<String> body) {
        checks++;
        try {
            String detail = body.get();
            line("PASS " + id + " " + what + (detail == null || detail.isEmpty() ? "" : " - " + detail));
        } catch (Throwable e) {
            failures++;
            line("FAIL " + id + " " + what + " - " + (e instanceof AssertionError ? e.getMessage() : e.toString()));
            if (!(e instanceof AssertionError)) LOGGER.error("[{}] {} {}", tag, id, what, e);
        }
    }

    public void fail(String id, String what, String why) {
        checks++;
        failures++;
        line("FAIL " + id + " " + what + " - " + why);
    }

    public void info(String text) {
        line("INFO " + text);
    }

    private void line(String text) {
        lines.add(text);
        LOGGER.info("[{}] {}", tag, text);
    }

    public boolean started() {
        return started;
    }

    public void start() {
        started = true;
        wait = steps.isEmpty() ? 0 : steps.peek().delay();
    }

    public boolean finished() {
        return finished;
    }

    /** One client tick. */
    public void tick() {
        if (!started || finished) return;
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = steps.peek();
        if (step == null) return;
        if (!step.ready().getAsBoolean()) {
            if (++waited < STEP_TIMEOUT) return;
            fail("run", "step " + step.name(), "never became ready");
        }
        waited = 0;
        steps.poll();
        try {
            step.action().run();
        } catch (RuntimeException e) {
            fail("run", "step " + step.name(), e.toString());
            LOGGER.error("[{}] step {} failed", tag, step.name(), e);
        }
        Step next = steps.peek();
        wait = next == null ? 0 : next.delay();
    }

    /** Writes the report and stops the game. */
    public void finish(Path report) {
        finished = true;
        lines.add(failures == 0 ? "RESULT PASS " + checks + " checks" : "RESULT FAIL " + failures + " of " + checks + " checks");
        try {
            Files.write(report, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[{}] could not write {}", tag, report, e);
        }
        LOGGER.info("[{}] {}", tag, lines.get(lines.size() - 1));
        Minecraft.getInstance().stop();
    }
}
