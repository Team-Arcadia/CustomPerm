/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import com.arcadia.customperm.util.AtomicFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private final Path dir;
    private final Path legacyDir;
    private final Path gradesFile;
    private final Path aliasesFile;
    private final Path commandsFile;
    private final Path settingsFile;
    private final Path rateLimitsFile;

    private final AtomicReference<ConfigSnapshot> configRef;
    // package-private pour accès depuis les tests unitaires (même package)
    final AtomicBoolean reloading = new AtomicBoolean(false);

    /**
     * False while the files on disk hold content that could not be parsed. Saving in that
     * state would overwrite them with the in-memory snapshot: after a failed boot load that
     * snapshot is empty, so a single admin command would wipe every grade, alias and
     * assignment the admin was one typo away from keeping. Cleared by the next successful load.
     */
    private volatile boolean diskWritable = true;

    /** What made the last load fail, for admin-facing messages; null after a successful load. */
    private volatile String lastLoadFailure;

    /** Constructeur production — chemin résolu via FMLPaths. */
    public ConfigManager() {
        this(
                FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("customperm"),
                FMLPaths.CONFIGDIR.get().resolve("customperm"));
    }

    /** Constructeur injectable pour tests unitaires (pas d'import NeoForge requis dans les tests). */
    ConfigManager(Path dir) {
        this(dir, null);
    }

    ConfigManager(Path dir, Path legacyDir) {
        this.dir = dir;
        this.legacyDir = legacyDir;
        this.gradesFile      = dir.resolve("grades.json");
        this.aliasesFile     = dir.resolve("aliases.json");
        this.commandsFile    = dir.resolve("commands.json");
        this.settingsFile    = dir.resolve("settings.json");
        this.rateLimitsFile  = dir.resolve("ratelimits.json");
        // snapshot vide initial — remplacé par load()
        this.configRef = new AtomicReference<>(
                new ConfigSnapshot(new GradesConfig(), new AliasesConfig(), new CommandsConfig(), new SettingsConfig(), new RateLimitsConfig()));
    }

    /**
     * Charge la config depuis le disque et remplace le snapshot atomiquement.
     *
     * <p>Transaction tout-ou-rien : si un seul fichier contient du JSON invalide,
     * aucune config n'est appliquée et le snapshot précédent reste intact (INVARIANT-401).</p>
     *
     * @return true si le reload a réussi, false si rejeté (reload concurrent) ou si JSON invalide
     */
    public boolean load() {
        if (!reloading.compareAndSet(false, true)) {
            LOGGER.info("[CustomPerm] Reload already in progress — request rejected");
            return false;
        }
        try {
            migrateLegacyConfigIfNeeded();
            Files.createDirectories(dir);

            GradesConfig     grades     = new GradesConfig();
            AliasesConfig    aliases    = new AliasesConfig();
            CommandsConfig   commands   = new CommandsConfig();
            SettingsConfig   settings   = new SettingsConfig();
            RateLimitsConfig rateLimits = new RateLimitsConfig();

            // Parsing avec catch individuel par fichier — INVARIANT-401 :
            // si un fichier est invalide, on retourne false AVANT configRef.set(),
            // le snapshot précédent reste intact.
            List<String> invalidFiles = new ArrayList<>();
            if (Files.exists(gradesFile)) {
                try {
                    GradesConfig parsed = GSON.fromJson(Files.readString(gradesFile), GradesConfig.class);
                    if (parsed != null) { grades = parsed; grades.normalize(); }
                    else {
                        LOGGER.warn("[CustomPerm] Configuration reload failed — grades.json is empty or null. Keeping previous config.");
                        invalidFiles.add("grades.json");
                    }
                } catch (Exception e) {
                    LOGGER.warn("[CustomPerm] Configuration reload failed — invalid JSON in grades.json. Keeping previous config.");
                    invalidFiles.add("grades.json");
                }
            }
            if (Files.exists(aliasesFile)) {
                try {
                    AliasesConfig parsed = GSON.fromJson(Files.readString(aliasesFile), AliasesConfig.class);
                    if (parsed != null) { aliases = parsed; aliases.normalize(); }
                    else {
                        LOGGER.warn("[CustomPerm] Configuration reload failed — aliases.json is empty or null. Keeping previous config.");
                        invalidFiles.add("aliases.json");
                    }
                } catch (Exception e) {
                    LOGGER.warn("[CustomPerm] Configuration reload failed — invalid JSON in aliases.json. Keeping previous config.");
                    invalidFiles.add("aliases.json");
                }
            }
            if (Files.exists(commandsFile)) {
                try {
                    CommandsConfig parsed = GSON.fromJson(Files.readString(commandsFile), CommandsConfig.class);
                    if (parsed != null) { commands = parsed; commands.normalize(); }
                    else {
                        LOGGER.warn("[CustomPerm] Configuration reload failed — commands.json is empty or null. Keeping previous config.");
                        invalidFiles.add("commands.json");
                    }
                } catch (Exception e) {
                    LOGGER.warn("[CustomPerm] Configuration reload failed — invalid JSON in commands.json. Keeping previous config.");
                    invalidFiles.add("commands.json");
                }
            }
            if (Files.exists(settingsFile)) {
                try {
                    SettingsConfig parsed = GSON.fromJson(Files.readString(settingsFile), SettingsConfig.class);
                    if (parsed != null) { settings = parsed; settings.normalize(); }
                    else {
                        LOGGER.warn("[CustomPerm] Configuration reload failed — settings.json is empty or null. Keeping previous config.");
                        invalidFiles.add("settings.json");
                    }
                } catch (Exception e) {
                    LOGGER.warn("[CustomPerm] Configuration reload failed — invalid JSON in settings.json. Keeping previous config.");
                    invalidFiles.add("settings.json");
                }
            } else {
                // No settings.json: a fresh install, not an upgrade. Stamp it so no migration notice is given.
                settings.configVersion = SettingsConfig.CURRENT_CONFIG_VERSION;
                settings.normalize();
            }
            if (Files.exists(rateLimitsFile)) {
                try {
                    RateLimitsConfig parsed = GSON.fromJson(Files.readString(rateLimitsFile), RateLimitsConfig.class);
                    if (parsed != null) { rateLimits = parsed; rateLimits.normalize(); }
                    else {
                        LOGGER.warn("[CustomPerm] Configuration reload failed — ratelimits.json is empty or null. Keeping previous config.");
                        invalidFiles.add("ratelimits.json");
                    }
                } catch (Exception e) {
                    LOGGER.warn("[CustomPerm] Configuration reload failed — invalid JSON in ratelimits.json. Keeping previous config.");
                    invalidFiles.add("ratelimits.json");
                }
            } else {
                rateLimits.normalize();
            }
            if (!invalidFiles.isEmpty()) {
                diskWritable = false;
                lastLoadFailure = "invalid or empty " + String.join(", ", invalidFiles);
                LOGGER.error("[CustomPerm] Config saves are suspended until the invalid file is fixed and "
                        + "/customperm reload succeeds, so the file on disk is not overwritten.");
                return false;
            }

            // Tous les fichiers sont valides — mise à jour atomique du snapshot
            configRef.set(new ConfigSnapshot(grades, aliases, commands, settings, rateLimits));
            diskWritable = true;
            lastLoadFailure = null;
            if (!save()) return false;
            writeBackup();   // AR10 — backup après chargement réussi
            return true;

        } catch (IOException | RuntimeException e) {
            // Any failure, not only I/O: an unexpected exception here used to leave saves enabled
            // on top of a snapshot that never reflected the disk.
            LOGGER.error("[CustomPerm] Failed to load config", e);
            diskWritable = false;
            lastLoadFailure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return false;
        } finally {
            reloading.set(false);
        }
    }

    private void migrateLegacyConfigIfNeeded() throws IOException {
        if (legacyDir == null || Files.exists(dir) || !Files.isDirectory(legacyDir)) {
            return;
        }

        Files.createDirectories(dir);
        copyLegacyConfigFile("grades.json");
        copyLegacyConfigFile("aliases.json");
        copyLegacyConfigFile("commands.json");
        copyLegacyConfigFile("settings.json");
        copyLegacyConfigFile("ratelimits.json");
        LOGGER.info("[CustomPerm] Migrated legacy config from {} to {}", legacyDir, dir);
    }

    private void copyLegacyConfigFile(String fileName) throws IOException {
        Path source = legacyDir.resolve(fileName);
        Path target = dir.resolve(fileName);
        if (Files.isRegularFile(source) && !Files.exists(target)) {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    public synchronized boolean save() {
        if (!diskWritable) {
            LOGGER.warn("[CustomPerm] Save skipped: the config on disk failed to load. "
                    + "Fix the file and run /customperm reload before making changes.");
            return false;
        }
        ConfigSnapshot snap = configRef.get();
        try {
            Files.createDirectories(dir);
            AtomicFiles.write(gradesFile,     GSON.toJson(snap.grades()));
            AtomicFiles.write(aliasesFile,    GSON.toJson(snap.aliases()));
            AtomicFiles.write(commandsFile,   GSON.toJson(snap.commands()));
            AtomicFiles.write(settingsFile,   GSON.toJson(snap.settings()));
            AtomicFiles.write(rateLimitsFile, GSON.toJson(snap.rateLimits()));
            return true;
        } catch (IOException e) {
            LOGGER.error("[CustomPerm] Failed to save config", e);
            return false;
        }
    }

    /**
     * Écrit une backup horodatée des fichiers de config dans {@code backup/}.
     * Non-fatale : un échec logge un WARN mais ne remet pas en cause le chargement.
     * Appelée uniquement après un {@link #load()} réussi.
     */
    private void writeBackup() {
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        Path backupDir = dir.resolve("backup");
        try {
            Files.createDirectories(backupDir);
            ConfigSnapshot snap = configRef.get();

            Files.writeString(backupDir.resolve("grades.json."     + timestamp + ".bak"), GSON.toJson(snap.grades()));
            Files.writeString(backupDir.resolve("aliases.json."    + timestamp + ".bak"), GSON.toJson(snap.aliases()));
            Files.writeString(backupDir.resolve("commands.json."   + timestamp + ".bak"), GSON.toJson(snap.commands()));
            Files.writeString(backupDir.resolve("settings.json."   + timestamp + ".bak"), GSON.toJson(snap.settings()));
            Files.writeString(backupDir.resolve("ratelimits.json." + timestamp + ".bak"), GSON.toJson(snap.rateLimits()));

            // Rotation AR10 : conserver les 3 dernières backups par fichier
            rotateBackups(backupDir, "grades.json");
            rotateBackups(backupDir, "aliases.json");
            rotateBackups(backupDir, "commands.json");
            rotateBackups(backupDir, "settings.json");
            rotateBackups(backupDir, "ratelimits.json");

        } catch (IOException e) {
            LOGGER.warn("[CustomPerm] Failed to write config backup: {}", e.getMessage());
            // Non-fatal : la config est chargée correctement, seul le backup a échoué
        }
    }

    /**
     * Conserve les 3 dernières backups pour {@code baseName}, supprime les plus anciennes.
     * Le tri lexicographique est équivalent au tri chronologique grâce au format ISO du timestamp.
     *
     * <p>Package-private pour accès depuis {@code ConfigManagerTest} (même package).</p>
     */
    void rotateBackups(Path backupDir, String baseName) throws IOException {
        List<Path> backups;
        try (var stream = Files.list(backupDir)) {
            backups = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(baseName + ".") && name.endsWith(".bak");
                    })
                    .sorted()   // tri lexicographique = ordre chronologique (format ISO)
                    .collect(Collectors.toList());
        }
        // Supprimer toutes sauf les 3 plus récentes
        int toDelete = backups.size() - 3;
        for (int i = 0; i < toDelete; i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }

    public boolean isReloading() { return reloading.get(); }

    /** False after a load that found an unparseable file; see {@link #diskWritable}. */
    public boolean isDiskWritable() { return diskWritable; }

    /** Reason of the last failed load, or null when the last load succeeded. */
    public String getLastLoadFailure() { return lastLoadFailure; }

    /**
     * Suspends saves when the config could not be read at all (see the mod constructor's fallback):
     * an empty snapshot that never came from disk must not overwrite the files.
     */
    public void suspendSaves(String reason) {
        diskWritable = false;
        lastLoadFailure = reason;
    }

    /** Retourne le snapshot courant — lecture atomique, jamais null. */
    public ConfigSnapshot getSnapshot() {
        return configRef.get();
    }

    // Getters de commodité — compatibles avec tous les appels existants sans modification des call-sites
    public GradesConfig     getGrades()     { return configRef.get().grades(); }
    public AliasesConfig    getAliases()    { return configRef.get().aliases(); }
    public CommandsConfig   getCommands()   { return configRef.get().commands(); }
    public SettingsConfig   getSettings()   { return configRef.get().settings(); }
    public RateLimitsConfig getRateLimits() { return configRef.get().rateLimits(); }
}
