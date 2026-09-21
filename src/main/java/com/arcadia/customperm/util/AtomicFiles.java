/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.util;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe file replacement shared by every file CustomPerm writes.
 *
 * <p>A plain {@code Files.writeString} truncates the target before writing: a server crash in between
 * leaves an empty or partial file. Writing to a temporary file in the same directory and moving it over
 * the target means a reader only ever sees the old content or the complete new content.</p>
 */
public final class AtomicFiles {

    /** Six tries, the wait doubling from 25 ms: about 0.8 s at worst, spent only while the file is held. */
    private static final int MOVE_ATTEMPTS = 6;
    private static final long MOVE_RETRY_DELAY_MS = 25L;

    private AtomicFiles() {
    }

    /** Replaces {@code target} with {@code content}, creating parent directories as needed. */
    public static void write(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(tmp, content);
            moveReplacingWithRetry(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Windows refuses to replace a file another process (an editor, an antivirus scan) holds open for a
     * moment; a retry absorbs that instead of failing the save. A scan can hold a file well past 75 ms, the
     * whole window three tries at 25 ms gave, so the wait doubles between tries.
     */
    private static void moveReplacingWithRetry(Path source, Path target) throws IOException {
        AccessDeniedException lastAccessDenied = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException e) {
                lastAccessDenied = e;
                if (attempt == MOVE_ATTEMPTS) break;
                try {
                    Thread.sleep(MOVE_RETRY_DELAY_MS << (attempt - 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying file replacement", interrupted);
                }
            }
        }
        throw lastAccessDenied;
    }
}
