package com.arcadia.customperm.config;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the explicit set of root command names that the admin has exposed to the
 * permission system. Only commands listed here become grantable via
 * `customperm.command.<name>`. Any command NOT listed keeps its original vanilla
 * requirement (typically op-only) and is unaffected by this mod.
 *
 * The mod is shipped empty: each server admin picks what they want to expose.
 */
public class CommandsConfig {
    public Set<String> grantedCommands = new LinkedHashSet<>();
    public Map<String, Boolean> preserveOriginalRequires = new LinkedHashMap<>();

    public void normalize() {
        if (grantedCommands == null) grantedCommands = new LinkedHashSet<>();
        if (preserveOriginalRequires == null) preserveOriginalRequires = new LinkedHashMap<>();
    }

    public boolean shouldPreserveOriginalRequires(String commandName) {
        Boolean preserve = preserveOriginalRequires.get(commandName);
        return preserve != null && preserve;
    }

    public boolean removeCommand(String commandName) {
        boolean removed = grantedCommands.remove(commandName);
        if (removed) {
            preserveOriginalRequires.remove(commandName);
        }
        return removed;
    }
}
