package com.arcadia.customperm.config;

import java.util.Locale;

public class SettingsConfig {
    public static final String LUCKPERMS_FALLBACK_DENY = "deny";
    public static final String LUCKPERMS_FALLBACK_INTERNAL = "internal";

    /**
     * Controls what happens when LuckPerms is loaded but becomes unavailable.
     *
     * deny     = fail closed and return false for CustomPerm permission checks.
     * internal = fall back to grades.json.
     */
    public String luckPermsFallbackMode = LUCKPERMS_FALLBACK_DENY;

    public void normalize() {
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
