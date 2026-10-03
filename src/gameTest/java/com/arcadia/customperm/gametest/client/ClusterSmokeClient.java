/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import com.arcadia.customperm.client.gui.kit.CpButton;
import com.arcadia.customperm.client.gui.kit.Icon;
import com.arcadia.customperm.network.gui.GuiPage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.arcadia.customperm.gametest.client.Drive.*;
import static com.arcadia.customperm.gametest.client.StepQueue.SECOND;

/**
 * The admin client of {@code tools/cluster_smoke.py}, on the member {@code alpha} of a two-member cluster. V06: the
 * members of an element are one toggle each, this server marked with a house, a name no member answers to marked with
 * a warning, and a change reaches the other member within seconds. V07: a node server by server, each click moving a
 * member from framed to allowed to denied and back, as {@code grade list} says. The cluster views are also judged at
 * every window size, the narrow row being where an icon-only button once crashed.
 *
 * <p>The script prepares the elements ({@code tp} exposed, the alias {@code cs_alias}, a limit on {@code tp}, the
 * grade {@code cs_vip} with {@code customperm.cs.node}) and answers {@link Bridge} questions: {@code rcon <a|b> <command>}
 * and {@code read <a|b> <path>}.</p>
 */
@EventBusSubscriber(modid = "customperm", value = Dist.CLIENT)
public final class ClusterSmokeClient {
    static final boolean ACTIVE = Boolean.getBoolean("customperm.clusterSmoke.client");
    private static final int GRANT_TIMEOUT = 5 * 60 * SECOND;
    private static final String HERE = "alpha";
    private static final String OTHER = "beta";
    private static final String GRADE = "cs_vip";
    private static final String NODE = "customperm.cs.node";

    private static final StepQueue RUN = new StepQueue("cluster-smoke");
    private static Bridge bridge;
    private static int connectedTicks;

    private ClusterSmokeClient() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ACTIVE || RUN.finished()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (!RUN.started()) {
            if (mc.getConnection().getCommands().getRoot().getChild("customperm") != null) {
                bridge = new Bridge(mc.gameDirectory.toPath());
                plan();
                RUN.start();
            } else if (++connectedTicks > GRANT_TIMEOUT) {
                RUN.fail("run", "administration granted", "/customperm never reached this client's command tree");
                RUN.finish(report());
            }
            return;
        }
        RUN.tick();
    }

    private static Path report() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("smoke-report.txt");
    }

    private static void plan() {
        members();
        nodeByServer();
        drawsEverywhere(RUN, List.of(COMMAND_SERVERS, ALIAS_SERVERS, LIMIT_SERVERS, GRADE_NODE_SERVERS));
        RUN.add("close", SECOND, () -> Minecraft.getInstance().setScreen(null));
        RUN.add("report", SECOND, () -> RUN.finish(report()));
    }

    private static final View COMMAND_SERVERS = new View("commands-servers", GuiPage.COMMANDS, () -> {
        press("Exposed");
        list("Commands").selectByKey("tp");
        openView("serversView");
    });
    private static final View ALIAS_SERVERS = new View("aliases-servers", GuiPage.ALIASES, () -> {
        list("Aliases").selectByKey("cs_alias");
        rebuild();
        setTab("SERVERS");
    });
    private static final View LIMIT_SERVERS = new View("ratelimits-servers", GuiPage.RATE_LIMITS, () -> {
        list("Rate limits").selectByKey("tp");
        rebuild();
        openView("serversView");
    });
    private static final View GRADE_NODE_SERVERS = new View("grades-node-servers", GuiPage.GRADES, () -> {
        list("Grades").selectByKey(GRADE);
        setTab("NODES");
        list("Nodes").selectByKey("allow:" + NODE + "@");
        rebuild();
        openView("nodeServersView");
    });

    /** A page asked for again refreshes the same screen, which keeps its view: "Servers" toggles, so only while closed. */
    private static void openView(String flag) {
        if (!Boolean.TRUE.equals(screenField(flag))) press("Servers");
    }

    // ------------------------------------------------------------------ V06

    private static void members() {
        open(RUN, COMMAND_SERVERS);
        RUN.add("V06 toggles", 2, () -> RUN.check("V06", "one toggle per member heard, this server marked with a house", () -> {
            CpButton here = button(HERE);
            if (button("All") == null || here == null || button(OTHER) == null) throw new AssertionError("buttons " + labels());
            if (icon(here) != Icon.HOME) throw new AssertionError(HERE + " shows " + icon(here));
            return labels();
        }));
        long[] clicked = {0};
        RUN.add("V06 pick the other member", 2, () -> {
            clicked[0] = System.currentTimeMillis();
            press(OTHER);
        });
        ask("V06", "the other member's commands.json names it", "read b config/arcadia/customperm/commands.json",
                answer -> answer.contains("\"" + OTHER + "\""), clicked);
        // The page has two "All": the filter of the command list first, the member toggle last.
        RUN.add("V06 all", SECOND, () -> {
            List<CpButton> all = widgets().filter(w -> w instanceof CpButton b && b.getMessage().getString().equals("All"))
                    .map(CpButton.class::cast).toList();
            all.get(all.size() - 1).onPress();
        });
        ask("V06", "All clears the list on the other member too", "read b config/arcadia/customperm/commands.json",
                answer -> !answer.contains("\"" + OTHER + "\""), null);
        ask("V06", "a name no member answers to is accepted by command", "rcon a customperm command servers tp alpha ghost",
                answer -> answer.contains("ghost"), null);
        open(RUN, COMMAND_SERVERS);
        RUN.add("V06 ghost", 2, () -> RUN.check("V06", "the unknown name shows with a warning mark", () -> {
            CpButton ghost = button("ghost");
            if (ghost == null || icon(ghost) != Icon.WARN) throw new AssertionError("buttons " + labels());
            return labels();
        }));
        ask("V06", "the list is cleared for the next views", "rcon a customperm command servers tp all", answer -> true, null);
    }

    // ------------------------------------------------------------------ V07

    private static void nodeByServer() {
        open(RUN, GRADE_NODE_SERVERS);
        Icon[] cycle = {Icon.CHECK, Icon.CROSS, null};
        String[] words = {"allow", "deny", "inherit"};
        for (int i = 0; i < 3; i++) {
            int n = i;
            RUN.add("V07 click " + (n + 1), 2, () -> press(OTHER));
            RUN.await("V07", "click " + (n + 1) + " on " + OTHER + " shows it " + words[n], 0,
                    () -> button(OTHER) != null && icon(button(OTHER)) == cycle[n],
                    () -> OTHER + " shows " + (button(OTHER) == null ? "no button" : icon(button(OTHER))) + ", status \"" + status() + "\"");
            if (n < 2) {
                String entry = NODE + " (server=" + OTHER + ")";
                ask("V07", "grade list says the same after click " + (n + 1), "rcon a customperm grade list " + GRADE,
                        answer -> n == 0 ? sectionHas(answer, "allow", entry) : sectionHas(answer, "deny", entry), null);
            } else {
                ask("V07", "grade list has no " + OTHER + " entry after the third click", "rcon a customperm grade list " + GRADE,
                        answer -> !answer.contains(NODE + " (server=" + OTHER + ")"), null);
            }
        }
    }

    /** Whether the {@code allow} or {@code deny} line of a {@code grade list} answer carries the entry. */
    private static boolean sectionHas(String answer, String section, String entry) {
        return answer.lines().anyMatch(line -> line.trim().startsWith(section) && line.contains(entry));
    }

    // ------------------------------------------------------------------ helpers

    private static void ask(String id, String what, String question, java.util.function.Predicate<String> expected, long[] since) {
        // The answer may need a poll or two to show on the other member: ask again until it does.
        long[] asked = {0};
        RUN.add(id + " ask " + question, 2, () -> {
            asked[0] = System.currentTimeMillis();
            bridge.ask(question);
        });
        RUN.await(id, what, 0, () -> {
            if (!bridge.answered()) return false;
            if (expected.test(bridge.answer())) return true;
            if (System.currentTimeMillis() - asked[0] > 500) {
                asked[0] = System.currentTimeMillis();
                bridge.ask(question);
            }
            return false;
        }, () -> (since == null ? "" : "after " + (System.currentTimeMillis() - since[0]) + " ms, ")
                + "answer: " + oneLine(bridge.answer()));
    }

    /** The report is read line by line: an answer spanning lines is joined so none of it is lost. */
    private static String oneLine(String text) {
        String joined = String.join(" / ", text.lines().toList());
        return joined.substring(0, Math.min(400, joined.length()));
    }

    private static CpButton button(String label) {
        return widgets().filter(w -> w instanceof CpButton b && b.getMessage().getString().equals(label))
                .map(CpButton.class::cast).findFirst().orElse(null);
    }

    private static String labels() {
        return widgets().filter(w -> w instanceof CpButton).map(w -> {
            CpButton b = (CpButton) w;
            Icon icon = icon(b);
            return b.getMessage().getString() + (icon == null ? "" : "[" + icon + "]");
        }).collect(Collectors.joining(", "));
    }

    private static Icon icon(CpButton button) {
        try {
            Field field = CpButton.class.getDeclaredField("icon");
            field.setAccessible(true);
            return (Icon) field.get(button);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CpButton.icon moved; update ClusterSmokeClient", e);
        }
    }
}
