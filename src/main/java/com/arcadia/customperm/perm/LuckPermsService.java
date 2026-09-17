/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.CustomPerm;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuckPermsService implements PermissionService {

    /** Fallback vers InternalPermService si LP devient défaillant (INVARIANT-301). */
    private final InternalPermService fallback;

    /**
     * Flag de dégradation permanent. AtomicBoolean garantit que la bascule (false→true)
     * n'a lieu qu'une seule fois même sous concurrence — et que le WARN AC2 n'est logué
     * qu'une seule fois (compareAndSet gate).
     */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    /**
     * Abonnement LP actif — conservé pour pouvoir le fermer proprement à l'arrêt du serveur.
     * Sans close(), l'abonnement survit au cycle de vie du serveur : la lambda garde une
     * référence vers l'ancien MinecraftServer (fuite mémoire) et, au démarrage suivant dans
     * la même JVM, le resync pointe vers un serveur mort.
     */
    private final Object hooksLock = new Object();
    private volatile ServerHooks serverHooks;

    public LuckPermsService(InternalPermService fallback) {
        // P7 : fail-fast si fallback null — NPE tardif lors d'une vraie défaillance LP serait bien pire.
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    /**
     * Retourne true si le backend LP est tombé en fallback permanent vers InternalPermService.
     * Exposé pour la commande /customperm status (É5).
     */
    public boolean isDegraded() {
        return degraded.get();
    }

    @Override
    public boolean hasPermission(CommandSourceStack source, String node) {
        return resolve(source, node, false);
    }

    /** LuckPerms has no operator short-circuit; only the internal fallback path differs. */
    @Override
    public boolean hasGrantedNode(CommandSourceStack source, String node) {
        return resolve(source, node, true);
    }

    private boolean resolve(CommandSourceStack source, String node, boolean grantedOnly) {
        // AC3 : une fois dégradé, LP n'est plus consulté.
        if (degraded.get()) return handleUnavailable(source, node, grantedOnly);

        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUUID());
            if (user == null) return false;
            return user.getCachedData().getPermissionData().checkPermission(node).asBoolean();
        } catch (Throwable t) {
            // D1 : ne pas absorber les erreurs JVM fatales (OOM, SOE…) — LP n'en est pas responsable.
            if (t instanceof Error e) throw e;
            // AC1/D2 : bascule permanente sur toute exception LP (IllegalStateException incluse).
            markUnavailable("API error: " + t.getClass().getSimpleName(), t);
            // AC4/AC5 : politique de fallback appliquée immédiatement à cette requête aussi.
            return handleUnavailable(source, node, grantedOnly);
        }
    }

    /**
     * Switches permanently to the fallback policy. compareAndSet makes the log line and the admin
     * alert happen once, whichever path noticed first (AC2, P1).
     */
    private void markUnavailable(String reason, Throwable cause) {
        if (!degraded.compareAndSet(false, true)) return;
        // P3 : throwable attaché pour que la cause LP soit visible dans les logs.
        String mode = CustomPerm.configManager.getSettings().luckPermsFallbackMode;
        if (CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()) {
            CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms unavailable ({}) — switching permanently to internal backend (luckPermsFallbackMode=internal).", reason, cause);
        } else {
            CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms unavailable ({}) — failing closed (luckPermsFallbackMode={}).", reason, mode, cause);
        }
        CustomPerm.raiseLuckPermsUnavailable(reason);
    }

    private boolean handleUnavailable(CommandSourceStack source, String node, boolean grantedOnly) {
        if (CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()) {
            return safeCallFallback(source, node, grantedOnly);
        }
        return false;
    }

    /**
     * Délègue à InternalPermService avec protection contre les exceptions inattendues (P5).
     * InternalPermService est très stable, mais un guard explicite évite une remontée brute
     * vers le dispatcher de commandes en cas de corruption config.
     */
    private boolean safeCallFallback(CommandSourceStack source, String node, boolean grantedOnly) {
        try {
            return grantedOnly ? fallback.hasGrantedNode(source, node) : fallback.hasPermission(source, node);
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Fallback InternalPermService.hasPermission() failed for node {}", node, t);
            return false;
        }
    }

    /**
     * Subscribes to LuckPerms' UserDataRecalculateEvent so that whenever a player's
     * permissions change (via /lp, web editor, sync...), we resend their command tree.
     * Without this, granting a perm via /lp doesn't make the command appear client-side
     * until the player reconnects or the server reloads.
     */
    public void initServerHooks(MinecraftServer server) {
        synchronized (hooksLock) {
            if (serverHooks != null) return;
            LuckPerms api;
            try {
                api = LuckPermsProvider.get();
            } catch (Throwable t) {
                if (t instanceof Error e) throw e;
                // The mod is loaded but its API never came up by server start. LuckPerms does this on
                // purpose in singleplayer ("not supported on the client"); it also covers a failed
                // enable. Degrade now rather than at the first permission check, so the backend
                // label, the admin alert and the admin interface stop presenting LuckPerms as active.
                markUnavailable(net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                        ? "LuckPerms does not run in singleplayer"
                        : "LuckPerms API not loaded at server start", t);
                return;
            }
            try {
                ResyncCoordinator coordinator = new ResyncCoordinator();
                EventSubscription<UserDataRecalculateEvent> subscription =
                    api.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
                        UUID uuid = event.getUser().getUniqueId();
                        if (!coordinator.schedule(uuid)) return;
                        // LP events fire off-thread; schedule the resync on the server thread.
                        server.execute(() -> {
                            if (!coordinator.complete(uuid)) return;
                            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                            if (player != null) {
                                CustomPerm.LOGGER.debug("[CustomPerm] LP user data recalculated for {}, resending command tree.", player.getGameProfile().getName());
                                server.getCommands().sendCommands(player);
                            }
                        });
                    });
                serverHooks = new ServerHooks(subscription, coordinator);
                CustomPerm.LOGGER.info("[CustomPerm] Subscribed to LuckPerms UserDataRecalculateEvent for live command tree resync.");
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Could not subscribe LP events; permission checks may still work but live command tree resync is disabled.", t);
            }
        }
    }

    /**
     * Ferme l'abonnement LP au moment où le serveur s'arrête, pour que :
     * 1) la lambda ne retienne pas l'ancien MinecraftServer (fuite mémoire),
     * 2) un redémarrage de serveur dans la même JVM ré-abonne avec le bon serveur.
     */
    public void closeServerHooks() {
        ServerHooks hooks;
        synchronized (hooksLock) {
            hooks = serverHooks;
            serverHooks = null;
            if (hooks != null) {
                hooks.coordinator().close();
            }
        }

        if (hooks != null) {
            try {
                hooks.subscription().close();
                CustomPerm.LOGGER.info("[CustomPerm] Unsubscribed from LuckPerms UserDataRecalculateEvent (server stopped).");
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Failed to close LuckPerms event subscription.", t);
            }
        }
    }

    private record ServerHooks(
            EventSubscription<UserDataRecalculateEvent> subscription,
            ResyncCoordinator coordinator) {
    }
}
