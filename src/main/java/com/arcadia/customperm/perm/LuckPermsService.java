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
import net.luckperms.api.actionlog.Action;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.log.LogPublishEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuckPermsService implements PermissionService {

    /** Fallback to InternalPermService once LuckPerms fails (INVARIANT-301). */
    private final InternalPermService fallback;

    /**
     * Permanent degradation flag. An AtomicBoolean makes the switch (false to true) happen once
     * under concurrency, and the AC2 WARN be logged once, both gated by compareAndSet.
     */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    /**
     * The live LuckPerms subscription, kept so it can be closed cleanly on server stop.
     * Without close(), it outlives the server: the lambda holds the old MinecraftServer (a
     * memory leak) and, on the next start in the same JVM, the resync points at a dead server.
     */
    private final Object hooksLock = new Object();
    private volatile ServerHooks serverHooks;
    /** Copies LuckPerms' own action log (/lp, web editor) into CustomPerm's activity log. */
    private volatile EventSubscription<LogPublishEvent> logSubscription;

    public LuckPermsService(InternalPermService fallback) {
        // P7: fail fast on a null fallback. A late NPE during a real LuckPerms failure is worse.
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    /**
     * True once the LuckPerms backend has fallen back permanently to InternalPermService.
     * Read by /customperm status.
     */
    public boolean isDegraded() {
        return degraded.get();
    }

    /**
     * The player's LuckPerms value for {@code node}: {@code true} is ALLOW, {@code false} is DENY, undefined
     * is UNSET. A {@code false} therefore applies to operators too, as it does for LuckPerms' own checks.
     */
    @Override
    public Tristate check(CommandSourceStack source, String node) {
        // AC3: once degraded, LuckPerms is never asked again.
        if (degraded.get()) return handleUnavailable(source, node);

        if (!(source.getEntity() instanceof ServerPlayer player)) return Tristate.UNSET;
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUUID());
            if (user == null) return Tristate.UNSET;
            return switch (user.getCachedData().getPermissionData().checkPermission(node)) {
                case TRUE -> Tristate.ALLOW;
                case FALSE -> Tristate.DENY;
                case UNDEFINED -> Tristate.UNSET;
            };
        } catch (Throwable t) {
            // D1: fatal JVM errors (OOM, SOE) are not LuckPerms' fault and must not be swallowed.
            if (t instanceof Error e) throw e;
            // AC1/D2: any LuckPerms exception switches permanently, IllegalStateException included.
            markUnavailable("API error: " + t.getClass().getSimpleName(), t);
            // AC4/AC5: the fallback policy applies to this very request too.
            return handleUnavailable(source, node);
        }
    }

    /**
     * LuckPerms' own prefix and suffix for the player, from its cached meta: the ones its meta stacking
     * picks, inheritance and priorities included. LuckPerms stores them, and nothing on NeoForge shows
     * them without a mod like this one. Once degraded, the fallback policy applies as for a permission.
     */
    @Override
    public ChatMeta chatMeta(ServerPlayer player) {
        if (degraded.get()) {
            return CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()
                    ? fallback.chatMeta(player) : ChatMeta.NONE;
        }
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
            if (user == null) return ChatMeta.NONE;
            var meta = user.getCachedData().getMetaData();
            return new ChatMeta(meta.getPrefix(), meta.getSuffix());
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            // A name is not worth degrading the permission backend for: the next permission check decides.
            return ChatMeta.NONE;
        }
    }

    /** LuckPerms' own meta value for the player, inheritance, weights and contexts resolved by LuckPerms. */
    @Override
    public String meta(ServerPlayer player, String key) {
        if (degraded.get()) {
            return CustomPerm.configManager.getSettings().useInternalLuckPermsFallback() ? fallback.meta(player, key) : null;
        }
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
            return user == null ? null : user.getCachedData().getMetaData().getMetaValue(key);
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            // The rule's own limit applies meanwhile; the next permission check decides whether to degrade.
            return null;
        }
    }

    /**
     * Switches permanently to the fallback policy. compareAndSet makes the log line and the admin
     * alert happen once, whichever path noticed first (AC2, P1).
     */
    private void markUnavailable(String reason, Throwable cause) {
        if (!degraded.compareAndSet(false, true)) return;
        // P3: the throwable is attached so the LuckPerms cause shows in the logs.
        String mode = CustomPerm.configManager.getSettings().luckPermsFallbackMode;
        if (CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()) {
            CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms unavailable ({}) — switching permanently to internal backend (luckPermsFallbackMode=internal).", reason, cause);
        } else {
            CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms unavailable ({}) — failing closed (luckPermsFallbackMode={}).", reason, mode, cause);
        }
        CustomPerm.raiseLuckPermsUnavailable(reason);
    }

    /** Internal fallback: the grades decide. Deny mode: nothing is granted, only vanilla levels open commands. */
    private Tristate handleUnavailable(CommandSourceStack source, String node) {
        if (CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()) {
            return safeCallFallback(source, node);
        }
        return Tristate.UNSET;
    }

    /**
     * Delegates to InternalPermService, guarded against unexpected exceptions (P5): a corrupted config
     * must not surface raw into the command dispatcher.
     */
    private Tristate safeCallFallback(CommandSourceStack source, String node) {
        try {
            return fallback.check(source, node);
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            CustomPerm.LOGGER.warn("[CustomPerm] Fallback InternalPermService.check() failed for node {}", node, t);
            return Tristate.UNSET;
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
                                // Their prefix may have changed with the rest.
                                com.arcadia.customperm.chat.NameDecoration.refresh(player);
                            }
                        });
                    });
                serverHooks = new ServerHooks(subscription, coordinator);
                CustomPerm.LOGGER.info("[CustomPerm] Subscribed to LuckPerms UserDataRecalculateEvent for live command tree resync.");
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Could not subscribe LP events; permission checks may still work but live command tree resync is disabled.", t);
            }
            try {
                logSubscription = api.getEventBus().subscribe(LogPublishEvent.class, event -> {
                    Action action = event.getEntry();
                    Action.Target target = action.getTarget();
                    com.arcadia.customperm.log.ActivityLog.luckPerms(action.getTimestamp().toEpochMilli(),
                            action.getSource().getName(), action.getSource().getUniqueId().toString(),
                            target.getName() + " " + action.getDescription(),
                            target.getType().name().toLowerCase(java.util.Locale.ROOT) + " " + target.getName());
                });
            } catch (Throwable t) {
                if (t instanceof Error e) throw e;
                CustomPerm.LOGGER.warn("[CustomPerm] Could not subscribe to the LuckPerms action log; /lp changes will not appear in the activity log.", t);
            }
        }
    }

    /**
     * Closes the LuckPerms subscription when the server stops, so that:
     * 1) the lambda stops holding the old MinecraftServer (a memory leak),
     * 2) a restart in the same JVM subscribes again against the right server.
     */
    public void closeServerHooks() {
        ServerHooks hooks;
        EventSubscription<LogPublishEvent> log;
        synchronized (hooksLock) {
            log = logSubscription;
            logSubscription = null;
            hooks = serverHooks;
            serverHooks = null;
            if (hooks != null) {
                hooks.coordinator().close();
            }
        }

        if (log != null) {
            try {
                log.close();
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Failed to close the LuckPerms action log subscription.", t);
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
