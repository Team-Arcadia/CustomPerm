/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.packsmoke;

import com.arcadia.customperm.client.gui.admin.AdminScreen;
import com.arcadia.customperm.gametest.client.StepQueue;
import com.arcadia.customperm.network.gui.GuiPage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.arcadia.customperm.gametest.client.Drive.*;
import static com.arcadia.customperm.gametest.client.StepQueue.SECOND;

/**
 * CustomPerm's admin interface inside the Arcadia client pack, on the pack's server where LuckPerms runs: the
 * interface opens through the real command, and every page and view draws and keeps its layout at four window sizes
 * among about 450 other mods (Sodium and Iris replace the render pipeline). The script prepares {@code weather}
 * exposed and limited and the alias {@code pack_alias}, and grants this player once joined.
 * Verdict in {@code run/arcadia/client/smoke-report.txt}.
 */
@EventBusSubscriber(modid = "customperm_packsmoke", value = Dist.CLIENT)
public final class PackSmokeClient {
    static final boolean ACTIVE = Boolean.getBoolean("customperm.packSmoke");
    private static final int GRANT_TIMEOUT = 10 * 60 * SECOND;
    private static final StepQueue RUN = new StepQueue("pack-smoke");
    private static int connectedTicks;

    private PackSmokeClient() {
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

    private static Path report() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("smoke-report.txt");
    }

    private static void plan() {
        RUN.info("client mods loaded: " + ModList.get().size() + ", administration reached after " + connectedTicks / SECOND + " s");
        RUN.add("V02 gui command", 2 * SECOND, () -> Minecraft.getInstance().player.connection.sendCommand("customperm gui"));
        RUN.await("V02", "/customperm gui opens the interface among the pack's mods", 0,
                () -> screen() instanceof AdminScreen, () -> "screen " + screen());
        drawsEverywhere(RUN, views());
        RUN.add("close", SECOND, () -> Minecraft.getInstance().setScreen(null));
        RUN.add("report", SECOND, () -> RUN.finish(report()));
    }

    private static List<View> views() {
        List<View> views = new ArrayList<>();
        views.add(new View("pack-dashboard", GuiPage.DASHBOARD, () -> { }));
        views.add(new View("pack-commands", GuiPage.COMMANDS, () -> { }));
        views.add(new View("pack-commands-who", GuiPage.COMMANDS, () -> {
            press("Exposed");
            list("Commands").selectByKey("weather");
            rebuild();
            if (optionalList("Who decides") == null && !Boolean.TRUE.equals(screenField("whoView"))) press("Who");
        }));
        views.add(new View("pack-aliases", GuiPage.ALIASES, () -> list("Aliases").selectByKey("pack_alias")));
        views.add(new View("pack-ratelimits", GuiPage.RATE_LIMITS, () -> list("Rate limits").selectByKey("weather")));
        views.add(new View("pack-ratelimits-levels", GuiPage.RATE_LIMITS, () -> {
            list("Rate limits").selectByKey("weather");
            rebuild();
            if (!Boolean.TRUE.equals(screenField("levelsView"))) press("Levels");
        }));
        for (String tab : List.of("NODES", "PARENTS", "PLAYERS", "CHAT", "META")) {
            views.add(new View("pack-grades-" + tab.toLowerCase(), GuiPage.GRADES, () -> setTab(tab)));
        }
        for (String tab : List.of("NODES", "CHAT", "META", "TRACKS")) {
            views.add(new View("pack-players-" + tab.toLowerCase(), GuiPage.PLAYERS, () -> {
                list("Players").selectByKey(Minecraft.getInstance().player.getUUID().toString());
                setTab(tab);
            }));
        }
        views.add(new View("pack-luckperms", GuiPage.LUCKPERMS, () -> { }));
        views.add(new View("pack-import", GuiPage.IMPORT, () -> { }));
        views.add(new View("pack-logs", GuiPage.LOGS, () -> { }));
        views.add(new View("pack-help", GuiPage.HELP, () -> { }));
        return views;
    }
}
