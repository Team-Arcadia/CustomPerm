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
 * Multi-grade resolution, pure Java with no Minecraft or NeoForge import (AR8, AC6).
 *
 * <p>Rules, in order:</p>
 * <ol>
 *   <li>The most specific entry wins, like LuckPerms: the exact node beats {@code a.b.*}, which beats
 *       {@code a.*}, which beats {@code *}. So a grade denying {@code *} and allowing
 *       {@code customperm.command.home} refuses everything except {@code /home}.</li>
 *   <li>At the same specificity, the heaviest grade decides ({@link GradesConfig.Grade#weight}), like a
 *       LuckPerms group weight. Weight never beats specificity: it only breaks a tie between grades that
 *       cover the node just as precisely.</li>
 *   <li>Between equal weights, a DENY wins over an ALLOW, whichever grades they come from
 *       (INVARIANT-101). Every weight left at 0, which is what a file written before the field
 *       deserializes to, makes this the only tie-break, as it was.</li>
 *   <li>The grades assigned to the player decide first. Only when none of them mentions the node does
 *       the default grade, which applies to every player, decide.</li>
 *   <li>Nothing matching at all is {@link Tristate#UNSET}: the caller decides, usually from the
 *       operator level.</li>
 * </ol>
 */
public final class PermissionResolver {

    /** Specificity of an exact match, above any wildcard depth. */
    private static final int EXACT = Integer.MAX_VALUE;
    private static final int NONE = -1;

    private PermissionResolver() {}

    /**
     * Explicit value of {@code node} for {@code uuid}.
     *
     * @param defaultGrade grade applied to every player, or {@code null} / empty for none
     */
    public static Tristate check(GradesConfig grades, UUID uuid, String node, String defaultGrade) {
        if (node == null || uuid == null) return Tristate.UNSET;
        boolean hasDefault = defaultGrade != null && !defaultGrade.isEmpty();
        List<String> assigned = grades.userGrades.get(uuid.toString());
        if (assigned != null) {
            Tristate own = layer(grades, assigned, node, hasDefault ? defaultGrade : null);
            if (own != Tristate.UNSET) return own;
        }
        return hasDefault ? layer(grades, List.of(defaultGrade), node, null) : Tristate.UNSET;
    }

    /** True only for an explicit ALLOW from the assigned grades, ignoring any default grade. */
    public static boolean resolve(GradesConfig grades, UUID uuid, String node) {
        return check(grades, uuid, node, null) == Tristate.ALLOW;
    }

    /**
     * Verdict of one layer of grades: the most specific entry wins, the heaviest grade breaks a tie on
     * specificity, and a DENY breaks a tie on weight. Written so the outcome does not depend on the order
     * the grades are iterated in, which is the order they were assigned in and carries no meaning.
     */
    private static Tristate layer(GradesConfig grades, List<String> gradeNames, String node, String skip) {
        int bestSpecificity = NONE;
        int bestWeight = 0;
        Tristate best = Tristate.UNSET;
        for (String gradeName : gradeNames) {
            if (gradeName == null || gradeName.equals(skip)) continue;
            GradesConfig.Grade grade = grades.grades.get(gradeName);
            if (grade == null) continue;
            int allow = specificity(grade.permissions, node);
            int deny = specificity(grade.deniedPermissions, node);
            if (allow == NONE && deny == NONE) continue;
            // Inside one grade the same rule applies, DENY included: a grade that both allows and denies
            // a node at the same level refuses it.
            Tristate verdict = deny >= allow ? Tristate.DENY : Tristate.ALLOW;
            int specificity = Math.max(allow, deny);
            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                bestWeight = grade.weight;
                best = verdict;
            } else if (specificity == bestSpecificity) {
                if (grade.weight > bestWeight) {
                    bestWeight = grade.weight;
                    best = verdict;
                } else if (grade.weight == bestWeight && verdict == Tristate.DENY) {
                    best = Tristate.DENY;
                }
            }
        }
        return best;
    }

    /**
     * How specifically {@code perms} covers {@code node}: {@link #EXACT} for the node itself, the number
     * of segments before {@code .*} for a wildcard on an ancestor ({@code customperm.command.*} is 2),
     * 0 for {@code *}, and -1 when nothing covers it. A {@code prefix.*} covers descendants only, never
     * the prefix itself. Package-private so PermissionResolverTest can call it directly.
     */
    static int specificity(Set<String> perms, String node) {
        if (perms == null || perms.isEmpty()) return NONE;
        if (perms.contains(node)) return EXACT;
        int depth = segments(node) - 1;
        for (int dot = node.lastIndexOf('.'); dot > 0; dot = node.lastIndexOf('.', dot - 1), depth--) {
            if (perms.contains(node.substring(0, dot) + ".*")) return depth;
        }
        return perms.contains("*") ? 0 : NONE;
    }

    /** Whether {@code perms} covers {@code node} at all. */
    static boolean matchesNode(Set<String> perms, String node) {
        return specificity(perms, node) != NONE;
    }

    private static int segments(String node) {
        int count = 1;
        for (int i = 0; i < node.length(); i++) {
            if (node.charAt(i) == '.') count++;
        }
        return count;
    }
}
