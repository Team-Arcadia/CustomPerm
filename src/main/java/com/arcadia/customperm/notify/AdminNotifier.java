/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.notify;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.perm.AdminAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;

/**
 * Delivers {@link AdminAlerts} to the people who can act on them: every online player who may run
 * {@code /customperm} ({@link AdminAccess}).
 *
 * <p>A new or changed alert is broadcast once to the ops online at that moment; an op who joins
 * while an alert is active receives it on login. That is the whole anti-spam policy: no repeat
 * while the alert stays unchanged, one reminder per login.</p>
 *
 * <p>Alerts may be raised from any thread (LuckPerms failures surface wherever a permission is
 * checked) and before the server exists (backend selection runs in the mod constructor). Delivery
 * is therefore always handed to the server thread, and alerts raised before start simply wait for
 * the first op to join.</p>
 */
public final class AdminNotifier {

    private static final AdminAlerts ALERTS = new AdminAlerts();
    private static volatile MinecraftServer server;

    private AdminNotifier() {
    }

    public static void raise(AdminAlerts.Key key, String message) {
        if (!ALERTS.raise(key, message)) return;
        CustomPerm.LOGGER.warn("[CustomPerm] Admin alert {}: {}", key, message);
        broadcast(alertLine(message));
    }

    public static void clear(AdminAlerts.Key key, String resolvedMessage) {
        if (!ALERTS.clear(key)) return;
        CustomPerm.LOGGER.info("[CustomPerm] Admin alert {} resolved: {}", key, resolvedMessage);
        broadcast(Component.literal("[CustomPerm] Resolved: " + resolvedMessage).withStyle(ChatFormatting.GREEN));
    }

    public static boolean isActive(AdminAlerts.Key key) {
        return ALERTS.isActive(key);
    }

    public static Map<AdminAlerts.Key, String> activeAlerts() {
        return ALERTS.snapshot();
    }

    public static Component alertLine(String message) {
        return Component.literal("[CustomPerm] ALERT: " + message).withStyle(ChatFormatting.RED);
    }

    static boolean isAdmin(ServerPlayer player) {
        return AdminAccess.canAdminister(player);
    }

    private static void broadcast(Component line) {
        MinecraftServer current = server;
        if (current == null) return;
        current.execute(() -> current.getPlayerList().getPlayers().stream()
                .filter(AdminNotifier::isAdmin)
                .forEach(player -> player.sendSystemMessage(line)));
    }

    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isAdmin(player)) return;
        ALERTS.snapshot().values().forEach(message -> player.sendSystemMessage(alertLine(message)));
    }
}
