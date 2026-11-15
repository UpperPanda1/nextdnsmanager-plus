package org.dnsmanager.app.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Text must stay readable in both schemes ({@link Themes}): each text colour against the background
 * it is drawn on, measured with WCAG 2.1's contrast ratio (4.5:1 for normal text).
 * Formula: https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 */
public class ContrastTest {

    /** @return the WCAG contrast ratio of two colours (1 to 21). */
    public static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int argb) {
        double r = channel((argb >> 16) & 0xFF);
        double g = channel((argb >> 8) & 0xFF);
        double b = channel(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static final String[][] PAIRS = {
            {"onSurface", "surface"}, {"onSurfaceVariant", "surface"},
            {"onSurface", "surfaceContainer"}, {"onSurfaceVariant", "surfaceContainer"},
            {"onSurface", "surfaceContainerHigh"}, {"onSurfaceVariant", "surfaceContainerHigh"},
            {"onPrimary", "primary"}, {"onPrimaryContainer", "primaryContainer"},
            {"onSecondaryContainer", "secondaryContainer"}, {"primary", "surface"},
            {"primary", "surfaceContainer"}, {"error", "surfaceContainerLow"},
            {"inverseOnSurface", "inverseSurface"}, {"onError", "error"},
    };

    @Test
    public void textIsReadableInBothSchemes() {
        for (Scheme scheme : new Scheme[]{Themes.LIGHT, Themes.DARK}) {
            for (String[] pair : PAIRS) {
                double r = ratio(scheme.get(pair[0]), scheme.get(pair[1]));
                assertTrue((scheme.dark ? "dark " : "light ") + pair[0] + " on " + pair[1] + ": " + r, r >= 4.5);
            }
        }
    }

    @Test
    public void theBarsMatchTheWebsite() {
        // The NextDNS website's backgrounds (its CSS: body white, [data-bs-theme=dark] --bs-body-bg #212529).
        assertEquals(0xFFFFFFFF, Themes.LIGHT.get(Scheme.SURFACE));
        assertEquals(0xFF212529, Themes.DARK.get(Scheme.SURFACE));
        assertEquals(0xFF0056B3, Themes.LIGHT.get(Scheme.PRIMARY));
    }

    @Test
    public void ratioMatchesKnownValues() {
        assertEquals(21.0, ratio(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, ratio(0xFF777777, 0xFF777777), 0.001);
    }
}
