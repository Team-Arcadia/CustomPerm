package com.example.customperm.config;

import com.example.customperm.CustomPerm;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir;
    private final Path gradesFile;
    private final Path aliasesFile;

    private GradesConfig grades = new GradesConfig();
    private AliasesConfig aliases = new AliasesConfig();

    public ConfigManager() {
        this.dir = FMLPaths.CONFIGDIR.get().resolve("customperm");
        this.gradesFile = dir.resolve("grades.json");
        this.aliasesFile = dir.resolve("aliases.json");
    }

    public void load() {
        try {
            Files.createDirectories(dir);
            if (Files.exists(gradesFile)) {
                GradesConfig parsed = GSON.fromJson(Files.readString(gradesFile), GradesConfig.class);
                if (parsed != null) grades = parsed;
            }
            if (Files.exists(aliasesFile)) {
                AliasesConfig parsed = GSON.fromJson(Files.readString(aliasesFile), AliasesConfig.class);
                if (parsed != null) aliases = parsed;
            }
            save();
        } catch (IOException e) {
            CustomPerm.LOGGER.error("[CustomPerm] Failed to load config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(dir);
            Files.writeString(gradesFile, GSON.toJson(grades));
            Files.writeString(aliasesFile, GSON.toJson(aliases));
        } catch (IOException e) {
            CustomPerm.LOGGER.error("[CustomPerm] Failed to save config", e);
        }
    }

    public GradesConfig getGrades() { return grades; }
    public AliasesConfig getAliases() { return aliases; }
}
