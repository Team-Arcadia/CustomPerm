/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.config.GradesConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code grades.json} cut into rows: {@code grade:<name>} per grade, {@code player:<uuid>} per player with every
 * {@code user*} map's entry for that player, and {@code global} for the tracks.
 *
 * <p>The player maps are found by reflection, every public {@code user*} field of type {@code Map<String, ?>}, so
 * a field added to {@link GradesConfig} later is shared without anyone remembering to add it here.
 * {@code GradesCodecTest} fails when a public field belongs to none of the three kinds.</p>
 */
public final class GradesCodec implements PartCodec<GradesConfig> {

    public static final String PART = "grades";
    public static final String GRADE = "grade:";
    public static final String PLAYER = "player:";
    public static final String GLOBAL = "global";

    /** Player field name, without the {@code user} prefix and decapitalised, to its field. */
    private static final Map<String, Field> PLAYER_FIELDS = playerFields();

    @Override
    public String part() {
        return PART;
    }

    @Override
    public Map<String, String> split(GradesConfig config) {
        Map<String, String> rows = new HashMap<>();
        config.grades.forEach((name, grade) -> {
            if (grade != null) rows.put(GRADE + name, CanonicalJson.GSON.toJson(grade));
        });
        Map<String, JsonObject> players = new HashMap<>();
        PLAYER_FIELDS.forEach((key, field) -> {
            Map<String, ?> map = read(field, config);
            if (map == null) return;
            map.forEach((uuid, value) -> {
                if (value == null || isEmpty(value)) return;
                players.computeIfAbsent(uuid, u -> new JsonObject()).add(key, CanonicalJson.GSON.toJsonTree(value));
            });
        });
        players.forEach((uuid, body) -> rows.put(PLAYER + uuid, CanonicalJson.GSON.toJson(sortedKeys(body))));
        if (config.tracks != null && !config.tracks.isEmpty()) {
            JsonObject global = new JsonObject();
            global.add("tracks", CanonicalJson.GSON.toJsonTree(config.tracks));
            rows.put(GLOBAL, CanonicalJson.GSON.toJson(global));
        }
        return rows;
    }

    @Override
    public void patch(GradesConfig config, String holder, String body) {
        if (holder.startsWith(GRADE)) {
            String name = holder.substring(GRADE.length());
            if (body == null) {
                config.grades.remove(name);
            } else {
                GradesConfig.Grade grade = CanonicalJson.GSON.fromJson(body, GradesConfig.Grade.class);
                grade.name = name;
                config.grades.put(name, grade);
            }
        } else if (holder.startsWith(PLAYER)) {
            String uuid = holder.substring(PLAYER.length());
            JsonObject object = body == null ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
            PLAYER_FIELDS.forEach((key, field) -> {
                Map<String, Object> map = writable(field, config);
                JsonElement value = object.get(key);
                if (value == null || value.isJsonNull()) {
                    map.remove(uuid);
                } else {
                    map.put(uuid, CanonicalJson.GSON.fromJson(value, valueType(field)));
                }
            });
        } else if (holder.equals(GLOBAL)) {
            config.tracks = new HashMap<>();
            if (body == null) return;
            JsonElement tracks = JsonParser.parseString(body).getAsJsonObject().get("tracks");
            if (tracks != null && tracks.isJsonObject()) {
                tracks.getAsJsonObject().entrySet().forEach(e -> {
                    List<String> rungs = new ArrayList<>();
                    e.getValue().getAsJsonArray().forEach(r -> rungs.add(r.getAsString()));
                    config.tracks.put(e.getKey(), rungs);
                });
            }
        }
    }

    @Override
    public void afterPatch(GradesConfig config) {
        config.normalize();
    }

    @Override
    public GradesConfig empty() {
        GradesConfig config = new GradesConfig();
        config.normalize();
        return config;
    }

    /** The public fields of {@link GradesConfig} this codec carries, for the test that no field is left out. */
    static Set<String> carriedFields() {
        Set<String> names = new HashSet<>();
        PLAYER_FIELDS.values().forEach(f -> names.add(f.getName()));
        names.add("grades");
        names.add("tracks");
        return Collections.unmodifiableSet(names);
    }

    private static Map<String, Field> playerFields() {
        Map<String, Field> fields = new HashMap<>();
        for (Field field : GradesConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getName().startsWith("user") || !Map.class.isAssignableFrom(field.getType())) continue;
            String rest = field.getName().substring("user".length());
            fields.put(Character.toLowerCase(rest.charAt(0)) + rest.substring(1), field);
        }
        return Collections.unmodifiableMap(fields);
    }

    private static Type valueType(Field field) {
        return ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1];
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> read(Field field, GradesConfig config) {
        try {
            return (Map<String, ?>) field.get(config);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> writable(Field field, GradesConfig config) {
        try {
            Map<String, Object> map = (Map<String, Object>) field.get(config);
            if (map == null) {
                map = new HashMap<>();
                field.set(config, map);
            }
            return map;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isEmpty(Object value) {
        if (value instanceof Map<?, ?> m) return m.isEmpty();
        if (value instanceof java.util.Collection<?> c) return c.isEmpty();
        if (value instanceof String s) return s.isEmpty();
        return false;
    }

    private static JsonObject sortedKeys(JsonObject object) {
        JsonObject sorted = new JsonObject();
        object.keySet().stream().sorted().forEach(k -> sorted.add(k, object.get(k)));
        return sorted;
    }
}
