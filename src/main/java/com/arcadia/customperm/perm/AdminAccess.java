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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who administers CustomPerm: {@code /customperm}, the admin interface and admin alerts share this gate.
 *
 * <p>Op level 2 is required, and {@link PermissionNodes#ADMIN} can only take access away: an explicit
 * DENY, directly or through a denied {@code *} or {@code customperm.*}, locks an operator out, while
 * granting it to a non-operator opens nothing (audit retest R02). The console and other non-player
 * sources are never asked for a node, so the server can always be repaired from the console.</p>
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    /**
     * Checked on a source rebuilt from the player's real op level, never an elevated one: an alias runs
     * its steps at level 4 and must not open {@code /customperm} (INVARIANT-503).
     */
    public static boolean canAdminister(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack();
        return source.hasPermission(2) && explicit(source, PermissionNodes.ADMIN) != Tristate.DENY;
    }

    public static boolean canAdminister(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return canAdminister(player);
        return source.hasPermission(2);
    }

    /**
     * Explicit value of a node, never throwing into a command predicate or a packet handler: a failing
     * backend reads as DENY, so a broken permission store closes admin access instead of opening it.
     */
    public static Tristate explicit(CommandSourceStack source, String node) {
        try {
            return PermissionService.get().check(source, node);
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            CustomPerm.LOGGER.warn("[CustomPerm] Permission check for {} failed; treating it as denied.", node, t);
            return Tristate.DENY;
        }
    }
}
