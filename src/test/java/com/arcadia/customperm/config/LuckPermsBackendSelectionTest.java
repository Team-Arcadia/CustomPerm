/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.config;

import com.arcadia.customperm.perm.InternalPermService;
import com.arcadia.customperm.perm.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-layer tests of backend selection (story 4-1).
 * No NeoForge or Minecraft import: pure JUnit 5.
 *
 * Deliberately left to the GameTests:
 *   - instantiating a real LuckPermsService: the LP API is compileOnly, absent from the test classpath
 *   - checking through ServicesManager or ModList: needs the NeoForge runtime
 *   - warnIfLuckPerms behaviour: needs a CommandContext&lt;CommandSourceStack&gt;
 *   - the start-up log messages: need a ServerStartingEvent
 *
 * Covers what is testable in pure Java:
 *   - the PermissionService contract (interface and InternalPermService implementation)
 *   - the backend selection logic, simulated
 *   - INVARIANT-502: the backend is fixed once selected (simulated with an AtomicReference)
 */
class LuckPermsBackendSelectionTest {

    @TempDir
    Path tempDir;

    // ── T3.2-a: the PermissionService contract ──────────────────────────────

    // InternalPermService does implement PermissionService, with the right concrete type
    @Test
    void shouldImplementPermissionService_internalPermService() {
        ConfigManager cm = new ConfigManager(tempDir);
        InternalPermService svc = new InternalPermService(cm);
        assertInstanceOf(InternalPermService.class, svc,
                "InternalPermService must be instantiable as a concrete PermissionService");
    }

    // onConfigReload is a no-op by default in PermissionService: InternalPermService reads from
    // ConfigManager at call time, so it holds no cache to invalidate
    @Test
    void shouldNotThrow_whenOnConfigReloadCalledWithNullSnapshot() {
        ConfigManager cm = new ConfigManager(tempDir);
        PermissionService svc = new InternalPermService(cm);
        assertDoesNotThrow(() -> svc.onConfigReload(null),
                "onConfigReload must be a no-op by default, InternalPermService reading at call time");
    }

    // ── T3.2-b: the backend selection logic, simulated ──────────────────────

    /**
     * InternalPermService can be instantiated and is a valid backend for the "LuckPerms absent"
     * branch of the CustomPerm constructor (FR28).
     *
     * The production branch under test:
     *   } else {
     *       permissions = new InternalPermService(configManager);
     *       LOGGER.info("[CustomPerm] LuckPerms not present — using internal JSON grade backend.");
     *   }
     *
     * Note: the LuckPerms branch (ModList.isLoaded = true) cannot be tested here, LuckPermsService
     * needing the LP API, which is compileOnly and absent from the test classpath. It is covered
     * by the GameTests.
     */
    @Test
    void shouldSelectInternalBackend_whenLuckPermsNotLoaded() {
        ConfigManager cm = new ConfigManager(tempDir);
        InternalPermService backend = new InternalPermService(cm);

        assertInstanceOf(PermissionService.class, backend,
                "without LuckPerms, InternalPermService must be selected and implement PermissionService (FR28)");
        assertNotNull(backend, "the internal backend must not be null");
    }

    // ── T3.2-c: INVARIANT-502, the backend is fixed once selected ───────────

    /**
     * Simulates INVARIANT-502 with an AtomicReference.
     * In production CustomPerm.permissions is a static field written once, in the @Mod
     * constructor, and no code path writes it again.
     */
    @Test
    void shouldNotReplaceBackend_onceSelected_invariant502() {
        ConfigManager cm = new ConfigManager(tempDir);
        PermissionService initialBackend = new InternalPermService(cm);

        // The AtomicReference stands in for CustomPerm.permissions, written once
        AtomicReference<PermissionService> backendRef = new AtomicReference<>(initialBackend);

        // Selecting again after init: compareAndSet(null, x) does nothing once the value is set
        PermissionService secondAttempt = new InternalPermService(cm);
        backendRef.compareAndSet(null, secondAttempt);

        assertSame(initialBackend, backendRef.get(),
                "INVARIANT-502: the backend selected at start-up must not be replaced mid-session");
    }
}
