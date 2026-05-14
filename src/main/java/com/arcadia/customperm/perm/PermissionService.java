package com.arcadia.customperm.perm;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.ConfigSnapshot;
import net.minecraft.commands.CommandSourceStack;

public interface PermissionService {
    /** Returns true if the source has the given permission node. Console / non-player sources return false here — vanilla op-level checks already cover them. */
    boolean hasPermission(CommandSourceStack source, String node);

    /**
     * Appelée après chaque hot-reload réussi pour notifier le service du nouveau snapshot.
     * Implémentation par défaut : no-op — les implémentations lisant dynamiquement
     * depuis ConfigManager (InternalPermService) ou gérant leurs propres données (LuckPermsService)
     * n'ont rien à faire. Contrat existant pour les implémentations futures (É4.x).
     */
    default void onConfigReload(ConfigSnapshot snapshot) {}

    static PermissionService get() {
        return CustomPerm.permissions;
    }
}
