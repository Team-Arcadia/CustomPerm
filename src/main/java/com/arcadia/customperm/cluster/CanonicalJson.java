/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * JSON that reads the same for the same content: map keys and set elements written in sorted order. Two servers
 * holding the same grade must write the same text, or each would see the other's copy as a change. Lists keep
 * their order, which can mean something (a track's rungs).
 */
final class CanonicalJson {

    static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(new SortedFactory())
            .create();

    private CanonicalJson() {}

    private static final class SortedFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> raw = type.getRawType();
            boolean map = Map.class.isAssignableFrom(raw);
            boolean set = Set.class.isAssignableFrom(raw);
            if (!map && !set) return null;
            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value == null ? null : (T) sorted(value, map));
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    return delegate.read(in);
                }
            };
        }

        /** An insertion-ordered copy in sorted order; the map and collection adapters write any Map or Set. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object sorted(Object value, boolean map) {
            if (map) {
                Map<?, ?> m = (Map<?, ?>) value;
                if (m.size() < 2 || !m.keySet().stream().allMatch(k -> k instanceof Comparable)) return value;
                return new java.util.LinkedHashMap(new TreeMap(m));
            }
            Set<?> s = (Set<?>) value;
            if (s.size() < 2 || !s.stream().allMatch(e -> e instanceof Comparable)) return value;
            List list = new ArrayList(s);
            list.sort(null);
            return new java.util.LinkedHashSet(list);
        }
    }
}
