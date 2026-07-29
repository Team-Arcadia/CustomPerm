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
import com.arcadia.customperm.perm.PermissionResolver;
import org.openjdk.jmh.annotations.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Microbenchmarks sur le hot path de résolution de permission (story 6-3).
 *
 * <p>Cible : {@link PermissionResolver#resolve(GradesConfig, UUID, String)} — classe
 * 100 % Java pur, sans import Minecraft/NeoForge, instanciable dans un JVM autonome.</p>
 *
 * <p>Seuil NFR1/NFR4 : tous les scores {@code avgt} doivent être
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

    private static final String ALLOWED_NODE = "customperm.command.fly";
    private static final String DENIED_NODE  = "customperm.command.ban";
    private static final String ABSENT_NODE  = "customperm.notexist";

    /**
     * Setup : 3 grades assignés, 10 permissions each (mirrors NFR scenario).
     * Grade 1 : 10 ALLOWs dont ALLOWED_NODE.
     * Grade 2 : 10 ALLOWs (autres nœuds).
     * Grade 3 : DENY explicite sur DENIED_NODE (INVARIANT-101).
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

        // Grade 3 — restricted : DENY sur DENIED_NODE → short-circuit INVARIANT-101
        GradesConfig.Grade g3 = new GradesConfig.Grade();
        g3.name = "restricted";
        g3.deniedPermissions.add(DENIED_NODE);
        grades.grades.put("restricted", g3);

        grades.userGrades.put(uuid.toString(), List.of("default", "member", "restricted"));
    }

    /**
     * AC2 — Scénario ALLOW : nœud présent dans grade 1, résolution complète des 3 grades.
     * Résultat attendu : {@code true}.
     */
    @Benchmark
    public boolean resolveAllow() {
        return PermissionResolver.resolve(grades, uuid, ALLOWED_NODE);
    }

    /**
     * AC2 — Scénario DENY short-circuit (INVARIANT-101) : grade 3 contient un DENY explicite.
     * L'algorithme doit court-circuiter dès que le DENY est trouvé.
     * Résultat attendu : {@code false}.
     */
    @Benchmark
    public boolean resolveDeny() {
        return PermissionResolver.resolve(grades, uuid, DENIED_NODE);
    }

    /**
     * AC2 — Scénario nœud absent : parcourt tous les grades, aucun match, retourne false.
     * Représente le cas pessimiste (scan complet sans early-exit).
     * Résultat attendu : {@code false}.
     */
    @Benchmark
    public boolean resolveAbsent() {
        return PermissionResolver.resolve(grades, uuid, ABSENT_NODE);
    }
}
