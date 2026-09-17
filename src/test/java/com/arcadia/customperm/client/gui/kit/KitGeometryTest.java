/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.kit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure parts of the design system: colour arithmetic, rectangle slicing, window regions and
 * icon bitmaps. Rendering itself needs a client and is checked by hand.
 */
class KitGeometryTest {

    @Test
    void tintKeepsAlphaAndMovesTowardsWhiteOrBlack() {
        int base = 0x80404040;
        assertEquals(0x80FFFFFF, Palette.tint(base, 1f));
        assertEquals(0x80000000, Palette.tint(base, -1f));
        assertEquals(base, Palette.tint(base, 0f));
        int lighter = Palette.tint(base, 0.5f);
        assertEquals(0x80, lighter >>> 24);
        assertTrue((lighter & 0xFF) > 0x40);
        // Out-of-range amounts are clamped, not wrapped.
        assertEquals(Palette.tint(base, 1f), Palette.tint(base, 7f));
    }

    @Test
    void mixBlendsEveryChannelAndClampsT() {
        assertEquals(0xFF000000, Palette.mix(0xFF000000, 0x00FFFFFF, -1f));
        assertEquals(0x00FFFFFF, Palette.mix(0xFF000000, 0x00FFFFFF, 2f));
        assertEquals(0x80808080, Palette.mix(0x00000000, 0xFFFFFFFF, 0.5f) & 0xFEFEFEFE);
        assertEquals(0x40123456, Palette.withAlpha(0xFF123456, 0x40));
        assertEquals(0xFF123456, Palette.withAlpha(0x00123456, 999));
    }

    @Test
    void rectContainsIsHalfOpenSoAdjacentRectsNeverOverlap() {
        Rect a = new Rect(10, 10, 20, 10);
        Rect b = new Rect(30, 10, 20, 10);
        assertTrue(a.contains(10, 10));
        assertTrue(a.contains(29.9, 19.9));
        assertFalse(a.contains(30, 15));
        assertTrue(b.contains(30, 15));
        assertFalse(a.contains(15, 20));
    }

    @Test
    void rectSlicesNeverGoNegative() {
        Rect r = new Rect(0, 0, 50, 40);
        assertEquals(new Rect(0, 10, 50, 30), r.belowTop(10));
        assertEquals(new Rect(0, 30, 50, 10), r.bottom(10));
        assertEquals(new Rect(0, 40, 50, 0), r.belowTop(100));
        assertEquals(new Rect(45, 0, 5, 40), r.right(5));
        assertEquals(new Rect(0, 0, 45, 40), r.beforeRight(5));
        assertEquals(new Rect(30, 30, 0, 0), r.inset(30));
        assertTrue(r.inset(30).isEmpty());
        assertEquals(new Rect(15, 10, 20, 20), r.centered(20, 20));
    }

    @Test
    void windowRegionsTileTheWindowWithoutOverlap() {
        WindowLayout l = WindowLayout.of(854, 480, true);
        assertEquals(WindowLayout.MAX_WIDTH, l.window().w());
        assertEquals(WindowLayout.MAX_HEIGHT, l.window().h());
        assertTrue(l.hasSidebar());
        assertEquals(l.header().bottom(), l.sidebar().y());
        assertEquals(l.sidebar().bottom(), l.footer().y());
        assertEquals(l.sidebar().right() + WindowLayout.CONTENT_PADDING, l.content().x());
        assertTrue(l.content().right() <= l.window().right() - 1);
        assertTrue(l.footer().bottom() <= l.window().bottom() - 1);
    }

    @Test
    void smallScreensShrinkTheWindowAndDropTheSidebar() {
        WindowLayout l = WindowLayout.of(280, 200, true);
        assertEquals(280 - 2 * WindowLayout.MARGIN, l.window().w());
        assertFalse(l.hasSidebar());
        assertEquals(l.window().x() + 1 + WindowLayout.CONTENT_PADDING, l.content().x());

        WindowLayout none = WindowLayout.of(854, 480, false);
        assertFalse(none.hasSidebar());
    }

    @Test
    void iconsAreDistinctAndFitTheAtlasSheet() {
        Set<Long> seen = new HashSet<>();
        for (Icon icon : Icon.values()) {
            assertTrue(icon.bits() != 0L, icon + " is blank");
            assertTrue(seen.add(icon.bits()), icon + " duplicates another icon");
            Atlas.Region cell = Atlas.icon(icon.ordinal());
            assertTrue(cell.u() + cell.w() <= Atlas.TEXTURE_SIZE, icon + " overflows the atlas width");
            assertTrue(cell.v() + cell.h() <= Atlas.TEXTURE_SIZE, icon + " overflows the atlas height");
        }
        assertTrue(Icon.PLUS.lit(3, 3));
        assertFalse(Icon.PLUS.lit(0, 0));
        assertFalse(Icon.PLUS.lit(-1, 3));
        assertFalse(Icon.PLUS.lit(8, 3));
    }

    @Test
    void atlasButtonStatesAndVariantsDoNotOverlap() {
        Set<String> origins = new HashSet<>();
        for (Skin.Variant variant : Skin.Variant.values()) {
            for (int state = Atlas.BUTTON_STATE_NORMAL; state <= Atlas.BUTTON_STATE_DISABLED; state++) {
                Atlas.Region r = Atlas.button(variant.row, state);
                assertTrue(origins.add(r.u() + ":" + r.v()));
                assertTrue(r.u() + r.w() <= Atlas.SCROLL_TRACK.u(), "button block runs into the scrollbar column");
                assertTrue(r.v() + r.h() <= Atlas.INPUT.v(), "button block runs into the input row");
            }
        }
    }
}
