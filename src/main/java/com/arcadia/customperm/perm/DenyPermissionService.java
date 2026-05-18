package com.arcadia.customperm.perm;

import net.minecraft.commands.CommandSourceStack;

public class DenyPermissionService implements PermissionService {
    @Override
    public boolean hasPermission(CommandSourceStack source, String node) {
        return false;
    }
}
