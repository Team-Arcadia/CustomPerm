/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.bench;

import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.perm.Contexts;
import com.arcadia.customperm.perm.PermissionResolver;
import com.arcadia.customperm.perm.Tristate;
import org.openjdk.jmh.annotations.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmarks of the permission resolution hot path (story 6-3).
 *
 * <p>Cible : {@link PermissionResolver#resolve(GradesConfig, UUID, String)} — classe
 * Pure Java, with no Minecraft or NeoForge import, so it runs in a standalone JVM.</p>
 *
 * <p>NFR1/NFR4 bound: every {@code avgt} score must be
 * {@code < 5 000 000 ns} (5 ms). En pratique attendu sub-microseconde (~200–800 ns).</p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class PermissionResolverBenchmark {

    private GradesConfig grades;
    private UUID         uuid;
    /** Holds a single grade at the end of a three-deep inheritance chain. */
    private UUID         heir;
    /** Holds one grade that also carries nodes limited to the Nether. */
    private UUID         traveller;

    private static final Contexts IN_NETHER = Contexts.world("minecraft:the_nether");

    private static final String ALLOWED_NODE = "customperm.command.fly";
    private static final String DENIED_NODE  = "customperm.command.ban";
    private static final String ABSENT_NODE  = "customperm.notexist";

    /**
     * Setup : 3 grades assignés, 10 permissions each (mirrors NFR scenario).
     * Grade 1 : 10 ALLOWs dont ALLOWED_NODE.
     * Grade 2 : 10 ALLOWs (autres nœuds).
     * Grade 3: an explicit DENY on DENIED_NODE (INVARIANT-101).
     */
    @Setup(Level.Trial)
    public void setup() {
        grades = new GradesConfig();
        grades.grades = new LinkedHashMap<>();
        uuid   = UUID.randomUUID();

        // Grade 1 — default : 10 ALLOW dont ALLOWED_NODE
        GradesConfig.Grade g1 = new GradesConfig.Grade();
        g1.name = "default";
        for (int i = 0; i < 10; i++) g1.permissions.add("customperm.command.perm" + i);
        g1.permissions.add(ALLOWED_NODE);
        grades.grades.put("default", g1);

        // Grade 2 — member : 10 ALLOW (nœuds différents)
        GradesConfig.Grade g2 = new GradesConfig.Grade();
        g2.name = "member";
        for (int i = 0; i < 10; i++) g2.permissions.add("customperm.member.perm" + i);
        grades.grades.put("member", g2);

        // Grade 3, restricted: a DENY on DENIED_NODE, which short-circuits per INVARIANT-101
        GradesConfig.Grade g3 = new GradesConfig.Grade();
        g3.name = "restricted";
        g3.deniedPermissions.add(DENIED_NODE);
        grades.grades.put("restricted", g3);

        grades.userGrades.put(uuid.toString(), List.of("default", "member", "restricted"));

        // Inheritance: heir -> mid -> base, 10 ALLOW each, the node answered by the farthest ancestor.
        // Measures the walk, which a grade without parents never pays for.
        heir = UUID.randomUUID();
        String child = null;
        for (String name : List.of("chain_base", "chain_mid", "chain_leaf")) {
            GradesConfig.Grade g = new GradesConfig.Grade();
            g.name = name;
            for (int i = 0; i < 10; i++) g.permissions.add("customperm." + name + ".perm" + i);
            if (child != null) g.parents.add(child);
            grades.grades.put(name, g);
            child = name;
        }
        grades.grades.get("chain_base").permissions.add(ALLOWED_NODE);
        grades.userGrades.put(heir.toString(), List.of("chain_leaf"));

        // Contextual: a grade with 10 global ALLOW and 10 more limited to the Nether, one overriding a global one.
        traveller = UUID.randomUUID();
        GradesConfig.Grade explorer = new GradesConfig.Grade();
        explorer.name = "explorer";
        for (int i = 0; i < 10; i++) explorer.permissions.add("customperm.explorer.perm" + i);
        explorer.permissions.add(ALLOWED_NODE);
        GradesConfig.GradeScoped nether = new GradesConfig.GradeScoped();
        for (int i = 0; i < 10; i++) nether.permissions.add("customperm.nether.perm" + i);
        nether.deniedPermissions.add(ALLOWED_NODE);
        explorer.contexts.put("world=minecraft:the_nether", nether);
        grades.grades.put("explorer", explorer);
        grades.userGrades.put(traveller.toString(), List.of("explorer"));
    }

    /**
     * AC2, the ALLOW case: the node is in grade 1, and all 3 grades are resolved.
     * Résultat attendu : {@code true}.
     */
    @Benchmark
    public boolean resolveAllow() {
        return PermissionResolver.resolve(grades, uuid, ALLOWED_NODE);
    }

    /**
     * AC2 — Scénario DENY short-circuit (INVARIANT-101) : grade 3 contient un DENY explicite.
     * The algorithm must short-circuit as soon as the DENY is found.
     * Résultat attendu : {@code false}.
     */
    @Benchmark
    public boolean resolveDeny() {
        return PermissionResolver.resolve(grades, uuid, DENIED_NODE);
    }

    /**
     * Inheritance: the node is answered two levels up, so the whole chain is walked.
     * Résultat attendu : {@code true}.
     */
    @Benchmark
    public boolean resolveAllowThroughInheritance() {
        return PermissionResolver.resolve(grades, heir, ALLOWED_NODE);
    }

    /**
     * AC2, the missing-node case: every grade is walked, nothing matches, false is returned.
     * This is the worst case, a full scan with no early exit.
     * Résultat attendu : {@code false}.
     */
    @Benchmark
    public boolean resolveAbsent() {
        return PermissionResolver.resolve(grades, uuid, ABSENT_NODE);
    }

    /**
     * The path a server takes on every check since world contexts: the player's world is passed, but no
     * entry is limited to one. Must match {@link #resolveAllow}.
     */
    @Benchmark
    public Tristate resolveAllowInWorld() {
        return PermissionResolver.check(grades, uuid, ALLOWED_NODE, null, IN_NETHER);
    }

    /** A grade with nodes limited to the Nether, read from the Nether: the contextual DENY decides. */
    @Benchmark
    public Tristate resolveContextual() {
        return PermissionResolver.check(grades, traveller, ALLOWED_NODE, null, IN_NETHER);
    }
}
