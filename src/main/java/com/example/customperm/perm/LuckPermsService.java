package com.example.customperm.perm;

import com.example.customperm.CustomPerm;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class LuckPermsService implements PermissionService {

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
}
