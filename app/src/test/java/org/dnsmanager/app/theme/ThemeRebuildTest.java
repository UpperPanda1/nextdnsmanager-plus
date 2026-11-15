package org.dnsmanager.app.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When a theme change rebuilds the screen ({@link ThemeRebuild}). */
public class ThemeRebuildTest {

    private static final String SYSTEM = ThemeManager.MODE_SYSTEM;
    private static final String LIGHT = ThemeManager.MODE_LIGHT;
    private static final String DARK = ThemeManager.MODE_DARK;

    @Test
    public void nothingChangedIsNotStale() {
        assertFalse(ThemeRebuild.isStale(SYSTEM, false, SYSTEM, false));
        assertFalse(ThemeRebuild.isStale(DARK, true, DARK, true));
    }

    @Test
    public void lightDarkFlipIsStale() {
        assertTrue(ThemeRebuild.isStale(SYSTEM, false, SYSTEM, true));  // the phone switched to dark
        assertTrue(ThemeRebuild.isStale(LIGHT, false, DARK, true));     // Settings: White → Black
    }

    @Test
    public void switchingBetweenForcedAndFollowingIsStaleEvenWithTheSameColours() {
        // Dark phone, Theme Black → Match system: the screen looks the same but must start following the phone.
        assertTrue(ThemeRebuild.isStale(DARK, true, SYSTEM, true));
        assertTrue(ThemeRebuild.isStale(SYSTEM, false, LIGHT, false));
    }

    @Test
    public void neverRebuildsUnderOpenSettingsOrAGoingScreen() {
        assertFalse(ThemeRebuild.onResume(true, true));
        assertTrue(ThemeRebuild.onResume(true, false));
        assertFalse(ThemeRebuild.onResume(false, false));
        assertFalse(ThemeRebuild.onSettingsClosed(true, true));
        assertTrue(ThemeRebuild.onSettingsClosed(true, false));
        assertFalse(ThemeRebuild.onSettingsClosed(false, false));
    }
}
