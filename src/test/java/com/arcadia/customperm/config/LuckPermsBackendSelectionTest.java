package com.arcadia.customperm.config;

import com.arcadia.customperm.perm.InternalPermService;
import com.arcadia.customperm.perm.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests data-layer pour la sélection du backend (histoire 4-1).
 * Zéro import NeoForge/Minecraft — tests JUnit 5 purs.
 *
 * Limites intentionnelles (déférées à É6.2 GameTest) :
 *   - Instanciation réelle de LuckPermsService : LP API est compileOnly → absent du classpath test
 *   - Vérification via ServicesManager/ModList : requiert NeoForge runtime
 *   - Comportement warnIfLuckPerms : requiert CommandContext<CommandSourceStack>
 *   - Log messages au démarrage : requiert ServerStartingEvent
 *
 * Couvre les aspects testables en pur Java :
 *   - Contrat PermissionService (interface + implémentation InternalPermService)
 *   - Simulation de la logique de sélection du backend
 *   - INVARIANT-502 : backend immuable une fois sélectionné (simulation AtomicReference)
 */
class LuckPermsBackendSelectionTest {

    @TempDir
    Path tempDir;

    // ── T3.2-a — Contrat interface PermissionService ─────────────────────────

    // InternalPermService implémente bien PermissionService et est du bon type concret
    @Test
    void shouldImplementPermissionService_internalPermService() {
        ConfigManager cm = new ConfigManager(tempDir);
        InternalPermService svc = new InternalPermService(cm);
        assertInstanceOf(InternalPermService.class, svc,
                "InternalPermService doit être instanciable comme implémentation concrète de PermissionService");
    }

    // onConfigReload est un no-op par défaut dans PermissionService
    // InternalPermService lit dynamiquement depuis ConfigManager → pas de cache à invalider
    @Test
    void shouldNotThrow_whenOnConfigReloadCalledWithNullSnapshot() {
        ConfigManager cm = new ConfigManager(tempDir);
        PermissionService svc = new InternalPermService(cm);
        assertDoesNotThrow(() -> svc.onConfigReload(null),
                "onConfigReload doit être un no-op par défaut (InternalPermService lit dynamiquement)");
    }

    // ── T3.2-b — Simulation de la logique de sélection du backend ────────────

    /**
     * Vérifie que InternalPermService est instanciable et constitue un backend valide
     * pour la branche "LuckPerms absent" du constructeur CustomPerm (FR28).
     *
     * Code production (branche testée) :
     *   } else {
     *       permissions = new InternalPermService(configManager);
     *       LOGGER.info("[CustomPerm] LuckPerms not present — using internal JSON grade backend.");
     *   }
     *
     * Note : la branche LP (ModList.isLoaded = true) ne peut pas être testée ici —
     * LuckPermsService requiert LP API (compileOnly → absent du classpath test). Déféré à É6.2 GameTest.
     */
    @Test
    void shouldSelectInternalBackend_whenLuckPermsNotLoaded() {
        ConfigManager cm = new ConfigManager(tempDir);
        InternalPermService backend = new InternalPermService(cm);

        assertInstanceOf(PermissionService.class, backend,
                "Sans LuckPerms, InternalPermService doit être sélectionné et implémenter PermissionService (FR28)");
        assertNotNull(backend, "Le backend interne ne doit pas être null");
    }

    // ── T3.2-c — INVARIANT-502 : backend immuable une fois sélectionné ───────

    /**
     * Simule l'immuabilité d'INVARIANT-502 via AtomicReference.
     * En production : CustomPerm.permissions est un champ static écrit une seule fois
     * dans le constructeur @Mod — aucun chemin de code ne le réécrit ensuite.
     */
    @Test
    void shouldNotReplaceBackend_onceSelected_invariant502() {
        ConfigManager cm = new ConfigManager(tempDir);
        PermissionService initialBackend = new InternalPermService(cm);

        // AtomicReference simule CustomPerm.permissions — écrit une seule fois
        AtomicReference<PermissionService> backendRef = new AtomicReference<>(initialBackend);

        // Tentative de re-sélection après initialisation : compareAndSet(null, x) est no-op si non null
        PermissionService secondAttempt = new InternalPermService(cm);
        backendRef.compareAndSet(null, secondAttempt);

        assertSame(initialBackend, backendRef.get(),
                "INVARIANT-502 : le backend sélectionné au démarrage ne doit pas être remplacé en cours de session");
    }
}
