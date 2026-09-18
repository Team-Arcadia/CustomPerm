/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.config.GradesConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One meta value carried by an import or an export plan: plain Java, like {@link ChatGrant}.
 *
 * @param expires epoch seconds, 0 for good
 * @param context the stored form of the context it is limited to, empty for everywhere
 */
public record MetaGrant(String key, String value, long expires, String context) {

    /** A holder's meta still alive at {@code now}, by key, then that limited to a context, by context. */
    public static List<MetaGrant> of(Map<String, String> meta, Map<String, Long> expiries,
                                     Map<String, ? extends GradesConfig.Scoped> scopes, long now) {
        List<MetaGrant> out = new ArrayList<>();
        add(out, meta, expiries, "", now);
        if (scopes != null) new TreeMap<>(scopes).forEach((context, scope) -> add(out, scope.meta, scope.metaExpiries, context, now));
        return List.copyOf(out);
    }

    private static void add(List<MetaGrant> out, Map<String, String> meta, Map<String, Long> expiries, String context,
                            long now) {
        if (meta == null) return;
        new TreeMap<>(meta).forEach((key, value) -> {
            Long at = expiries == null ? null : expiries.get(key);
            if (at != null && at <= now) return;
            out.add(new MetaGrant(key, value, at == null ? 0 : at, context));
        });
    }

    /** Whether the key and value can be stored here: an import leaves the others behind. */
    public boolean storable() {
        return MetaAdmin.problem(key, value) == null;
    }

    /** Adds this to a grade, in its context, unless the key is set there already; true when added. */
    public boolean addTo(GradesConfig.Grade grade) {
        if (context.isEmpty()) return addTo(grade.meta, grade.metaExpiries);
        GradesConfig.Scoped scope = Scopes.of(grade, context);
        return addTo(scope.meta, scope.metaExpiries);
    }

    /** Adds this to one player, in its context, unless the key is set there already; true when added. */
    public boolean addTo(GradesConfig config, java.util.UUID uuid) {
        if (!context.isEmpty()) {
            GradesConfig.Scoped scope = Scopes.of(config, uuid, context);
            return addTo(scope.meta, scope.metaExpiries);
        }
        Map<String, String> meta = config.userMeta.computeIfAbsent(uuid.toString(), k -> new TreeMap<>());
        Map<String, Long> expiries = config.userMetaExpiries.computeIfAbsent(uuid.toString(), k -> new HashMap<>());
        boolean added = addTo(meta, expiries);
        if (meta.isEmpty()) config.userMeta.remove(uuid.toString());
        if (expiries.isEmpty()) config.userMetaExpiries.remove(uuid.toString());
        return added;
    }

    /** A value already set here was chosen here, as a weight or a prefix is kept when an import adds. */
    private boolean addTo(Map<String, String> meta, Map<String, Long> expiries) {
        if (meta.containsKey(key)) return false;
        meta.put(key, value);
        if (expires > 0) expiries.put(key, expires);
        return true;
    }
}
