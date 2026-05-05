package com.example.customperm.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GradesConfig {
    public Map<String, Grade> grades = new HashMap<>();
    /** UUID string -> list of grade names (ordered, but order is informational only). */
    public Map<String, List<String>> userGrades = new HashMap<>();

    public static class Grade {
        public String name;
        public Set<String> permissions = new HashSet<>();
    }

    public boolean userHasPermission(UUID uuid, String node) {
        List<String> assigned = userGrades.getOrDefault(uuid.toString(), new ArrayList<>());
        for (String gradeName : assigned) {
            Grade g = grades.get(gradeName);
            if (g == null || g.permissions == null) continue;
            if (g.permissions.contains(node)) return true;
            // crude wildcard: a node "customperm.command.*" grants all customperm.command.X
            if (g.permissions.contains("*")) return true;
            int dot = node.lastIndexOf('.');
            if (dot > 0 && g.permissions.contains(node.substring(0, dot) + ".*")) return true;
        }
        return false;
    }
}
