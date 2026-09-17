/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of an administrative operation shared by the text commands and the admin interface.
 * The command prints {@link #message()} in chat; the interface shows it on its status line. One
 * message for both keeps the two paths from drifting into different explanations of the same refusal.
 *
 * @param success  whether the change was applied (or, for a no-op, accepted)
 * @param message  what happened, naming the object
 * @param warnings things the admin must know even though the change applied, such as a change that
 *                 could not be saved to disk; printed before the message
 * @param notes    reminders printed after the message in chat only; the interface shows the same
 *                 facts on the page itself
 */
public record AdminResult(boolean success, String message, List<String> warnings, List<String> notes) {

    public AdminResult {
        warnings = List.copyOf(warnings);
        notes = List.copyOf(notes);
    }

    public static AdminResult ok(String message) {
        return new AdminResult(true, message, List.of(), List.of());
    }

    public static AdminResult fail(String message) {
        return new AdminResult(false, message, List.of(), List.of());
    }

    /** The same result with a warning appended; {@code null} leaves it unchanged. */
    public AdminResult warn(String warning) {
        if (warning == null) return this;
        List<String> all = new ArrayList<>(warnings);
        all.add(warning);
        return new AdminResult(success, message, all, notes);
    }

    /** The same result with a chat-only note appended. */
    public AdminResult note(String note) {
        List<String> all = new ArrayList<>(notes);
        all.add(note);
        return new AdminResult(success, message, warnings, all);
    }

    /** One line for a status bar: warnings first, then the message. */
    public String summary() {
        if (warnings.isEmpty()) return message;
        return String.join(" ", warnings) + " " + message;
    }
}
