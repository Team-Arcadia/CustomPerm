/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Prefix and suffix text as admins write it, with {@code &} colour codes: {@code &0}-{@code &f} for the
 * sixteen colours, {@code &k}-{@code &o} for the formats, {@code &r} to reset and {@code &#RRGGBB} for any
 * colour, the forms LuckPerms prefixes use. It becomes a styled component, never a string carrying
 * section signs: a {@code §} typed into the text is dropped, so nothing reaches the client as a raw code.
 *
 * <p>The text only ever comes from an admin (a command, the interface, a config file, LuckPerms), never
 * from what a player types; this class still refuses what would break a line, since a config file can
 * carry anything.
 */
public final class LegacyText {

    /** Longest prefix or suffix, codes included: a name decoration, not a paragraph. */
    public static final int MAX_LENGTH = 64;

    private LegacyText() {
    }

    /** Why this text cannot be a prefix or suffix, or {@code null} when it can. */
    public static String problem(String text) {
        return problem(text, MAX_LENGTH);
    }

    /** Why this text cannot be decoration text of at most {@code max} characters, or {@code null}. */
    public static String problem(String text, int max) {
        if (text == null) return null;
        if (text.length() > max) return "It is " + text.length() + " characters long, " + max + " at most.";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c)) return "It contains a line break or a control character.";
            if (c == '§') return "Use & for colour codes, not the section sign.";
        }
        return null;
    }

    /** The text as a styled component; an empty one for {@code null}. */
    public static MutableComponent parse(String text) {
        MutableComponent result = Component.empty();
        if (text == null || text.isEmpty()) return result;
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' || Character.isISOControl(c)) continue;
            if (c == '&' && i + 1 < text.length()) {
                Style next = hex(text, i);
                if (next != null) {
                    flush(result, run, style);
                    style = next;
                    i += 7;
                    continue;
                }
                ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
                if (format != null) {
                    flush(result, run, style);
                    style = apply(style, format);
                    i++;
                    continue;
                }
            }
            run.append(c);
        }
        flush(result, run, style);
        return result;
    }

    /** The text without its codes, as players read it. */
    public static String plain(String text) {
        return parse(text).getString();
    }

    /** {@code &#RRGGBB} at {@code at}, as the style it sets, or {@code null} when it is not one. */
    private static Style hex(String text, int at) {
        if (at + 8 > text.length() || text.charAt(at + 1) != '#') return null;
        String digits = text.substring(at + 2, at + 8);
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) return null;
        }
        // Like a named colour, a colour clears the formats before it.
        return Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(digits, 16)));
    }

    /** Legacy semantics: a colour resets the formats, a format adds to the colour, {@code &r} resets all. */
    private static Style apply(Style style, ChatFormatting format) {
        if (format == ChatFormatting.RESET) return Style.EMPTY;
        if (format.isColor()) return Style.EMPTY.withColor(format);
        return style.applyFormat(format);
    }

    private static void flush(MutableComponent into, StringBuilder run, Style style) {
        if (run.isEmpty()) return;
        into.append(Component.literal(run.toString()).setStyle(style));
        run.setLength(0);
    }
}
