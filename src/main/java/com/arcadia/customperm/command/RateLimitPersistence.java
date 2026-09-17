/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.config.RateLimitsConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Keeps rate-limit history across restarts, in {@code <world>/data/customperm_ratelimits.json}.
 *
 * <p>The history is a server state, not a setting: it lives with the world, so config backups and
 * {@code /customperm reload} never touch it, and every singleplayer world keeps its own counters.</p>
 *
 * <p>When it is written is chosen per rule ({@code persistence} in ratelimits.json):
 * {@code world_save} (default) writes with the world, on autosave, {@code /save-all} and stop, at no
 * cost per command, and a crash loses at most the uses since the last save; {@code immediate} writes
 * after each accepted use of that command, so nothing is lost, at the price of one disk write per use.</p>
 */
public final class RateLimitPersistence {

    static final String FILE_NAME = "customperm_ratelimits.json";
    private static final DateTimeFormatter CORRUPT_SUFFIX = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private static volatile Path file;

    private RateLimitPersistence() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        file = event.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        load();
    }

    /** One write per world save: the overworld is saved on every autosave, save-all and stop. */
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            if (RateLimiter.consumeDirty()) write();
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        if (RateLimiter.consumeDirty()) write();
        RateLimiter.clearServerState();
        file = null;
    }

    /** Called after a use was accepted under {@code rule}. */
    public static void afterAcceptedUse(RateLimitsConfig.Rule rule) {
        if (rule.persistsImmediately()) {
            RateLimiter.consumeDirty();
            write();
        }
    }

    /** Where the history is stored for the running server, or null when no server is running. */
    public static Path file() {
        return file;
    }

    /**
     * Reads the history back into memory. A file that is not valid JSON is renamed aside rather than
     * overwritten at the next save, and the server starts with empty counters.
     */
    public static void load() {
        Path current = file;
        if (current == null) return;
        try {
            RateLimiter.restore(RateLimitStore.read(current), System.currentTimeMillis(), CommandTreeRewriter::rateLimitWindowMillis);
        } catch (IOException e) {
            Path aside = current.resolveSibling(FILE_NAME + ".corrupt-" + LocalDateTime.now().format(CORRUPT_SUFFIX));
            CustomPerm.LOGGER.warn("[CustomPerm] Rate-limit history is unreadable; moved to {} and starting with empty counters.",
                    aside.getFileName(), e);
            try {
                Files.move(current, aside, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                CustomPerm.LOGGER.warn("[CustomPerm] Could not move the unreadable rate-limit history aside.", moveError);
            }
            RateLimiter.restore(java.util.Map.of(), System.currentTimeMillis(), CommandTreeRewriter::rateLimitWindowMillis);
        }
    }

    /** Writes the current history now. */
    public static void write() {
        Path current = file;
        if (current == null) return;
        try {
            RateLimitStore.write(current, RateLimiter.snapshot(System.currentTimeMillis(), CommandTreeRewriter::rateLimitWindowMillis));
        } catch (IOException e) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not save rate-limit history to {}.", current, e);
        }
    }
}
