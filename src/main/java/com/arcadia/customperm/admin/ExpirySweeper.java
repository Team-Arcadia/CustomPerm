/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.perm.Expiry;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Removes temporary entries once they have run out. The resolver already ignores them, so a permission is
 * right without this; what the sweep adds is a file that says what is true, and a command tree sent again
 * to the players concerned, whose client would otherwise keep offering a command that now refuses.
 *
 * <p>Once a second on the server thread, and only over the entries that carry an expiry: a server with no
 * temporary entry walks a handful of empty maps. Each removal is recorded in the activity log.
 */
public final class ExpirySweeper {

    private static final int EVERY_TICKS = 20;
    private static int ticks;

    private ExpirySweeper() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < EVERY_TICKS) return;
        ticks = 0;
        if (CustomPerm.configManager == null) return;
        sweep(event.getServer());
    }

    /** Removes what has expired and says what it removed; public so a test can sweep without waiting. */
    public static List<String> sweep(MinecraftServer server) {
        GradesConfig config = CustomPerm.configManager.getGrades();
        long now = Expiry.now();
        List<String> removed = new ArrayList<>();

        for (Map.Entry<String, GradesConfig.Grade> entry : config.grades.entrySet()) {
            GradesConfig.Grade grade = entry.getValue();
            expire(grade.permissionExpiries, grade.permissions, now,
                    node -> removed.add(entry.getKey() + " no longer grants " + node));
            expire(grade.deniedPermissionExpiries, grade.deniedPermissions, now,
                    node -> removed.add(entry.getKey() + " no longer denies " + node));
            expire(grade.parentExpiries, grade.parents, now,
                    parent -> removed.add(entry.getKey() + " no longer inherits " + parent));
            expire(grade.deniedParentExpiries, grade.deniedParents, now,
                    parent -> removed.add(entry.getKey() + " no longer refuses " + parent));
        }
        expireUsers(server, config.userPermissionExpiries, config.userPermissions, now, removed,
                (who, node) -> who + " no longer has " + node);
        expireUsers(server, config.userDeniedPermissionExpiries, config.userDeniedPermissions, now, removed,
                (who, node) -> node + " is no longer denied to " + who);
        expireUsers(server, config.userGradeExpiries, config.userGrades, now, removed,
                (who, grade) -> who + " no longer holds " + grade);
        expireUsers(server, config.userDeniedGradeExpiries, config.userDeniedGrades, now, removed,
                (who, grade) -> who + " no longer refuses " + grade);
        if (removed.isEmpty()) return removed;

        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        for (String line : removed) {
            CustomPerm.LOGGER.info("[CustomPerm] Expired: {}.", line);
            ActivityLog.admin("CustomPerm", "", LogEntry.SOURCE_EXPIRY, line, true, "expired");
        }
        if (warning != null) CustomPerm.LOGGER.warn("[CustomPerm] {}", warning);
        return removed;
    }

    private static void expire(Map<String, Long> expiries, Collection<String> entries, long now,
                               java.util.function.Consumer<String> onRemoved) {
        if (expiries.isEmpty()) return;
        Iterator<Map.Entry<String, Long>> iterator = expiries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> expiry = iterator.next();
            if (expiry.getValue() > now) continue;
            iterator.remove();
            if (entries.remove(expiry.getKey())) onRemoved.accept(expiry.getKey());
        }
    }

    private static <C extends Collection<String>> void expireUsers(MinecraftServer server,
                                                                   Map<String, Map<String, Long>> expiries,
                                                                   Map<String, C> entries, long now,
                                                                   List<String> removed,
                                                                   java.util.function.BiFunction<String, String, String> line) {
        if (expiries.isEmpty()) return;
        Iterator<Map.Entry<String, Map<String, Long>>> users = expiries.entrySet().iterator();
        while (users.hasNext()) {
            Map.Entry<String, Map<String, Long>> user = users.next();
            C held = entries.get(user.getKey());
            // The name is looked up only for an entry that did expire, not every second for every holder.
            expire(user.getValue(), held == null ? java.util.Collections.emptyList() : held, now,
                    key -> removed.add(line.apply(name(server, user.getKey()), key)));
            if (user.getValue().isEmpty()) users.remove();
            if (held != null && held.isEmpty()) entries.remove(user.getKey());
        }
    }

    /** The name the log shows. */
    private static String name(MinecraftServer server, String uuid) {
        try {
            return server == null ? uuid : GradeAdmin.displayName(server, UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return uuid;
        }
    }
}
