package org.dnsmanager.app.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** The colour scheme object and its CSS ({@link Scheme}). */
public class SchemeTest {

    @Test
    public void cssHasEveryRoleAndTheColourScheme() {
        String css = Themes.DARK.toCss();
        assertTrue(css.startsWith(":root{color-scheme:dark;"));
        assertTrue(css.contains("--md-surface:#212529;"));
        assertTrue(css.contains("--md-surface-container-high:#343A40;"));
        for (String role : Scheme.ROLES) {
            assertTrue(role, css.contains("--md-" + Scheme.kebab(role) + ":#"));
        }
        assertTrue(Themes.LIGHT.toCss().contains("color-scheme:light;"));
        assertTrue(Themes.LIGHT.toCss().contains("--md-primary:#0056B3;"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMissingRoleIsRefused() {
        Map<String, Integer> colors = new HashMap<>();
        colors.put("primary", 0xFF000000);
        new Scheme(false, colors);
    }

    @Test
    public void hexIsTheCssForm() {
        assertEquals("#0056B3", Scheme.hex(0xFF0056B3));
        assertEquals(0xFF0056B3, Scheme.parseHex("#0056b3"));
        assertEquals("surface-container-high", Scheme.kebab("surfaceContainerHigh"));
    }
}
