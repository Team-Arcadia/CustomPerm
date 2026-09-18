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
 *       grades at the weight of the grade they actually hold. A temporary parent that has run out is
 *       not followed, and a temporary refusal that has run out refuses nothing.</li>
 *   <li>A grade is not reached at all when the holder refuses it: {@link GradesConfig#userDeniedGrades}
 *       takes it out of everything that player resolves, the default grade included, and
 *       {@link GradesConfig.Grade#deniedParents} takes it out of that grade's own chain, from the point
 *       where the refusal is declared. A refusal removes a grade from the resolution; it never turns
 *       what that grade allows into a denial.</li>
 *   <li>Nodes carried by the player themselves ({@link GradesConfig#userPermissions},
 *       {@link GradesConfig#userDeniedPermissions}) rank above every grade at the same specificity,
 *       whatever its weight, like a node set on a LuckPerms user rather than on one of their groups.
 *       They do not beat a more specific grade node either: the rule above comes first.</li>
 *   <li>An entry limited to a context the player is in ({@link GradesConfig.Grade#contexts},
 *       {@link GradesConfig#userContexts}, see {@link Contexts}) outranks the same holder's entry without
 *       one at the same specificity, like a contextual node in LuckPerms: a grade allowing a node
 *       everywhere and denying it in the Nether refuses it there. It ranks below the holder, never above:
 *       a heavier grade, or the player's own node, still decides over a lighter grade's contextual one.
 *       An entry limited to a context the player is not in does not exist for this check. A grade held in
 *       a context only is read like a grade held everywhere, while the player is there; so is a parent
 *       inherited, or a grade refused, in a context only.</li>
 *   <li>Between equal ranks, a DENY wins over an ALLOW, whichever grades they come from
 *       (INVARIANT-101). Every weight left at 0, which is what a file written before the field
 *       deserializes to, makes this the only tie-break, as it was.</li>
 *   <li>What the player carries, own nodes and assigned grades, decides first. Only when none of it
 *       mentions the node does the default grade, which applies to every player, decide.</li>
 *   <li>Nothing matching at all is {@link Tristate#UNSET}: the caller decides, usually from the
 *       operator level.</li>
 * </ol>
 *
 * <p>Chat prefixes and suffixes ({@link #prefixes}, {@link #suffixes}) are collected rather than decided:
 * every one the player reaches, ordered by priority, highest first, like LuckPerms. At equal priority the
 * holder ranks them as it ranks a node, the player's own above every grade, then the heaviest grade, then
 * the nearest ancestor inside a chain, then one limited to the player's world before the same holder's global
 * one, and the text that sorts first last of all, so the order never depends
 * on the order grades were assigned in. Refusals, expiries and the default grade apply as they do to a node.
 * Showing the first or several of them is the caller's choice ({@code chat/ChatStack}).</p>
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
        return check(grades, uuid, node, defaultGrade, Contexts.NONE);
    }

    /**
     * {@link #check(GradesConfig, UUID, String, String)} for a player in {@code contexts}: entries limited
     * to a context apply when the player is in it.
     */
    public static Tristate check(GradesConfig grades, UUID uuid, String node, String defaultGrade,
                                 Contexts contexts) {
        if (node == null || uuid == null) return Tristate.UNSET;
        boolean hasDefault = defaultGrade != null && !defaultGrade.isEmpty();
        String user = uuid.toString();

        Map<String, GradesConfig.UserScoped> scoped = contexts.isEmpty() ? null : grades.userContexts.get(user);
        List<String> refused = refused(grades, user, scoped, contexts);

        Ranked<Tristate> own = new Ranked<>(DENY_WINS);
        offerNode(own, specificity(grades.userPermissions.get(user), grades.userPermissionExpiries.get(user), node),
                specificity(grades.userDeniedPermissions.get(user), grades.userDeniedPermissionExpiries.get(user), node),
                PLAYER_RANK, 0);
        if (scoped != null) {
            for (Map.Entry<String, GradesConfig.UserScoped> entry : scoped.entrySet()) {
                if (!contexts.satisfies(entry.getKey())) continue;
                offerNode(own, specificity(entry.getValue().permissions, node),
                        specificity(entry.getValue().deniedPermissions, node), PLAYER_RANK,
                        Contexts.size(entry.getKey()));
            }
        }
        List<String> assigned = Expiry.alive(grades.userGrades.get(user), grades.userGradeExpiries.get(user));
        if (assigned != null) {
            offerGrades(own, NODES, grades, assigned, node, hasDefault ? defaultGrade : null, refused, contexts);
        }
        if (scoped != null) {
            for (Map.Entry<String, GradesConfig.UserScoped> entry : scoped.entrySet()) {
                if (entry.getValue().grades.isEmpty() || !contexts.satisfies(entry.getKey())) continue;
                offerGrades(own, NODES, grades, entry.getValue().grades, node, hasDefault ? defaultGrade : null,
                        refused, contexts);
            }
        }
        if (own.value != null) return own.value;

        if (!hasDefault) return Tristate.UNSET;
        Ranked<Tristate> fallback = new Ranked<>(DENY_WINS);
        offerGrades(fallback, NODES, grades, List.of(defaultGrade), node, null, refused, contexts);
        return fallback.value == null ? Tristate.UNSET : fallback.value;
    }

    /** The chat prefix that shows first for {@code uuid}, or {@code null} when they reach none. */
    public static String prefix(GradesConfig grades, UUID uuid, String defaultGrade) {
        List<String> all = prefixes(grades, uuid, defaultGrade);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The chat suffix that shows first for {@code uuid}, or {@code null} when they reach none. */
    public static String suffix(GradesConfig grades, UUID uuid, String defaultGrade) {
        List<String> all = suffixes(grades, uuid, defaultGrade);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every chat prefix {@code uuid} reaches, the one that shows first first, each text once. */
    public static List<String> prefixes(GradesConfig grades, UUID uuid, String defaultGrade) {
        return chat(grades, uuid, defaultGrade, false, Contexts.NONE);
    }

    /** Every chat suffix {@code uuid} reaches, ordered like {@link #prefixes}. */
    public static List<String> suffixes(GradesConfig grades, UUID uuid, String defaultGrade) {
        return chat(grades, uuid, defaultGrade, true, Contexts.NONE);
    }

    /** {@link #prefixes}, for a player in {@code contexts}: entries limited to one apply there. */
    public static List<String> prefixes(GradesConfig grades, UUID uuid, String defaultGrade, Contexts contexts) {
        return chat(grades, uuid, defaultGrade, false, contexts);
    }

    /** {@link #suffixes}, for a player in {@code contexts}. */
    public static List<String> suffixes(GradesConfig grades, UUID uuid, String defaultGrade, Contexts contexts) {
        return chat(grades, uuid, defaultGrade, true, contexts);
    }

    /** One prefix or suffix reached, with what orders it. */
    private record Found(int priority, long rank, int depth, int context, String text) {
    }

    /** Priority, then holder, then nearness in a chain, then the entry naming more contexts, then text. */
    private static final java.util.Comparator<Found> SHOWN_FIRST =
            java.util.Comparator.comparingInt(Found::priority).reversed()
                    .thenComparing(java.util.Comparator.comparingLong(Found::rank).reversed())
                    .thenComparingInt(Found::depth)
                    .thenComparing(java.util.Comparator.comparingInt(Found::context).reversed())
                    .thenComparing(Found::text);

    /**
     * Off the permission path: called when a name is built, not per command, so it may allocate. It walks
     * the grades the way a node does, through {@link #walkChain}, so the two can never disagree on what a
     * player holds; only what is read on each grade differs.
     */
    private static List<String> chat(GradesConfig grades, UUID uuid, String defaultGrade, boolean suffix,
                                     Contexts contexts) {
        if (uuid == null) return List.of();
        boolean hasDefault = defaultGrade != null && !defaultGrade.isEmpty();
        String user = uuid.toString();
        long now = Expiry.now();
        List<Found> found = new ArrayList<>();
        collect(found, (suffix ? grades.userSuffixEntries : grades.userPrefixEntries).get(user), PLAYER_RANK, 0, 0, now);
        Map<String, GradesConfig.UserScoped> scoped = contexts.isEmpty() ? null : grades.userContexts.get(user);
        if (scoped != null) {
            for (Map.Entry<String, GradesConfig.UserScoped> entry : scoped.entrySet()) {
                if (!contexts.satisfies(entry.getKey())) continue;
                collect(found, suffix ? entry.getValue().suffixes : entry.getValue().prefixes, PLAYER_RANK, 0,
                        Contexts.size(entry.getKey()), now);
            }
        }

        List<String> refused = refused(grades, user, scoped, contexts);
        List<String> assigned = Expiry.alive(grades.userGrades.get(user), grades.userGradeExpiries.get(user));
        if (assigned != null) {
            collectGrades(found, grades, assigned, hasDefault ? defaultGrade : null, refused, suffix, now, contexts);
        }
        if (scoped != null) {
            for (Map.Entry<String, GradesConfig.UserScoped> entry : scoped.entrySet()) {
                if (entry.getValue().grades.isEmpty() || !contexts.satisfies(entry.getKey())) continue;
                collectGrades(found, grades, entry.getValue().grades, hasDefault ? defaultGrade : null, refused,
                        suffix, now, contexts);
            }
        }
        // As for a node, the default grade speaks only when nothing the player carries does.
        if (found.isEmpty() && hasDefault) {
            collectGrades(found, grades, List.of(defaultGrade), null, refused, suffix, now, contexts);
        }
        found.sort(SHOWN_FIRST);
        java.util.LinkedHashSet<String> texts = new java.util.LinkedHashSet<>();
        for (Found entry : found) texts.add(entry.text());
        return List.copyOf(texts);
    }

    private static void collectGrades(List<Found> found, GradesConfig grades, List<String> gradeNames, String skip,
                                      List<String> refused, boolean suffix, long now, Contexts contexts) {
        for (String gradeName : gradeNames) {
            if (gradeName == null || gradeName.equals(skip)) continue;
            if (refused != null && refused.contains(gradeName)) continue;
            GradesConfig.Grade grade = grades.grades.get(gradeName);
            if (grade == null) continue;
            long weight = grade.weight;
            // The walk passes minus the depth as the rank; what the player holds ranks the whole chain.
            Reader<String> reader = (into, reached, key, depth, where) -> {
                collect(found, suffix ? reached.suffixes : reached.prefixes, weight, (int) -depth, 0, now);
                if (reached.contexts.isEmpty() || where.isEmpty()) return;
                for (Map.Entry<String, GradesConfig.GradeScoped> entry : reached.contexts.entrySet()) {
                    if (!where.satisfies(entry.getKey())) continue;
                    collect(found, suffix ? entry.getValue().suffixes : entry.getValue().prefixes, weight,
                            (int) -depth, Contexts.size(entry.getKey()), now);
                }
            };
            walkChain(new Ranked<>(KEEP), reader, grades, gradeName, grade, null, refused, contexts);
        }
    }

    private static void collect(List<Found> found, List<GradesConfig.ChatEntry> entries, long rank, int depth,
                                int context, long now) {
        if (entries == null) return;
        for (GradesConfig.ChatEntry entry : entries) {
            if (entry.text != null && !entry.text.isEmpty() && entry.alive(now)) {
                found.add(new Found(entry.priority, rank, depth, context, entry.text));
            }
        }
    }

    /**
     * The grades {@code user} refuses where they stand: for good, and in the contexts they are in. The list
     * the file holds, untouched, when no refusal is limited to a context, so the usual check allocates nothing.
     */
    private static List<String> refused(GradesConfig grades, String user, Map<String, GradesConfig.UserScoped> scoped,
                                        Contexts contexts) {
        List<String> refused = Expiry.alive(grades.userDeniedGrades.get(user), grades.userDeniedGradeExpiries.get(user));
        if (scoped == null) return refused;
        List<String> merged = null;
        for (Map.Entry<String, GradesConfig.UserScoped> entry : scoped.entrySet()) {
            List<String> here = entry.getValue().refused;
            if (here.isEmpty() || !contexts.satisfies(entry.getKey())) continue;
            if (merged == null) merged = refused == null ? new ArrayList<>() : new ArrayList<>(refused);
            merged.addAll(here);
        }
        return merged == null ? refused : merged;
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
        void read(Ranked<T> into, GradesConfig.Grade grade, String key, long rank, Contexts contexts);
    }

    private static final Reader<Tristate> NODES = (into, grade, node, rank, contexts) -> {
        offerNode(into, specificity(grade.permissions, grade.permissionExpiries, node),
                specificity(grade.deniedPermissions, grade.deniedPermissionExpiries, node), rank, 0);
        // Checked for emptiness first: a grade without contextual nodes, the usual one, pays nothing more.
        if (grade.contexts.isEmpty() || contexts.isEmpty()) return;
        for (Map.Entry<String, GradesConfig.GradeScoped> entry : grade.contexts.entrySet()) {
            if (!contexts.satisfies(entry.getKey())) continue;
            offerNode(into, specificity(entry.getValue().permissions, node),
                    specificity(entry.getValue().deniedPermissions, node), rank, Contexts.size(entry.getKey()));
        }
    };

    /** Between equal ranks, a DENY wins over an ALLOW (INVARIANT-101). */
    private static final BinaryOperator<Tristate> DENY_WINS =
            (kept, offered) -> offered == Tristate.DENY ? Tristate.DENY : kept;
    /** The chat walk decides nothing through a ranking: its reader collects, and this is never reached. */
    private static final BinaryOperator<String> KEEP = (kept, offered) -> kept;

    /** Inside one holder the same rule applies, DENY included: allowing and denying at one level refuses. */
    private static void offerNode(Ranked<Tristate> into, int allow, int deny, long rank, int context) {
        if (allow == NONE && deny == NONE) return;
        into.offer(Math.max(allow, deny), deny >= allow ? Tristate.DENY : Tristate.ALLOW, rank, context);
    }

    private static <T> void offerGrades(Ranked<T> best, Reader<T> reader, GradesConfig grades,
                                        List<String> gradeNames, String node, String skip, List<String> refused,
                                        Contexts contexts) {
        for (String gradeName : gradeNames) {
            if (gradeName == null || gradeName.equals(skip)) continue;
            if (refused != null && refused.contains(gradeName)) continue;
            GradesConfig.Grade grade = grades.grades.get(gradeName);
            if (grade == null) continue;
            if (grade.parents.isEmpty() && !inheritsHere(grade, contexts)) {
                // The overwhelming case, and the one that must stay free of the walk's allocations.
                reader.read(best, grade, node, grade.weight, contexts);
                continue;
            }
            // A chain answers with one verdict, which then competes with the other grades at the weight of
            // the grade the player actually holds: what a parent says arrives through its child.
            Ranked<T> chain = new Ranked<>(best.onTie);
            walkChain(chain, reader, grades, gradeName, grade, node, refused, contexts);
            best.merge(chain, grade.weight);
        }
    }

    /** Marks as seen the grades {@code grade} refuses in the contexts the player is in. */
    private static void refuseHere(GradesConfig.Grade grade, Contexts contexts, Set<String> seen) {
        for (Map.Entry<String, GradesConfig.GradeScoped> entry : grade.contexts.entrySet()) {
            if (contexts.satisfies(entry.getKey())) seen.addAll(entry.getValue().refused);
        }
    }

    /** Adds the parents {@code grade} inherits in the player's contexts, at the depth of a global parent. */
    private static void inheritHere(GradesConfig grades, GradesConfig.Grade grade, Contexts contexts, Set<String> seen,
                                    List<GradesConfig.Grade> next) {
        for (Map.Entry<String, GradesConfig.GradeScoped> entry : grade.contexts.entrySet()) {
            if (entry.getValue().parents.isEmpty() || !contexts.satisfies(entry.getKey())) continue;
            for (String parent : entry.getValue().parents) {
                if (parent == null || !seen.add(parent)) continue;
                GradesConfig.Grade inherited = grades.grades.get(parent);
                if (inherited != null) next.add(inherited);
            }
        }
    }

    /** Whether {@code grade} inherits something in one of {@code contexts} only; checked after its global parents. */
    private static boolean inheritsHere(GradesConfig.Grade grade, Contexts contexts) {
        if (grade.contexts.isEmpty() || contexts.isEmpty()) return false;
        for (Map.Entry<String, GradesConfig.GradeScoped> entry : grade.contexts.entrySet()) {
            if (!entry.getValue().parents.isEmpty() && contexts.satisfies(entry.getKey())) return true;
        }
        return false;
    }

    /**
     * Resolves one grade and its ancestors, nearest first. Breadth-first, so the first time a grade is
     * reached is by its shortest path and the nearer holder wins a tie on specificity, which is what makes
     * a child override what it inherits. A grade already seen is not walked again, so a cycle stops at the
     * grade it comes back to instead of recursing, and {@link #MAX_INHERITANCE_DEPTH} bounds the rest.
     */
    private static <T> void walkChain(Ranked<T> chain, Reader<T> reader, GradesConfig grades, String rootName,
                                      GradesConfig.Grade root, String node, List<String> refused,
                                      Contexts contexts) {
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
                reader.read(chain, grade, node, -depth, contexts);
                // Read where it is declared: a grade nearer to the holder has already been walked, so its
                // refusal closes a farther one, never the other way round.
                for (String refusedParent : grade.deniedParents) {
                    if (Expiry.alive(grade.deniedParentExpiries, refusedParent)) seen.add(refusedParent);
                }
                // The contextual part lives apart, so this loop stays small enough for the JIT to inline.
                boolean scoped = !grade.contexts.isEmpty() && !contexts.isEmpty();
                if (scoped) refuseHere(grade, contexts, seen);
                for (String parent : grade.parents) {
                    // Alive first: a parent that ran out must not mark the grade as seen for another path.
                    if (parent == null || !Expiry.alive(grade.parentExpiries, parent) || !seen.add(parent)) continue;
                    GradesConfig.Grade inherited = grades.grades.get(parent);
                    if (inherited != null) next.add(inherited);
                }
                if (scoped) inheritHere(grades, grade, contexts, seen, next);
            }
            level = next;
        }
    }

    /**
     * The best entry seen so far in one layer: the most specific wins, the highest rank breaks a tie on
     * specificity, the entry naming more contexts breaks a tie on rank, and {@code onTie} breaks the rest. A rank is a grade weight, or {@link #PLAYER_RANK}
     * for what the player carries themselves. Written so the outcome does not depend on the order things
     * are offered in, which is the order grades were assigned in and carries no meaning.
     */
    private static final class Ranked<T> {
        private final BinaryOperator<T> onTie;
        private int specificity = NONE;
        private long rank;
        /** How many context pairs the kept entry names, 0 for one that applies everywhere. */
        private int context;
        private T value;

        Ranked(BinaryOperator<T> onTie) {
            this.onTie = onTie;
        }

        /** Takes the answer of a resolved inheritance chain as one entry, at the rank of the grade held. */
        void merge(Ranked<T> chain, long rank) {
            if (chain.value != null) offer(chain.specificity, chain.value, rank, chain.context);
        }

        void offer(int reach, T candidate, long rank, int context) {
            if (reach > specificity
                    || (reach == specificity && (rank > this.rank || (rank == this.rank && context > this.context)))) {
                specificity = reach;
                this.rank = rank;
                this.context = context;
                value = candidate;
            } else if (reach == specificity && rank == this.rank && context == this.context) {
                value = onTie.apply(value, candidate);
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
