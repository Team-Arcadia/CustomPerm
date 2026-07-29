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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Benchmark de lecture concurrente du snapshot config (story 6-3 — AR1).
 *
 * <p>Modélise le hot path de production :</p>
 * <pre>
 *   configRef.get()   ← AtomicReference volatile read (non-bloquant)
 *       ↓
 *   PermissionResolver.resolve()   ← algorithme pur Java
 * </pre>
 *
 * <p>{@code @Threads(200)} simule 200 joueurs simultanés (NFR4).
 * Le score throughput doit rester positif sans dégradation disproportionnée —
 * preuves qu'aucun {@code synchronized} ni lock ne bloque le hot path (AR1).</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(200)
public class AtomicSnapshotReadBenchmark {

    private AtomicReference<GradesConfig> ref;
    private UUID                           uuid;

    private static final String NODE = "customperm.command.fly";

    /**
     * Setup : AtomicReference pré-rempli avec un GradesConfig représentatif
     * (1 grade, 1 permission ALLOW sur NODE).
     */
    @Setup(Level.Trial)
    public void setup() {
        GradesConfig g    = new GradesConfig();
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = "bench_grade";
        grade.permissions.add(NODE);
        g.grades.put("bench_grade", grade);
        UUID u = UUID.randomUUID();
        g.userGrades.put(u.toString(), List.of("bench_grade"));
        this.uuid = u;
        this.ref  = new AtomicReference<>(g);
    }

    /**
     * AC3 — Lecture concurrente : {@code ref.get()} (volatile read, aucun lock)
     * suivi de {@code resolve()} (pur Java, stateless).
     * <p>
     * 200 threads s'exécutent simultanément. Aucun {@code synchronized} dans le
     * chemin → throughput doit scaler sans contention (AR1).
     * </p>
     */
    @Benchmark
    public boolean concurrentSnapshotRead() {
        GradesConfig grades = ref.get();                        // volatile read — no lock
        return PermissionResolver.resolve(grades, uuid, NODE);  // pure Java, stateless
    }
}
