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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GradesCodecTest {

    private static final String STEVE = "8667ba71-b85a-4004-af54-457a9734eed7";
    private static final GradesCodec CODEC = new GradesCodec();

    private static GradesConfig.Grade grade(String name, String... nodes) {
        GradesConfig.Grade g = new GradesConfig.Grade();
        g.name = name;
        g.permissions.addAll(List.of(nodes));
        return g;
    }

    @Test
    void everyPublicFieldIsCarried() {
        Set<String> missing = new HashSet<>();
        for (Field field : GradesConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!GradesCodec.carriedFields().contains(field.getName())) missing.add(field.getName());
        }
        assertTrue(missing.isEmpty(), "Fields cluster mode would not share: " + missing);
    }

    @Test
    void sameContentGivesSameTextWhateverTheInsertionOrder() {
        GradesConfig a = CODEC.empty();
        GradesConfig b = CODEC.empty();
        GradesConfig.Grade ga = grade("vip");
        GradesConfig.Grade gb = grade("vip");
        for (int i = 0; i < 40; i++) ga.permissions.add("node." + i);
        for (int i = 39; i >= 0; i--) gb.permissions.add("node." + i);
        a.grades.put("vip", ga);
        b.grades.put("vip", gb);
        a.userGrades.put(STEVE, new ArrayList<>(List.of("vip")));
        b.userGrades.put(STEVE, new ArrayList<>(List.of("vip")));
        a.userMeta.put(STEVE, new HashMap<>(Map.of("a", "1", "b", "2", "c", "3")));
        Map<String, String> meta = new java.util.LinkedHashMap<>();
        meta.put("c", "3");
        meta.put("b", "2");
        meta.put("a", "1");
        b.userMeta.put(STEVE, meta);
        assertEquals(CODEC.split(a), CODEC.split(b));
    }

    @Test
    void roundTripKeepsEveryKindOfHolder() {
        GradesConfig config = CODEC.empty();
        config.grades.put("vip", grade("vip", "customperm.command.home"));
        config.grades.get("vip").weight = 10;
        config.userGrades.put(STEVE, new ArrayList<>(List.of("vip")));
        config.userNicknames.put(STEVE, "&aSteve");
        config.userPermissionExpiries.put(STEVE, new HashMap<>(Map.of("x.y", 123L)));
        config.userPermissions.put(STEVE, new HashSet<>(Set.of("x.y")));
        config.tracks.put("staff", new ArrayList<>(List.of("vip")));
        Map<String, String> rows = CODEC.split(config);
        assertEquals(Set.of("grade:vip", "player:" + STEVE, "global"), rows.keySet());

        GradesConfig copy = CODEC.empty();
        rows.forEach((holder, body) -> CODEC.patch(copy, holder, body));
        CODEC.afterPatch(copy);
        assertEquals(rows, CODEC.split(copy));
        assertEquals("vip", copy.grades.get("vip").name);
        assertEquals("&aSteve", copy.userNicknames.get(STEVE));
    }
}
