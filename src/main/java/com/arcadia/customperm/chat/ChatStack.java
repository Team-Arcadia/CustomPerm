/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.chat;

import com.arcadia.customperm.config.SettingsConfig;

import java.util.List;

/**
 * Turns the prefixes a player reaches, highest priority first, into the text that fills {@code {prefix}}.
 * Pure Java, so the server's names and the pages' preview build it with the same code.
 */
public final class ChatStack {

    private ChatStack() {
    }

    /**
     * The first entry, or up to {@code limit} of them between the spacers when {@code stack} stacks them;
     * {@code null} when there is none, which the name format reads as nothing.
     */
    public static String format(List<String> ordered, SettingsConfig.ChatStack stack) {
        if (ordered == null || ordered.isEmpty()) return null;
        if (stack == null || !stack.stacked()) return ordered.get(0);
        StringBuilder text = new StringBuilder(stack.start);
        int shown = Math.min(ordered.size(), stack.limit);
        for (int i = 0; i < shown; i++) {
            if (i > 0) text.append(stack.middle);
            text.append(ordered.get(i));
        }
        return text.append(stack.end).toString();
    }
}
