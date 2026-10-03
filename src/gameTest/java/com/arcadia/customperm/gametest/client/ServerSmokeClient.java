/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import com.arcadia.customperm.network.gui.GuiAction;
import com.arcadia.customperm.network.gui.GuiPage;
import com.arcadia.customperm.network.gui.ImportData;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.arcadia.customperm.gametest.client.Drive.*;
import static com.arcadia.customperm.gametest.client.StepQueue.SECOND;

/**
 * The admin client of {@code tools/server_smoke.py}: a real client on a dedicated server where LuckPerms runs, which
 * singleplayer cannot give. It draws the pages only LuckPerms opens (the LuckPerms editor, Import) at every window
 * size, and plays V09: read LuckPerms, narrow what an import carries from the page, and find the same selection in
 * {@code /customperm import select}; an unknown name is refused and changes nothing.
 *
 * <p>The script seeds the LuckPerms groups {@code smoke_a} and {@code smoke_b} and grants this player once joined;
 * the run starts when {@code /customperm} reaches its command tree. Verdict in {@code run/serversmoke/admin/smoke-report.txt}.</p>
 */
@EventBusSubscriber(modid = "customperm", value = Dist.CLIENT)
public final class ServerSmokeClient {
    static final boolean ACTIVE = "admin".equals(System.getProperty("customperm.serverSmoke"));
    private static final int GRANT_TIMEOUT = 5 * 60 * SECOND;

    private static final StepQueue RUN = new StepQueue("server-smoke");
    private static final List<String> CHAT = new CopyOnWriteArrayList<>();
    private static int connectedTicks;

    private ServerSmokeClient() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        if (ACTIVE) CHAT.add(event.getMessage().getString());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ACTIVE || RUN.finished()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (!RUN.started()) {
            if (mc.getConnection().getCommands().getRoot().getChild("customperm") != null) {
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

    private static java.nio.file.Path report() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("smoke-report.txt");
    }

    private static void plan() {
        RUN.info("administration reached the client after " + connectedTicks / SECOND + " s");
        List<View> views = new ArrayList<>();
        views.add(new View("lp-dashboard", GuiPage.DASHBOARD, () -> { }));
        views.add(new View("lp-commands", GuiPage.COMMANDS, () -> { }));
        views.add(new View("lp-grades", GuiPage.GRADES, () -> { }));
        views.add(new View("lp-players", GuiPage.PLAYERS, () -> { }));
        views.add(new View("lp-luckperms", GuiPage.LUCKPERMS, () -> { }));
        views.add(new View("lp-import", GuiPage.IMPORT, () -> { }));
        views.add(new View("lp-logs", GuiPage.LOGS, () -> { }));
        drawsEverywhere(RUN, views);
        importSelection();
        RUN.add("close", SECOND, () -> Minecraft.getInstance().setScreen(null));
        RUN.add("report", SECOND, () -> RUN.finish(report()));
    }

    // ------------------------------------------------------------------ V09

    private static void importSelection() {
        open(RUN, new View("import", GuiPage.IMPORT, () -> { }));
        RUN.add("V09 read", 2, () -> press("Read LuckPerms"));
        RUN.await("V09", "reading LuckPerms fills the report and the groups to choose from", 0,
                () -> data() != null && data().previewed() && groups().contains("smoke_a") && groups().contains("smoke_b"),
                ServerSmokeClient::describe);

        RUN.add("V09 narrow", 2, () -> {
            act(GuiAction.IMPORT_SELECT, "groups", "set", "smoke_a");
            act(GuiAction.IMPORT_SELECT, "kinds", "set", "nodes");
        });
        RUN.await("V09", "the page shows the narrowed selection and a report that counts it", 0,
                () -> taken().equals(List.of("smoke_a")) && data().choice().kinds().equals(List.of("nodes"))
                        && reportText().contains("1 group(s) become grades"),
                ServerSmokeClient::describe);
        command("V09", "customperm import select", "the command shows the page's selection",
                out -> out.contains("smoke_a") && !out.contains("smoke_b") && out.contains("nodes"));

        command("V09", "customperm import select groups set nosuch_group", "an unknown group is refused",
                out -> out.toLowerCase().contains("nosuch_group"));
        RUN.add("V09 unchanged", SECOND, () -> RUN.check("V09", "the refusal changed nothing on the page", () -> {
            if (!taken().equals(List.of("smoke_a"))) throw new AssertionError(describe());
            return "still " + taken();
        }));

        RUN.add("V09 page toggle", 2, () -> {
            press("Choose");
            act(GuiAction.IMPORT_SELECT, "groups", "add", "smoke_b");
        });
        RUN.await("V09", "adding a group from the page brings it into the report's count", 0,
                () -> taken().contains("smoke_b") && reportText().contains("2 group(s) become grades"), ServerSmokeClient::describe);
        command("V09", "customperm import select", "the command follows the page again",
                out -> out.contains("smoke_a") && out.contains("smoke_b"));

        RUN.add("V09 export", 2, () -> {
            press("To LuckPerms");
            press("Read the grades");
        });
        RUN.await("V09", "reading the grades fills the export report", 0,
                () -> data() != null && data().export().previewed() && !data().export().report().isEmpty(),
                () -> data() == null ? "no data" : "export " + data().export().report());
        command("V09", "customperm export select", "the export selection answers by command",
                out -> !out.isBlank());
    }

    /** Sends a command as this player and judges the reply it gets. */
    private static void command(String id, String command, String what, java.util.function.Predicate<String> expected) {
        int[] mark = {0};
        RUN.add(id + " /" + command, 2, () -> {
            mark[0] = CHAT.size();
            Minecraft.getInstance().player.connection.sendCommand(command);
        });
        RUN.await(id, what + " (/" + command + ")", SECOND, () -> expected.test(replies(mark[0])), () -> replies(mark[0]));
    }

    private static String replies(int from) {
        return String.join(" | ", CHAT.subList(Math.min(from, CHAT.size()), CHAT.size()));
    }

    private static ImportData data() {
        if (!(screen() instanceof com.arcadia.customperm.client.gui.admin.ImportScreen)) return null;
        return screenField("data");
    }

    private static List<String> groups() {
        ImportData data = data();
        return data == null ? List.of() : data.choice().groups().stream().map(ImportData.Item::key).toList();
    }

    private static List<String> taken() {
        ImportData data = data();
        return data == null ? List.of() : data.choice().groups().stream().filter(ImportData.Item::on).map(ImportData.Item::key).toList();
    }

    private static String reportText() {
        ImportData data = data();
        return data == null ? "" : String.join("\n", data.report());
    }

    private static String describe() {
        ImportData data = data();
        if (data == null) return "no Import page open but " + screen();
        return "previewed " + data.previewed() + ", groups " + data.choice().groups().stream()
                .map(i -> i.key() + (i.on() ? "+" : "-")).collect(Collectors.joining(",")) + ", kinds " + data.choice().kinds()
                + ", report " + String.join(" / ", data.report()) + ", status \"" + status() + "\"";
    }
}
