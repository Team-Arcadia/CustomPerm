/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Which installed mods call LuckPerms' own API ({@code net.luckperms.api}) rather than NeoForge's permission
 * API. CustomPerm answers every check made through NeoForge; a check made through LuckPerms by name gets no
 * answer from it, and without LuckPerms that mod falls back to its own default. This lists them, so a server
 * running CustomPerm alone knows which of its mods a grade cannot reach.
 *
 * <p>It reads the compiled classes of every mod file and looks for the names of both APIs, which a class
 * that calls one of them has to carry. Reading hundreds of jars takes seconds, so it runs once, off the server
 * thread, and keeps the answer: the installed mods cannot change without a restart.
 */
public final class ModCheck {

    /** How a class names LuckPerms' API, in the constant pool of every class that calls it. */
    private static final byte[] LUCKPERMS_API = "net/luckperms/api/".getBytes(StandardCharsets.US_ASCII);
    /** How a class names NeoForge's permission API. */
    private static final byte[] NEOFORGE_PERMISSIONS =
            "net/neoforged/neoforge/server/permission/".getBytes(StandardCharsets.US_ASCII);
    /** LuckPerms itself, CustomPerm (which calls LuckPerms only when it is there), and the game: not asked. */
    private static final Set<String> SKIPPED = Set.of("luckperms", CustomPerm.MODID, "minecraft", "neoforge");

    /** What one mod file refers to: how many of its classes name LuckPerms' API, and whether any names NeoForge's. */
    public record Usage(int luckPermsClasses, boolean neoForgePermissions) {
    }

    /** One mod file that calls LuckPerms' API: its mods, as {@code Name (id)}, and what it refers to. */
    public record Finding(String mods, Usage usage) {
    }

    /** The whole answer: the mods found, how many files were read, how long it took, and the files that failed. */
    public record Report(List<Finding> findings, int scanned, long millis, List<String> unreadable) {
    }

    private static volatile CompletableFuture<Report> running;

    private ModCheck() {
    }

    /** The report, read on the first call and kept; later calls get the same future, done or not. */
    public static synchronized CompletableFuture<Report> report() {
        if (running == null || running.isCompletedExceptionally()) {
            CompletableFuture<Report> future = new CompletableFuture<>();
            Thread worker = new Thread(() -> {
                try {
                    future.complete(scanInstalled());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            }, "CustomPerm mod check");
            worker.setDaemon(true);
            worker.start();
            running = future;
        }
        return running;
    }

    /** Whether a report is already there, so the command can say it is reading rather than go quiet. */
    public static boolean ready() {
        CompletableFuture<Report> current = running;
        return current != null && current.isDone() && !current.isCompletedExceptionally();
    }

    private static Report scanInstalled() {
        long start = System.nanoTime();
        List<Finding> findings = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        int scanned = 0;
        for (IModFileInfo file : ModList.get().getModFiles()) {
            List<IModInfo> mods = file.getMods();
            if (mods.isEmpty() || mods.stream().anyMatch(mod -> SKIPPED.contains(mod.getModId()))) continue;
            String names = String.join(", ", mods.stream()
                    .map(mod -> mod.getDisplayName() + " (" + mod.getModId() + ")").toList());
            scanned++;
            try {
                Usage usage = scan(file.getFile().getSecureJar().getRootPath());
                if (usage.luckPermsClasses() > 0) findings.add(new Finding(names, usage));
            } catch (RuntimeException e) {
                // One jar that cannot be read must not hide what the others say.
                unreadable.add(names);
                CustomPerm.LOGGER.warn("[CustomPerm] Mod check could not read {}: {}", names, e.toString());
            }
        }
        findings.sort(Comparator.comparing(Finding::mods, String.CASE_INSENSITIVE_ORDER));
        return new Report(List.copyOf(findings), scanned, (System.nanoTime() - start) / 1_000_000,
                List.copyOf(unreadable));
    }

    /** The report as chat lines: what each mod means for a server with CustomPerm alone. */
    public static List<String> lines(Report report, boolean luckPerms) {
        List<String> lines = new ArrayList<>();
        String read = report.scanned() + " mod files read in " + report.millis() + " ms";
        if (report.findings().isEmpty()) {
            lines.add("No installed mod calls LuckPerms' API directly (" + read + "): every permission check they "
                    + "make through NeoForge reaches CustomPerm.");
        } else {
            lines.add(report.findings().size() + " installed mod file(s) call LuckPerms' API directly (" + read + "):");
            for (Finding finding : report.findings()) {
                lines.add(" - " + finding.mods() + ": " + finding.usage().luckPermsClasses() + " class(es). "
                    + (finding.usage().neoForgePermissions()
                        ? "Also uses NeoForge's permission API: CustomPerm answers the checks made there."
                        : "LuckPerms only: without LuckPerms, CustomPerm cannot answer its checks."));
            }
            lines.add(luckPerms
                ? "LuckPerms is installed and answers them. This is what would stop being answered without it."
                : "Without LuckPerms, these fall back to their own default, often the operator level, whatever a "
                    + "grade grants. A mod may only use LuckPerms when it is present: its page or config says.");
        }
        if (!report.unreadable().isEmpty()) {
            lines.add("Could not be read: " + String.join("; ", report.unreadable()) + ".");
        }
        return lines;
    }

    /** What the classes under {@code root} refer to. Pure file reading, testable on a plain folder. */
    public static Usage scan(Path root) {
        int luckPerms = 0;
        boolean neoForge = false;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                byte[] bytes = Files.readAllBytes(path);
                if (contains(bytes, LUCKPERMS_API)) luckPerms++;
                if (!neoForge && contains(bytes, NEOFORGE_PERMISSIONS)) neoForge = true;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Usage(luckPerms, neoForge);
    }

    /** Whether {@code needle} occurs in {@code haystack}; a plain scan, these arrays are a few kilobytes. */
    static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
