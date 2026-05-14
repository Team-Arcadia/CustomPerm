package com.arcadia.customperm.config;

/**
 * Snapshot immutable de la configuration CustomPerm.
 * Obtenu via ConfigManager.getSnapshot() — jamais construit directement.
 * Les sous-configs (grades, aliases, commands) sont les mêmes objets GSON-désérialisés ;
 * ne pas les muter après construction du snapshot.
 */
public record ConfigSnapshot(
        GradesConfig grades,
        AliasesConfig aliases,
        CommandsConfig commands
) {}
