package com.example.customperm.perm;

import com.example.customperm.CustomPerm;
import net.minecraft.commands.CommandSourceStack;

public interface PermissionService {
    /** Returns true if the source has the given permission node. Console / non-player sources return false here — vanilla op-level checks already cover them. */
    boolean hasPermission(CommandSourceStack source, String node);

    static PermissionService get() {
        return CustomPerm.permissions;
    }
}
