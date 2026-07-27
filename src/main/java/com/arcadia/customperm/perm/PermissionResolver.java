/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.config.GradesConfig;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution multi-grade pur Java — zéro import Minecraft/NeoForge (AR8, AC6).
 *
 * <p>Règles de résolution (par priorité) :</p>
 * <ol>
 *   <li>Si {@code node} est null → false</li>
 *   <li>Si aucun grade assigné → false (FR2)</li>
 *   <li>DENY explicite dans n'importe quel grade → false (INVARIANT-101)</li>
 *   <li>ALLOW dans au moins un grade → true (INVARIANT-102)</li>
 *   <li>Sinon → false</li>
 * </ol>
 */
public final class PermissionResolver {

    private PermissionResolver() {}

    /**
     * Résout la permission {@code node} pour {@code uuid} dans le contexte de {@code grades}.
     */
    public static boolean resolve(GradesConfig grades, UUID uuid, String node) {
        if (node == null || uuid == null) return false;
        List<String> assigned = grades.userGrades.getOrDefault(uuid.toString(), List.of());
        if (assigned.isEmpty()) return false;

        boolean anyAllow = false;
        for (String gradeName : assigned) {
            GradesConfig.Grade g = grades.grades.get(gradeName);
            if (g == null) continue;
            // DENY > ALLOW — INVARIANT-101 : un DENY dans n'importe quel grade l'emporte
            if (isDenied(g, node)) return false;
            if (isAllowed(g, node)) anyAllow = true;
        }
        return anyAllow;
    }

    private static boolean isDenied(GradesConfig.Grade g, String node) {
        if (g.deniedPermissions == null || g.deniedPermissions.isEmpty()) return false;
        return matchesNode(g.deniedPermissions, node);
    }

    private static boolean isAllowed(GradesConfig.Grade g, String node) {
        if (g.permissions == null || g.permissions.isEmpty()) return false;
        return matchesNode(g.permissions, node);
    }

    /**
     * Vérifie si {@code node} est couvert par {@code perms}.
     * Supporte trois formes : exact, wildcard global {@code *}, wildcard préfixe {@code prefix.*}.
     * Package-private pour être directement testable depuis PermissionResolverTest.
     */
    static boolean matchesNode(Set<String> perms, String node) {
        if (perms.contains(node)) return true;
        if (perms.contains("*")) return true;
        int dot = node.lastIndexOf('.');
        return dot > 0 && perms.contains(node.substring(0, dot) + ".*");
    }
}
