/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.config;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    /**
     * Exposed command -> the cluster members it is exposed on; absent for every member. See {@link ServerScope}.
     * A member not listed treats the command as not exposed: it keeps its original requirement there.
     */
    public Map<String, List<String>> commandServers = new LinkedHashMap<>();

    public void normalize() {
        if (grantedCommands == null) grantedCommands = new LinkedHashSet<>();
        if (preserveOriginalRequires == null) preserveOriginalRequires = new LinkedHashMap<>();
        if (commandServers == null) commandServers = new LinkedHashMap<>();
        grantedCommands.remove(null);
        preserveOriginalRequires.values().removeIf(java.util.Objects::isNull);
        commandServers.replaceAll((name, servers) -> ServerScope.normalize(servers));
        commandServers.values().removeIf(java.util.Objects::isNull);
        // A list on a command that is not exposed would decide nothing and still take a row in a cluster store.
        commandServers.keySet().retainAll(grantedCommands);
    }

    /** Whether {@code commandName} is exposed on the server named {@code here} (null outside a cluster). */
    public boolean exposedHere(String commandName, String here) {
        return grantedCommands.contains(commandName) && ServerScope.appliesHere(commandServers.get(commandName), here);
    }

    /** The members {@code commandName} is exposed on; empty for every member. */
    public List<String> servers(String commandName) {
        List<String> servers = commandServers.get(commandName);
        return servers == null ? List.of() : servers;
    }

    public boolean shouldPreserveOriginalRequires(String commandName) {
        Boolean preserve = preserveOriginalRequires.get(commandName);
        return preserve != null && preserve;
    }

    public boolean removeCommand(String commandName) {
        boolean removed = grantedCommands.remove(commandName);
        if (removed) {
            preserveOriginalRequires.remove(commandName);
            commandServers.remove(commandName);
        }
        return removed;
    }
}
