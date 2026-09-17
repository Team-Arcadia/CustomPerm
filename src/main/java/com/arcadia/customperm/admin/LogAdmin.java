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

/**
 * Activity log settings shared by {@code /customperm log} and the Logs page: whether player commands
 * are recorded, and whether the arguments of sensitive commands are masked. Admin changes are always
 * recorded.
 */
public final class LogAdmin {

    private LogAdmin() {
    }

    public static AdminResult setPlayerLog(boolean enabled) {
        var settings = CustomPerm.configManager.getSettings();
        if (settings.playerCommandLog == enabled) {
            return AdminResult.ok("The player command log is already " + (enabled ? "on" : "off") + ". No change.");
        }
        settings.playerCommandLog = enabled;
        String warning = ConfigAdmin.persist();
        if (!enabled) return AdminResult.ok("Player commands are no longer recorded. Recorded entries are kept.").warn(warning);
        String retention = settings.logRetentionDays == 0 ? "without a time limit"
                : "for " + settings.logRetentionDays + " day(s)";
        return AdminResult.ok("Every command players type is now recorded, kept " + retention + ".").warn(warning)
                .note(settings.maskPlayerCommandArguments
                        ? "Arguments of " + String.join(", ", settings.maskedCommands) + " are masked."
                        : "Arguments are recorded in full, private messages included: /customperm log mask true masks them.")
                .note("A command history is personal data: let your players know it is recorded.");
    }

    public static AdminResult setMasking(boolean enabled) {
        var settings = CustomPerm.configManager.getSettings();
        if (settings.maskPlayerCommandArguments == enabled) {
            return AdminResult.ok("Argument masking is already " + (enabled ? "on" : "off") + ". No change.");
        }
        settings.maskPlayerCommandArguments = enabled;
        String warning = ConfigAdmin.persist();
        return enabled
                ? AdminResult.ok("Arguments of " + String.join(", ", settings.maskedCommands)
                        + " are masked from now on. Entries already recorded are unchanged.").warn(warning)
                : AdminResult.ok("Player commands are recorded with their full arguments from now on, private messages and "
                        + "passwords included.").warn(warning);
    }
}
