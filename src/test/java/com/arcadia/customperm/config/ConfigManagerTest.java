package com.arcadia.customperm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldServeConsistentSnapshot_underConcurrentReads() throws Exception {
        // Arrange
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();
        ConfigSnapshot expected = mgr.getSnapshot();

        int threadCount = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<ConfigSnapshot>> futures = new ArrayList<>();

        // Act — 50 threads lisent getSnapshot() simultanément
        for (int i = 0; i < threadCount; i++) {
            futures.add(exec.submit(mgr::getSnapshot));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS));

        // Assert — tous les threads voient la même instance (AtomicReference garantit ça)
        for (Future<ConfigSnapshot> f : futures) {
            assertSame(expected, f.get(),
                "Tous les threads doivent obtenir la même instance de snapshot");
        }
    }

    @Test
    void shouldSerializeConcurrentSaves_withoutLeavingTemporaryFiles() throws Exception {
        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load());

        int threadCount = 16;
        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(exec.submit(mgr::save));
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Toutes les sauvegardes concurrentes doivent réussir");
        }
        assertTrue(mgr.load(), "Les fichiers produits doivent rester lisibles après les sauvegardes");

        try (var stream = Files.list(tempDir)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                    "Aucun fichier temporaire ne doit rester après une sauvegarde réussie");
        }
    }

    @Test
    void shouldRejectConcurrentReload_whenReloadInProgress() throws Exception {
        // Arrange
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();

        // Simuler un reload déjà en cours en positionnant le flag manuellement
        // (accès package-private depuis le même package)
        mgr.reloading.set(true);

        // Act — tenter un second reload pendant que le premier est "en cours"
        boolean result = mgr.load();

        // Assert
        assertFalse(result, "Le reload doit être rejeté si un reload est déjà en cours");

        // Cleanup — remettre le flag à false pour ne pas bloquer d'autres opérations
        mgr.reloading.set(false);
    }

    @Test
    void shouldRetainPreviousSnapshot_afterFailedReload() throws Exception {
        // Arrange — charger une config initiale valide
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();
        ConfigSnapshot before = mgr.getSnapshot();

        // Valide que le snapshot reste stable sans appel à load()
        // (le test de rollback complet avec JSON corrompu sera ajouté en É6.1 avec Mockito)
        ConfigSnapshot after = mgr.getSnapshot();
        assertSame(before, after, "Le snapshot ne doit pas changer sans reload réussi");
    }

    // ─── Tests H1.4 : backup automatique & restauration config invalide ──────────

    @Test
    void shouldRollbackSnapshot_whenGradesJsonIsInvalid() throws Exception {
        // Arrange — charger une config initiale valide
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();
        ConfigSnapshot before = mgr.getSnapshot();

        // Corrompre grades.json avec du JSON invalide (JsonSyntaxException)
        Files.writeString(tempDir.resolve("grades.json"), "{ INVALID JSON !!!");

        // Act
        boolean result = mgr.load();

        // Assert — INVARIANT-401 : snapshot inchangé, load() retourne false
        assertFalse(result, "load() doit retourner false si un fichier JSON est invalide");
        assertSame(before, mgr.getSnapshot(),
                "Le snapshot doit rester inchangé après un reload échoué (INVARIANT-401)");
    }

    @Test
    void shouldCreateBackupFiles_afterSuccessfulLoad() throws Exception {
        // Arrange & Act — un load() réussi doit créer un .bak par fichier de config dans backup/
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();

        // Assert
        Path backupDir = tempDir.resolve("backup");
        assertTrue(Files.isDirectory(backupDir), "Le répertoire backup/ doit exister après un load réussi");

        long backupCount;
        try (var stream = Files.list(backupDir)) {
            backupCount = stream
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .count();
        }
        assertEquals(5L, backupCount,
                "Exactement 5 fichiers .bak doivent être créés (grades, aliases, commands, settings, ratelimits)");
    }

    @Test
    void shouldMigrateLegacyConfigDirectory_whenNewDirectoryDoesNotExist() throws Exception {
        Path legacyDir = tempDir.resolve("customperm");
        Path newDir = tempDir.resolve("arcadia").resolve("customperm");
        Files.createDirectories(legacyDir);
        Files.writeString(legacyDir.resolve("grades.json"),
                "{\"grades\":{\"vip\":{\"permissions\":[\"customperm.test\"]}}}");
        Files.writeString(legacyDir.resolve("aliases.json"), "{\"aliases\":{}}");
        Files.writeString(legacyDir.resolve("commands.json"), "{\"grantedCommands\":[\"spawn\"]}");
        Files.writeString(legacyDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"internal\"}");

        ConfigManager mgr = new ConfigManager(newDir, legacyDir);

        assertTrue(mgr.load(), "load() doit migrer l'ancien dossier de configuration");
        assertTrue(Files.exists(newDir.resolve("grades.json")), "grades.json doit être copié vers le nouveau dossier");
        assertTrue(Files.exists(newDir.resolve("settings.json")), "settings.json doit être copié vers le nouveau dossier");
        assertTrue(Files.exists(legacyDir.resolve("grades.json")), "la migration ne doit pas supprimer l'ancien fichier");
        assertTrue(mgr.getGrades().grades.containsKey("vip"), "la config migrée doit être chargée");
        assertTrue(mgr.getCommands().grantedCommands.contains("spawn"), "commands.json migré doit être chargé");
        assertEquals("internal", mgr.getSettings().luckPermsFallbackMode, "settings.json migré doit être chargé");
    }

    @Test
    void shouldRetainOnlyThreeBackups_whenRotationLimitExceeded() throws Exception {
        // Arrange — créer le répertoire backup/ et y placer des backups "anciennes"
        ConfigManager mgr = new ConfigManager(tempDir);
        mgr.load();   // crée le premier jeu de backups (1 bak par fichier)
        Path backupDir = tempDir.resolve("backup");

        // Ajouter 3 fausses backups supplémentaires pour grades.json (anciennes timestamps)
        Files.writeString(backupDir.resolve("grades.json.2020-01-01T00-00-01.bak"), "{}");
        Files.writeString(backupDir.resolve("grades.json.2020-01-01T00-00-02.bak"), "{}");
        Files.writeString(backupDir.resolve("grades.json.2020-01-01T00-00-03.bak"), "{}");
        // On a maintenant 4 backups pour grades.json → rotateBackups doit en supprimer 1

        // Act — appeler directement rotateBackups (package-private, même package)
        mgr.rotateBackups(backupDir, "grades.json");

        // Assert — exactement 3 backups grades.json restantes, les plus anciennes supprimées
        long gradesBackups;
        try (var stream = Files.list(backupDir)) {
            gradesBackups = stream
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("grades.json.") && n.endsWith(".bak");
                    })
                    .count();
        }
        assertEquals(3L, gradesBackups,
                "La rotation doit conserver exactement 3 backups par fichier (AR10)");
        assertFalse(Files.exists(backupDir.resolve("grades.json.2020-01-01T00-00-01.bak")),
                "La backup la plus ancienne doit être supprimée");
    }
}
