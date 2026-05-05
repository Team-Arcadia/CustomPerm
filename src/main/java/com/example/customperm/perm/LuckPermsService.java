package com.example.customperm.perm;

import com.example.customperm.CustomPerm;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class LuckPermsService implements PermissionService {

    private boolean hooksReady = false;

    @Override
    public boolean hasPermission(CommandSourceStack source, String node) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUUID());
            if (user == null) return false;
            return user.getCachedData().getPermissionData().checkPermission(node).asBoolean();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms permission check failed for {}: {}", node, t.toString());
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
        if (hooksReady) return;
        try {
            LuckPerms api = LuckPermsProvider.get();
            api.getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
                UUID uuid = event.getUser().getUniqueId();
                // LP events fire off-thread; schedule the resync on the server thread.
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        CustomPerm.LOGGER.info("[CustomPerm] LP user data recalculated for {}, resending command tree.", player.getGameProfile().getName());
                        server.getCommands().sendCommands(player);
                    }
                });
            });
            hooksReady = true;
            CustomPerm.LOGGER.info("[CustomPerm] Subscribed to LuckPerms UserDataRecalculateEvent for live command tree resync.");
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not subscribe LP events: {}", t.toString());
        }
    }
}
