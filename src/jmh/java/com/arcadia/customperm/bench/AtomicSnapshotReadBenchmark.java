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
 * Benchmark of concurrent reads of the config snapshot (story 6-3, AR1).
 *
 * <p>Models the production hot path:</p>
 * <pre>
 *   configRef.get()   ← AtomicReference volatile read (non-bloquant)
 *       ↓
 *   PermissionResolver.resolve()   ← algorithme pur Java
 * </pre>
 *
 * <p>{@code @Threads(200)} simule 200 joueurs simultanés (NFR4).
 * The throughput score must stay positive with no disproportionate drop, which is the
 * evidence that no {@code synchronized} and no lock sits on the hot path (AR1).</p>
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
     * Setup: an AtomicReference preloaded with a representative GradesConfig
     * (1 grade, 1 ALLOW permission on NODE).
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
     * AC3, concurrent reads: {@code ref.get()} is a volatile read, with no lock
     * suivi de {@code resolve()} (pur Java, stateless).
     * <p>
     * 200 threads run at once. With no {@code synchronized} on the path, throughput
     * must scale without contention (AR1).
     * </p>
     */
    @Benchmark
    public boolean concurrentSnapshotRead() {
        GradesConfig grades = ref.get();                        // volatile read — no lock
        return PermissionResolver.resolve(grades, uuid, NODE);  // pure Java, stateless
    }
}
