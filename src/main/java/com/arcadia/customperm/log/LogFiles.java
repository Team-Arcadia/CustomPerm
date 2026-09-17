/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Naming, retention and masking rules of the activity log, pure Java so they are unit tested without a
 * server. One JSON Lines file per tab per day: {@code admin-2026-09-17.jsonl}, {@code players-2026-09-17.jsonl}.
 */
public final class LogFiles {

    public static final String MASK = "[masked]";
    private static final String SUFFIX = ".jsonl";

    private LogFiles() {
    }

    public static String fileName(LogKind kind, LocalDate day) {
        return kind.prefix() + "-" + day + SUFFIX;
    }

    /** The day a log file of {@code kind} covers, or {@code null} for any other file. */
    public static LocalDate dayOf(LogKind kind, String fileName) {
        String prefix = kind.prefix() + "-";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(SUFFIX)) return null;
        try {
            return LocalDate.parse(fileName.substring(prefix.length(), fileName.length() - SUFFIX.length()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Files of {@code kind} in {@code dir}, newest day first. */
    public static List<Path> newestFirst(Path dir, LogKind kind) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> dayOf(kind, p.getFileName().toString()) != null)
                    .sorted(Comparator.comparing((Path p) -> dayOf(kind, p.getFileName().toString())).reversed())
                    .toList();
        }
    }

    /**
     * Deletes the log files older than {@code retentionDays} days before {@code today}; 0 keeps everything.
     * Only files named like log files are touched.
     *
     * @return the deleted files
     */
    public static List<Path> purge(Path dir, LocalDate today, int retentionDays) throws IOException {
        List<Path> deleted = new ArrayList<>();
        if (retentionDays <= 0 || !Files.isDirectory(dir)) return deleted;
        LocalDate oldestKept = today.minusDays(retentionDays - 1L);
        for (LogKind kind : LogKind.values()) {
            for (Path file : newestFirst(dir, kind)) {
                LocalDate day = dayOf(kind, file.getFileName().toString());
                if (day.isBefore(oldestKept) && Files.deleteIfExists(file)) deleted.add(file);
            }
        }
        return deleted;
    }

    /**
     * The command as it is stored in the player log: at most {@code maxLength} characters, and, when
     * masking is on and the root command is listed, the arguments replaced by {@link #MASK}. The root is
     * compared without a namespace, so {@code /minecraft:msg} is masked like {@code /msg}.
     */
    public static String maskCommand(String command, boolean masking, Collection<String> maskedRoots, int maxLength) {
        String text = command == null ? "" : command.strip();
        if (text.startsWith("/")) text = text.substring(1);
        int space = indexOfWhitespace(text);
        String root = space < 0 ? text : text.substring(0, space);
        String bareRoot = root.substring(root.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        if (masking && space >= 0 && maskedRoots.contains(bareRoot)) {
            text = root + " " + MASK;
        }
        text = "/" + text;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) return i;
        }
        return -1;
    }
}
