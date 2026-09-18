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
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;

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
 *
 * <p>A chat prefix or suffix ({@link #prefix}, {@link #suffix}) is resolved by the same ranking with the
 * specificity step dropped, there being no specificity between two prefixes: the player's own above every
 * grade, then the heaviest grade, then the nearest ancestor inside a chain, refusals and the default grade
 * applying as they do to a node. Between two grades of equal weight the text that sorts first wins, so the
 * answer never depends on the order grades were assigned in.</p>
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

        List<String> refused = Expiry.alive(grades.userDeniedGrades.get(user), grades.userDeniedGradeExpiries.get(user));

        Ranked<Tristate> own = new Ranked<>(DENY_WINS);
        offerNode(own, specificity(grades.userPermissions.get(user), grades.userPermissionExpiries.get(user), node),
                specificity(grades.userDeniedPermissions.get(user), grades.userDeniedPermissionExpiries.get(user), node),
                PLAYER_RANK);
        List<String> assigned = Expiry.alive(grades.userGrades.get(user), grades.userGradeExpiries.get(user));
        if (assigned != null) {
            offerGrades(own, NODES, grades, assigned, node, hasDefault ? defaultGrade : null, refused);
        }
        if (own.value != null) return own.value;

        if (!hasDefault) return Tristate.UNSET;
        Ranked<Tristate> fallback = new Ranked<>(DENY_WINS);
        offerGrades(fallback, NODES, grades, List.of(defaultGrade), node, null, refused);
        return fallback.value == null ? Tristate.UNSET : fallback.value;
    }

    /** The chat prefix of {@code uuid}, or {@code null} when neither they nor any grade of theirs has one. */
    public static String prefix(GradesConfig grades, UUID uuid, String defaultGrade) {
        return meta(grades, uuid, defaultGrade, grades.userPrefixes, PREFIX);
    }

    /** The chat suffix of {@code uuid}, or {@code null} when neither they nor any grade of theirs has one. */
    public static String suffix(GradesConfig grades, UUID uuid, String defaultGrade) {
        return meta(grades, uuid, defaultGrade, grades.userSuffixes, SUFFIX);
    }

    private static String meta(GradesConfig grades, UUID uuid, String defaultGrade, Map<String, String> own,
                               Reader<String> reader) {
        if (uuid == null) return null;
        boolean hasDefault = defaultGrade != null && !defaultGrade.isEmpty();
        String user = uuid.toString();
        String mine = own.get(user);
        if (mine != null && !mine.isEmpty()) return mine;

        List<String> refused = Expiry.alive(grades.userDeniedGrades.get(user), grades.userDeniedGradeExpiries.get(user));
        Ranked<String> held = new Ranked<>(FIRST_SORTED);
        List<String> assigned = Expiry.alive(grades.userGrades.get(user), grades.userGradeExpiries.get(user));
        if (assigned != null) {
            offerGrades(held, reader, grades, assigned, null, hasDefault ? defaultGrade : null, refused);
        }
        if (held.value != null || !hasDefault) return held.value;
        Ranked<String> fallback = new Ranked<>(FIRST_SORTED);
        offerGrades(fallback, reader, grades, List.of(defaultGrade), null, null, refused);
        return fallback.value;
    }

    /** True only for an explicit ALLOW from the assigned grades, ignoring any default grade. */
    public static boolean resolve(GradesConfig grades, UUID uuid, String node) {
        return check(grades, uuid, node, null) == Tristate.ALLOW;
    }

    /**
     * What one grade says about the thing being resolved, offered at {@code rank}. The key is the node
     * for a permission and unused for a prefix; it is passed rather than captured so the readers stay
     * constants and a permission check allocates nothing for them.
     */
    @FunctionalInterface
    private interface Reader<T> {
        void read(Ranked<T> into, GradesConfig.Grade grade, String key, long rank);
    }

    private static final Reader<Tristate> NODES = (into, grade, node, rank) ->
            offerNode(into, specificity(grade.permissions, grade.permissionExpiries, node),
                    specificity(grade.deniedPermissions, grade.deniedPermissionExpiries, node), rank);
    private static final Reader<String> PREFIX = (into, grade, key, rank) -> offerText(into, grade.prefix, rank);
    private static final Reader<String> SUFFIX = (into, grade, key, rank) -> offerText(into, grade.suffix, rank);

    /** Between equal ranks, a DENY wins over an ALLOW (INVARIANT-101). */
    private static final BinaryOperator<Tristate> DENY_WINS =
            (kept, offered) -> offered == Tristate.DENY ? Tristate.DENY : kept;
    /** Between equal ranks, the text that sorts first: any fixed rule would do, as long as it ignores order. */
    private static final BinaryOperator<String> FIRST_SORTED =
            (kept, offered) -> offered.compareTo(kept) < 0 ? offered : kept;

    /** Inside one holder the same rule applies, DENY included: allowing and denying at one level refuses. */
    private static void offerNode(Ranked<Tristate> into, int allow, int deny, long rank) {
        if (allow == NONE && deny == NONE) return;
        into.offer(Math.max(allow, deny), deny >= allow ? Tristate.DENY : Tristate.ALLOW, rank);
    }

    /** A prefix has no specificity: every one is offered at the same reach, so only the rank decides. */
    private static void offerText(Ranked<String> into, String text, long rank) {
        if (text != null && !text.isEmpty()) into.offer(EXACT, text, rank);
    }

    private static <T> void offerGrades(Ranked<T> best, Reader<T> reader, GradesConfig grades,
                                        List<String> gradeNames, String node, String skip, List<String> refused) {
        for (String gradeName : gradeNames) {
            if (gradeName == null || gradeName.equals(skip)) continue;
            if (refused != null && refused.contains(gradeName)) continue;
            GradesConfig.Grade grade = grades.grades.get(gradeName);
            if (grade == null) continue;
            if (grade.parents.isEmpty()) {
                // The overwhelming case, and the one that must stay free of the walk's allocations.
                reader.read(best, grade, node, grade.weight);
                continue;
            }
            // A chain answers with one verdict, which then competes with the other grades at the weight of
            // the grade the player actually holds: what a parent says arrives through its child.
            Ranked<T> chain = new Ranked<>(best.onTie);
            walkChain(chain, reader, grades, gradeName, grade, node, refused);
            best.merge(chain, grade.weight);
        }
    }

    /**
     * Resolves one grade and its ancestors, nearest first. Breadth-first, so the first time a grade is
     * reached is by its shortest path and the nearer holder wins a tie on specificity, which is what makes
     * a child override what it inherits. A grade already seen is not walked again, so a cycle stops at the
     * grade it comes back to instead of recursing, and {@link #MAX_INHERITANCE_DEPTH} bounds the rest.
     */
    private static <T> void walkChain(Ranked<T> chain, Reader<T> reader, GradesConfig grades, String rootName,
                                      GradesConfig.Grade root, String node, List<String> refused) {
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
                reader.read(chain, grade, node, -depth);
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
     * specificity, and {@code onTie} breaks a tie on rank. A rank is a grade weight, or {@link #PLAYER_RANK}
     * for what the player carries themselves. Written so the outcome does not depend on the order things
     * are offered in, which is the order grades were assigned in and carries no meaning. Permissions and
     * prefixes are both resolved here, so the two can never rank holders differently.
     */
    private static final class Ranked<T> {
        private final BinaryOperator<T> onTie;
        private int specificity = NONE;
        private long rank;
        private T value;

        Ranked(BinaryOperator<T> onTie) {
            this.onTie = onTie;
        }

        /** Takes the answer of a resolved inheritance chain as one entry, at the rank of the grade held. */
        void merge(Ranked<T> chain, long rank) {
            if (chain.value != null) offer(chain.specificity, chain.value, rank);
        }

        void offer(int reach, T candidate, long rank) {
            if (reach > specificity) {
                specificity = reach;
                this.rank = rank;
                value = candidate;
            } else if (reach == specificity) {
                if (rank > this.rank) {
                    this.rank = rank;
                    value = candidate;
                } else if (rank == this.rank) {
                    value = onTie.apply(value, candidate);
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
        return specificity(perms, null, node);
    }

    /**
     * {@link #specificity(Set, String)}, skipping an entry whose expiry has passed: an expired entry does
     * not exist, so a less specific one below it answers instead. The expiry is read only for an entry that
     * matches, and only when the holder has a temporary entry at all.
     */
    static int specificity(Set<String> perms, Map<String, Long> expiries, String node) {
        if (perms == null || perms.isEmpty()) return NONE;
        if (perms.contains(node) && Expiry.alive(expiries, node)) return EXACT;
        int depth = segments(node) - 1;
        for (int dot = node.lastIndexOf('.'); dot > 0; dot = node.lastIndexOf('.', dot - 1), depth--) {
            String wildcard = node.substring(0, dot) + ".*";
            if (perms.contains(wildcard) && Expiry.alive(expiries, wildcard)) return depth;
        }
        return perms.contains("*") && Expiry.alive(expiries, "*") ? 0 : NONE;
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
