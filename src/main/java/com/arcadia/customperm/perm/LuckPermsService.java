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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private volatile EventSubscription<UserDataRecalculateEvent> recalcSubscription;

    /**
     * Coalescence des resyncs : UserDataRecalculateEvent peut être déclenché plusieurs fois
     * d'affilée par LP pour un même joueur (login, recalcul d'héritage de groupes, sync réseau).
     * Un seul ClientboundCommandsPacket est envoyé par rafale.
     */
    private final Set<UUID> pendingResyncs = ConcurrentHashMap.newKeySet();

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
        // AC3 : une fois dégradé, LP n'est plus consulté.
        if (degraded.get()) return handleUnavailable(source, node);

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
            // compareAndSet garantit que WARN + bascule n'ont lieu qu'une seule fois (AC2, P1).
            if (degraded.compareAndSet(false, true)) {
                // P3 : throwable attaché pour que la cause LP soit visible dans les logs.
                String mode = CustomPerm.configManager.getSettings().luckPermsFallbackMode;
                if (CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()) {
                    CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms unavailable — switching permanently to internal backend (luckPermsFallbackMode=internal).", t);
                } else {
                    CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms unavailable — failing closed (luckPermsFallbackMode={}).", mode, t);
                }
            }
            // AC4/AC5 : politique de fallback appliquée immédiatement à cette requête aussi.
            return handleUnavailable(source, node);
        }
    }

    private boolean handleUnavailable(CommandSourceStack source, String node) {
        if (CustomPerm.configManager.getSettings().useInternalLuckPermsFallback()) {
            return safeCallFallback(source, node);
        }
        return false;
    }

    /**
     * Délègue à InternalPermService avec protection contre les exceptions inattendues (P5).
     * InternalPermService est très stable, mais un guard explicite évite une remontée brute
     * vers le dispatcher de commandes en cas de corruption config.
     */
    private boolean safeCallFallback(CommandSourceStack source, String node) {
        try {
            return fallback.hasPermission(source, node);
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
        if (recalcSubscription != null) return;
        try {
            LuckPerms api = LuckPermsProvider.get();
            recalcSubscription = api.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
                UUID uuid = event.getUser().getUniqueId();
                // Coalesce: LP recalcule souvent plusieurs fois de suite pour le même joueur.
                if (!pendingResyncs.add(uuid)) return;
                // LP events fire off-thread; schedule the resync on the server thread.
                server.execute(() -> {
                    pendingResyncs.remove(uuid);
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        CustomPerm.LOGGER.debug("[CustomPerm] LP user data recalculated for {}, resending command tree.", player.getGameProfile().getName());
                        server.getCommands().sendCommands(player);
                    }
                });
            });
            CustomPerm.LOGGER.info("[CustomPerm] Subscribed to LuckPerms UserDataRecalculateEvent for live command tree resync.");
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not subscribe LP events; permission checks may still work but live command tree resync is disabled.", t);
        }
    }

    /**
     * Ferme l'abonnement LP au moment où le serveur s'arrête, pour que :
     * 1) la lambda ne retienne pas l'ancien MinecraftServer (fuite mémoire),
     * 2) un redémarrage de serveur dans la même JVM ré-abonne avec le bon serveur.
     */
    public void closeServerHooks() {
        EventSubscription<UserDataRecalculateEvent> sub = recalcSubscription;
        recalcSubscription = null;
        pendingResyncs.clear();
        if (sub != null) {
            try {
                sub.close();
                CustomPerm.LOGGER.info("[CustomPerm] Unsubscribed from LuckPerms UserDataRecalculateEvent (server stopped).");
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Failed to close LuckPerms event subscription.", t);
            }
        }
    }
}
