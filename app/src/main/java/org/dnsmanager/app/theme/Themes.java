package org.dnsmanager.app.theme;

import java.util.HashMap;
import java.util.Map;

/**
 * The app's two colour schemes, light and dark. <b>All colours of the app are here</b> (the native
 * top bar, menus and lock screen, and through {@link Scheme#toCss()} the Settings and About pages).
 *
 * <p>They are built around two fixed colours:</p>
 * <ul>
 *   <li>the accent {@code #0056B3} (a dark NextDNS blue; white text on it is easy to read), and</li>
 *   <li>the NextDNS website's own backgrounds: white in light mode and {@code #212529} in dark mode
 *       (read from the site's CSS, {@code [data-bs-theme=dark] --bs-body-bg}). The top bar and
 *       Android's status bar use exactly these, so the bar and the page look like one piece.</li>
 * </ul>
 * <p>The greys are the website's own grey scale (Bootstrap's gray-100…gray-900), so native parts and
 * page match. To re-colour the app (e.g. for another DNS service), change the values below; the role
 * names are explained in {@link Scheme}. ThemesTest checks that text stays readable on its
 * background (contrast).</p>
 */
public final class Themes {

    /** The accent colour, {@code #0056B3}. */
    public static final int ACCENT = 0xFF0056B3;

    public static final Scheme LIGHT = new Scheme(false, roles(
            // accent
            "primary", ACCENT, "onPrimary", 0xFFFFFFFF,
            "primaryContainer", 0xFFD6E4FF, "onPrimaryContainer", 0xFF001B3E,
            "inversePrimary", 0xFF6EA8FE,
            "secondary", 0xFF495057, "onSecondary", 0xFFFFFFFF,
            "secondaryContainer", 0xFFDCE8FA, "onSecondaryContainer", 0xFF0B2545,
            "tertiary", 0xFF0A7EA4, "onTertiary", 0xFFFFFFFF,
            "tertiaryContainer", 0xFFCFF4FC, "onTertiaryContainer", 0xFF055160,
            "error", 0xFFB02A37, "onError", 0xFFFFFFFF,
            "errorContainer", 0xFFF8D7DA, "onErrorContainer", 0xFF58151C,
            // backgrounds and text (the website's light theme)
            "background", 0xFFFFFFFF, "onBackground", 0xFF212529,
            "surface", 0xFFFFFFFF, "onSurface", 0xFF212529,
            "surfaceVariant", 0xFFE9ECEF, "onSurfaceVariant", 0xFF495057,
            "surfaceContainerLowest", 0xFFFFFFFF, "surfaceContainerLow", 0xFFF8F9FA,
            "surfaceContainer", 0xFFF1F3F5, "surfaceContainerHigh", 0xFFE9ECEF,
            "surfaceContainerHighest", 0xFFDEE2E6,
            "inverseSurface", 0xFF212529, "inverseOnSurface", 0xFFF1F3F5,
            "outline", 0xFF6C757D, "outlineVariant", 0xFFCED4DA));

    public static final Scheme DARK = new Scheme(true, roles(
            // accent (lighter blue: readable on the dark background)
            "primary", 0xFF6EA8FE, "onPrimary", 0xFF052C65,
            "primaryContainer", ACCENT, "onPrimaryContainer", 0xFFFFFFFF,
            "inversePrimary", ACCENT,
            "secondary", 0xFFADB5BD, "onSecondary", 0xFF212529,
            "secondaryContainer", 0xFF1F3A5F, "onSecondaryContainer", 0xFFD6E4FF,
            "tertiary", 0xFF6EDFF6, "onTertiary", 0xFF032830,
            "tertiaryContainer", 0xFF055160, "onTertiaryContainer", 0xFFCFF4FC,
            "error", 0xFFEA868F, "onError", 0xFF2C0B0E,
            "errorContainer", 0xFF842029, "onErrorContainer", 0xFFF8D7DA,
            // backgrounds and text (the website's dark theme: #212529)
            "background", 0xFF212529, "onBackground", 0xFFDEE2E6,
            "surface", 0xFF212529, "onSurface", 0xFFDEE2E6,
            "surfaceVariant", 0xFF343A40, "onSurfaceVariant", 0xFFADB5BD,
            "surfaceContainerLowest", 0xFF1A1D20, "surfaceContainerLow", 0xFF262B2F,
            "surfaceContainer", 0xFF2B3035, "surfaceContainerHigh", 0xFF343A40,
            "surfaceContainerHighest", 0xFF3D444B,
            "inverseSurface", 0xFFDEE2E6, "inverseOnSurface", 0xFF212529,
            "outline", 0xFF8A939B, "outlineVariant", 0xFF495057));

    private Themes() {
        // Only constants.
    }

    /** Builds a role → colour map from "name, colour, name, colour, …". */
    private static Map<String, Integer> roles(Object... pairs) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
