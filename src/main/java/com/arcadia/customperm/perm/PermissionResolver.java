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

import java.util.ArrayList;
import java.util.HashSet;
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
 *   <li>A grade inherits its parents ({@link GradesConfig.Grade#parents}): where it says nothing as
 *       precise about the node, what its parents say applies, nearest first, so a grade overrides what
 *       it inherits. A chain answers with one verdict, which then competes with the player's other
 *       grades at the weight of the grade they actually hold.</li>
 *   <li>A grade is not reached at all when the holder refuses it: {@link GradesConfig#userDeniedGrades}
 *       takes it out of everything that player resolves, the default grade included, and
 *       {@link GradesConfig.Grade#deniedParents} takes it out of that grade's own chain, from the point
 *       where the refusal is declared. A refusal removes a grade from the resolution; it never turns
 *       what that grade allows into a denial.</li>
 *   <li>Nodes carried by the player themselves ({@link GradesConfig#userPermissions},
 *       {@link GradesConfig#userDeniedPermissions}) rank above every grade at the same specificity,
 *       whatever its weight, like a node set on a LuckPerms user rather than on one of their groups.
 *       They do not beat a more specific grade node either: the rule above comes first.</li>
 *   <li>Between equal ranks, a DENY wins over an ALLOW, whichever grades they come from
 *       (INVARIANT-101). Every weight left at 0, which is what a file written before the field
 *       deserializes to, makes this the only tie-break, as it was.</li>
 *   <li>What the player carries, own nodes and assigned grades, decides first. Only when none of it
 *       mentions the node does the default grade, which applies to every player, decide.</li>
 *   <li>Nothing matching at all is {@link Tristate#UNSET}: the caller decides, usually from the
 *       operator level.</li>
 * </ol>
 */
public final class PermissionResolver {

    /** Specificity of an exact match, above any wildcard depth. */
    private static final int EXACT = Integer.MAX_VALUE;
    private static final int NONE = -1;

    /** Rank of the nodes a player carries themselves: above any grade weight, including {@code Integer.MAX_VALUE}. */
    private static final long PLAYER_RANK = Long.MAX_VALUE;

    /**
     * How far a grade's ancestry is followed. Already-seen grades are skipped, so this only bounds a
     * hand-written file with a very long chain, on a path called once per command per player.
     */
    private static final int MAX_INHERITANCE_DEPTH = 16;

    private PermissionResolver() {}

    /**
     * Explicit value of {@code node} for {@code uuid}.
     *
     * @param defaultGrade grade applied to every player, or {@code null} / empty for none
     */
    public static Tristate check(GradesConfig grades, UUID uuid, String node, String defaultGrade) {
        if (node == null || uuid == null) return Tristate.UNSET;
        boolean hasDefault = defaultGrade != null && !defaultGrade.isEmpty();
        String user = uuid.toString();

        List<String> refused = grades.userDeniedGrades.get(user);

        Ranked own = new Ranked();
        own.offer(specificity(grades.userPermissions.get(user), node),
                specificity(grades.userDeniedPermissions.get(user), node), PLAYER_RANK);
        List<String> assigned = grades.userGrades.get(user);
        if (assigned != null) {
            offerGrades(own, grades, assigned, node, hasDefault ? defaultGrade : null, refused);
        }
        if (own.verdict != Tristate.UNSET) return own.verdict;

        if (!hasDefault) return Tristate.UNSET;
        Ranked fallback = new Ranked();
        offerGrades(fallback, grades, List.of(defaultGrade), node, null, refused);
        return fallback.verdict;
    }

    /** True only for an explicit ALLOW from the assigned grades, ignoring any default grade. */
    public static boolean resolve(GradesConfig grades, UUID uuid, String node) {
        return check(grades, uuid, node, null) == Tristate.ALLOW;
    }

    private static void offerGrades(Ranked best, GradesConfig grades, List<String> gradeNames, String node,
                                    String skip, List<String> refused) {
        for (String gradeName : gradeNames) {
            if (gradeName == null || gradeName.equals(skip)) continue;
            if (refused != null && refused.contains(gradeName)) continue;
            GradesConfig.Grade grade = grades.grades.get(gradeName);
            if (grade == null) continue;
            if (grade.parents.isEmpty()) {
                // The overwhelming case, and the one that must stay free of the walk's allocations.
                best.offer(specificity(grade.permissions, node), specificity(grade.deniedPermissions, node),
                        grade.weight);
                continue;
            }
            // A chain answers with one verdict, which then competes with the other grades at the weight of
            // the grade the player actually holds: what a parent says arrives through its child.
            Ranked chain = new Ranked();
            walkChain(chain, grades, gradeName, grade, node, refused);
            best.merge(chain, grade.weight);
        }
    }

    /**
     * Resolves one grade and its ancestors, nearest first. Breadth-first, so the first time a grade is
     * reached is by its shortest path and the nearer holder wins a tie on specificity, which is what makes
     * a child override what it inherits. A grade already seen is not walked again, so a cycle stops at the
     * grade it comes back to instead of recursing, and {@link #MAX_INHERITANCE_DEPTH} bounds the rest.
     */
    private static void walkChain(Ranked chain, GradesConfig grades, String rootName, GradesConfig.Grade root,
                                  String node, List<String> refused) {
        // Grades are followed by the key they are stored under, never by their name field: a hand-edited
        // file can disagree on the two, and the key is what a parent entry names.
        Set<String> seen = new HashSet<>();
        // A refused grade is marked as already seen: nothing reaches it, whichever path would have.
        if (refused != null) seen.addAll(refused);
        List<GradesConfig.Grade> level = new ArrayList<>();
        seen.add(rootName);
        level.add(root);
        for (int depth = 0; depth <= MAX_INHERITANCE_DEPTH && !level.isEmpty(); depth++) {
            List<GradesConfig.Grade> next = new ArrayList<>();
            for (GradesConfig.Grade grade : level) {
                chain.offer(specificity(grade.permissions, node), specificity(grade.deniedPermissions, node), -depth);
                // Read where it is declared: a grade nearer to the holder has already been walked, so its
                // refusal closes a farther one, never the other way round.
                seen.addAll(grade.deniedParents);
                for (String parent : grade.parents) {
                    if (parent == null || !seen.add(parent)) continue;
                    GradesConfig.Grade inherited = grades.grades.get(parent);
                    if (inherited != null) next.add(inherited);
                }
            }
            level = next;
        }
    }

    /**
     * The best entry seen so far in one layer: the most specific wins, the highest rank breaks a tie on
     * specificity, and a DENY breaks a tie on rank. A rank is a grade weight, or {@link #PLAYER_RANK} for
     * the nodes the player carries themselves. Written so the outcome does not depend on the order things
     * are offered in, which is the order grades were assigned in and carries no meaning.
     */
    private static final class Ranked {
        private int specificity = NONE;
        private long rank;
        private Tristate verdict = Tristate.UNSET;

        void offer(int allow, int deny, long rank) {
            if (allow == NONE && deny == NONE) return;
            // Inside one holder the same rule applies, DENY included: a grade that both allows and denies
            // a node at the same level refuses it.
            offer(Math.max(allow, deny), deny >= allow ? Tristate.DENY : Tristate.ALLOW, rank);
        }

        /** Takes the verdict of a resolved inheritance chain as one entry, at the rank of the grade held. */
        void merge(Ranked chain, long rank) {
            if (chain.verdict != Tristate.UNSET) offer(chain.specificity, chain.verdict, rank);
        }

        private void offer(int reach, Tristate candidate, long rank) {
            if (reach > specificity) {
                specificity = reach;
                this.rank = rank;
                verdict = candidate;
            } else if (reach == specificity) {
                if (rank > this.rank) {
                    this.rank = rank;
                    verdict = candidate;
                } else if (rank == this.rank && candidate == Tristate.DENY) {
                    verdict = Tristate.DENY;
                }
            }
        }
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
