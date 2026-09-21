/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.kit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a text field proposes for what is typed in it. A proposal names the part of the text it would
 * replace, from {@code start} to the end, and the candidates for it, best first.
 *
 * <p>The source is read on every keystroke rather than copied once, so a field shows what the server
 * sent last, including after an action changed it.
 *
 * <p>Matching ignores case. A candidate starting with the typed part comes first, then one where a
 * segment after a dot, a colon, a slash, an underscore or a dash starts with it (typing {@code fly}
 * finds {@code essentials.fly}), then one that merely contains it. Within each group the source
 * order is kept. A candidate equal to the typed part is left out: there is nothing to complete.
 */
@FunctionalInterface
public interface Completer {

    /** Most candidates one proposal carries; the list scrolls past what fits on screen. */
    int LIMIT = 64;

    Proposal propose(String text);

    /** {@code start}: where the replaced part begins; {@code candidates}: best first, possibly empty. */
    record Proposal(int start, List<String> candidates) {

        public static final Proposal NONE = new Proposal(0, List.of());

        /** The text with the part from {@code start} replaced by {@code candidate}. */
        public String apply(String text, String candidate) {
            return text.substring(0, Math.min(start, text.length())) + candidate;
        }

        public boolean isEmpty() {
            return candidates.isEmpty();
        }
    }

    /** Completes the whole text against {@code source}. */
    static Completer of(Supplier<? extends Collection<String>> source) {
        return text -> new Proposal(0, match(text, source.get()));
    }

    /**
     * Completes the part after the last {@code separator}, for a list typed on one line such as
     * {@code world=the_nether,gamemode=creative}. Spaces after the separator are kept out of the match.
     */
    static Completer lastPart(char separator, Supplier<? extends Collection<String>> source) {
        return text -> {
            int start = text.lastIndexOf(separator) + 1;
            while (start < text.length() && text.charAt(start) == ' ') start++;
            return new Proposal(start, match(text.substring(start), source.get()));
        };
    }

    /**
     * Completes a command line: its first word against {@code roots}, any later word against
     * {@code arguments}. A leading slash is kept out of the match.
     */
    static Completer commandLine(Supplier<? extends Collection<String>> roots,
                                 Supplier<? extends Collection<String>> arguments) {
        return text -> {
            int space = text.lastIndexOf(' ');
            if (space < 0) {
                int start = text.startsWith("/") ? 1 : 0;
                return new Proposal(start, match(text.substring(start), roots.get()));
            }
            return new Proposal(space + 1, match(text.substring(space + 1), arguments.get()));
        };
    }

    /** The candidates of {@code source} for {@code typed}, best first, at most {@link #LIMIT}. */
    static List<String> match(String typed, Collection<String> source) {
        if (typed.isEmpty()) {
            List<String> all = new ArrayList<>();
            for (String candidate : source) {
                if (all.size() == LIMIT) break;
                if (!candidate.isEmpty() && !all.contains(candidate)) all.add(candidate);
            }
            return all;
        }
        String lower = typed.toLowerCase(Locale.ROOT);
        Set<String> starts = new LinkedHashSet<>();
        Set<String> segments = new LinkedHashSet<>();
        Set<String> contains = new LinkedHashSet<>();
        for (String candidate : source) {
            String c = candidate.toLowerCase(Locale.ROOT);
            if (c.equals(lower)) continue;
            if (c.startsWith(lower)) {
                starts.add(candidate);
                if (starts.size() == LIMIT) break;
            } else if (segmentStarts(c, lower)) {
                segments.add(candidate);
            } else if (c.contains(lower)) {
                contains.add(candidate);
            }
        }
        List<String> out = new ArrayList<>(starts);
        for (Set<String> group : List.of(segments, contains)) {
            for (String candidate : group) {
                if (out.size() == LIMIT) return out;
                out.add(candidate);
            }
        }
        return out;
    }

    private static boolean segmentStarts(String candidate, String typed) {
        for (int i = candidate.indexOf(typed, 1); i > 0; i = candidate.indexOf(typed, i + 1)) {
            char before = candidate.charAt(i - 1);
            if (before == '.' || before == ':' || before == '/' || before == '_' || before == '-' || before == '=') {
                return true;
            }
        }
        return false;
    }
}
