/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.gametest.support;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.GradesConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Grants permission nodes to a {@link TestPlayer} through whichever backend is active, so one test body
 * runs unchanged in the internal and the LuckPerms GameTest modes. {@link #close()} takes everything back.
 *
 * <p>Internal backend: a dedicated grade holding the nodes, assigned to the player. LuckPerms backend:
 * the nodes are set on the LuckPerms user directly, like {@code /lp user <name> permission set}.</p>
 */
public final class Grants implements AutoCloseable {

    private final UUID player;
    private final List<String> nodes;
    private final String gradeName;
    private final boolean deny;

    private Grants(UUID player, List<String> nodes, boolean deny) {
        this.player = player;
        this.nodes = nodes;
        this.deny = deny;
        this.gradeName = "gt_" + player.toString().substring(0, 8);
    }

    /** ALLOW nodes. */
    public static Grants allow(TestPlayer player, String... nodes) {
        return allow(player.uuid(), nodes);
    }

    /** ALLOW nodes for a player who has not joined yet, so they hold them from their first login. */
    public static Grants allow(UUID player, String... nodes) {
        Grants grants = new Grants(player, List.of(nodes), false);
        if (CustomPerm.isLuckPermsActive()) {
            LuckPermsTestSupport.setNodes(player, grants.nodes, true);
        } else {
            grants.grade().permissions.addAll(grants.nodes);
            grants.assign();
        }
        return grants;
    }

    /** DENY nodes: {@code deniedPermissions} internally, nodes set to false under LuckPerms. */
    public static Grants deny(TestPlayer player, String... nodes) {
        Grants grants = new Grants(player.uuid(), List.of(nodes), true);
        if (CustomPerm.isLuckPermsActive()) {
            LuckPermsTestSupport.setNodes(player.uuid(), grants.nodes, false);
        } else {
            grants.grade().deniedPermissions.addAll(grants.nodes);
            grants.assign();
        }
        return grants;
    }

    private GradesConfig.Grade grade() {
        return CustomPerm.configManager.getGrades().grades.computeIfAbsent(gradeName, name -> {
            GradesConfig.Grade grade = new GradesConfig.Grade();
            grade.name = name;
            grade.permissions = new HashSet<>();
            grade.deniedPermissions = new HashSet<>();
            return grade;
        });
    }

    private void assign() {
        List<String> assigned = CustomPerm.configManager.getGrades().userGrades
                .computeIfAbsent(player.toString(), key -> new ArrayList<>());
        if (!assigned.contains(gradeName)) assigned.add(gradeName);
    }

    /** Takes back these nodes only: several Grants on one player share a grade, and each closes on its own. */
    @Override
    public void close() {
        if (CustomPerm.isLuckPermsActive()) {
            LuckPermsTestSupport.clearNodes(player, nodes);
            return;
        }
        GradesConfig grades = CustomPerm.configManager.getGrades();
        GradesConfig.Grade grade = grades.grades.get(gradeName);
        if (grade != null) {
            (deny ? grade.deniedPermissions : grade.permissions).removeAll(nodes);
            if (!grade.permissions.isEmpty() || !grade.deniedPermissions.isEmpty()) return;
        }
        grades.grades.remove(gradeName);
        List<String> assigned = grades.userGrades.get(player.toString());
        if (assigned != null) {
            assigned.remove(gradeName);
            if (assigned.isEmpty()) grades.userGrades.remove(player.toString());
        }
    }
}
