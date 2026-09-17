/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

/**
 * Outcome of an administrative operation shared by the text commands and the admin interface.
 * The command prints {@link #message()} in chat; the interface shows it on its status line. One
 * message for both keeps the two paths from drifting into different explanations of the same refusal.
 */
public record AdminResult(boolean success, String message) {

    public static AdminResult ok(String message) {
        return new AdminResult(true, message);
    }

    public static AdminResult fail(String message) {
        return new AdminResult(false, message);
    }
}
