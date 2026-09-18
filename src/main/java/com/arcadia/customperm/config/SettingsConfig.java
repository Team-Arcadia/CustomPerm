/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class SettingsConfig {

    /**
     * Version of the settings format, stamped on a fresh install and after an upgrade notice. A file written
     * before 1.1.0 has no such field and reads as 0, which is how an upgrade is detected ({@link UpgradeNotice}).
     */
    public static final int CURRENT_CONFIG_VERSION = 1;

    public static final String LUCKPERMS_FALLBACK_DENY = "deny";
    public static final String LUCKPERMS_FALLBACK_INTERNAL = "internal";

    /**
     * Controls what happens when LuckPerms is loaded but becomes unavailable.
     *
     * deny     = fail closed and return false for CustomPerm permission checks.
     * internal = fall back to grades.json.
     */
    public String luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;

    /**
     * Internal backend only. {@code false}: only exposed commands read their
     * {@code customperm.command.<name>} node. {@code true}: every root command does, so an explicit DENY
     * (a denied {@code *} included) blocks any command, operators included, and an ALLOW opens any
     * command. Off by default: turning it on gives a grade holding {@code *} or
     * {@code customperm.command.*} every command of the server. No effect with LuckPerms installed,
     * which already gates every command.
     */
    public boolean gateAllCommands = false;

    /**
     * Internal grade applied to every player, below their assigned grades (like the LuckPerms
     * {@code default} group). Empty for none. This is what restricts a player made operator by mistake,
     * who has no grade of their own.
     */
    public String defaultGrade = "";

    /** The placeholder a {@link #nameFormat} must keep: a format without the name would let a prefix pass for one. */
    public static final String NAME_PLACEHOLDER = "{name}";
    public static final String DEFAULT_NAME_FORMAT = "{prefix}{name}{suffix}";
    /** Longest {@link #nameFormat}. */
    public static final int NAME_FORMAT_MAX = 128;

    /**
     * Puts each player's chat prefix and suffix around their name, from the grades or from LuckPerms.
     * Off by default: another chat mod may already decorate names. The name is decorated, never the
     * message, so chat stays signed and reportable; see {@code chat/NameDecoration}.
     */
    public boolean decorateNames = false;

    /** How the name is built: {@code {prefix}}, {@code {name}} and {@code {suffix}}, with {@code &} codes between. */
    public String nameFormat = DEFAULT_NAME_FORMAT;

    /**
     * Whether CustomPerm answers the permission checks other mods make through NeoForge's permission API.
     * It then selects its handler at start, but only when LuckPerms is not installed and
     * {@code permissionHandler} in {@code neoforge-server.toml} is still NeoForge's default: a value an admin
     * chose is never touched. Read at server start only.
     */
    public boolean answerOtherMods = true;

    /** Default {@link #maskedCommands}: private messages, and the password commands of common login mods. */
    public static final List<String> DEFAULT_MASKED_COMMANDS = List.of(
            "msg", "tell", "w", "teammsg", "tm", "login", "l", "register", "reg", "changepassword", "changepw");

    /** Days a daily activity log file is kept; 0 keeps them forever. */
    public static final int DEFAULT_LOG_RETENTION_DAYS = 30;

    /**
     * Records every command players type in the activity log (player tab). Off by default: a command
     * history is personal data. Admin changes are always recorded.
     */
    /** See {@link #CURRENT_CONFIG_VERSION}. 0 means a configuration written before 1.1.0. */
    public int configVersion = 0;

    public boolean playerCommandLog = false;

    /** Replaces the arguments of {@link #maskedCommands} with {@code [masked]} in the player log. */
    public boolean maskPlayerCommandArguments = true;

    /** Root commands whose arguments are masked, without the slash; a namespace prefix is ignored. */
    public List<String> maskedCommands = new ArrayList<>(DEFAULT_MASKED_COMMANDS);

    /** Days the activity log files are kept, 0 for no limit. */
    public int logRetentionDays = DEFAULT_LOG_RETENTION_DAYS;

    public void normalize() {
        defaultGrade = defaultGrade == null ? "" : defaultGrade.trim();
        if (maskedCommands == null) maskedCommands = new ArrayList<>(DEFAULT_MASKED_COMMANDS);
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        for (String root : maskedCommands) {
            if (root == null) continue;
            String clean = root.trim().toLowerCase(Locale.ROOT);
            if (clean.startsWith("/")) clean = clean.substring(1);
            if (!clean.isEmpty()) roots.add(clean);
        }
        maskedCommands = new ArrayList<>(roots);
        if (logRetentionDays < 0) logRetentionDays = DEFAULT_LOG_RETENTION_DAYS;
        if (configVersion < 0) configVersion = 0;
        if (nameFormat == null || !nameFormat.contains(NAME_PLACEHOLDER) || nameFormat.length() > NAME_FORMAT_MAX) {
            nameFormat = DEFAULT_NAME_FORMAT;
        }
        if (luckPermsFallbackMode == null) {
            luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;
            return;
        }

        luckPermsFallbackMode = luckPermsFallbackMode.trim().toLowerCase(Locale.ROOT);
        if (!LUCKPERMS_FALLBACK_DENY.equals(luckPermsFallbackMode)
                && !LUCKPERMS_FALLBACK_INTERNAL.equals(luckPermsFallbackMode)) {
            luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;
        }
    }

    public boolean useInternalLuckPermsFallback() {
        return LUCKPERMS_FALLBACK_INTERNAL.equals(luckPermsFallbackMode);
    }
}
