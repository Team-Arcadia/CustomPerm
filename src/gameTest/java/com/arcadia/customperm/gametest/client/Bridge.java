/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Questions a driven client asks the script that drives it, for what only the script reaches: another server's
 * console or files. The client writes {@code bridge-request.txt} as {@code seq<TAB>question}; the script answers in
 * {@code bridge-response.txt} as {@code seq<TAB>answer}. A file rather than a socket: nothing to open, nothing to leak.
 */
public final class Bridge {
    private final Path request;
    private final Path response;
    private int seq;
    private String answer;

    public Bridge(Path dir) {
        this.request = dir.resolve("bridge-request.txt");
        this.response = dir.resolve("bridge-response.txt");
    }

    /** Asks; {@link #answered()} turns true once the script replied, and {@link #answer()} holds the reply. */
    public void ask(String question) {
        seq++;
        answer = null;
        try {
            Files.writeString(request, seq + "\t" + question.replace('\n', ' '), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + request, e);
        }
    }

    public boolean answered() {
        if (answer != null) return true;
        try {
            if (!Files.isRegularFile(response)) return false;
            String text = Files.readString(response, StandardCharsets.UTF_8);
            int tab = text.indexOf('\t');
            if (tab < 0 || !text.substring(0, tab).equals(String.valueOf(seq))) return false;
            answer = text.substring(tab + 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public String answer() {
        return answer == null ? "" : answer;
    }
}
