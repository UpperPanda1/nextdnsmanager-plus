package org.dnsmanager.app.theme;

/**
 * When a theme change rebuilds the screen ({@code Activity.recreate()}). Plain Java so the rules are
 * unit-tested (ThemeRebuildTest).
 *
 * <p><b>Why a rebuild at all:</b> the website and Android's own dialogs only switch between light and
 * dark when the screen is built again with the new mode (see {@link ThemeManager#nightOverride}).
 * The app's own colours are repainted at once, without one.</p>
 *
 * <p><b>Why these rules:</b> a rebuild closes every window on the screen, Settings included, and
 * reloads the pages. So while Settings is open the rebuild waits until it closes. And the "Settings
 * closed" code may run <i>after</i> the screen was destroyed: Android delivers a dialog's dismiss
 * listener later, as a posted message ({@code Dialog.sendDismissMessage}); a {@code recreate()} asked
 * for at that moment would rebuild the brand-new screen a second time.</p>
 */
public final class ThemeRebuild {

    private ThemeRebuild() {
        // Only static methods.
    }

    /**
     * @param appliedMode  the Theme setting the screen was built with ("system", "light", "dark")
     * @param appliedNight whether the screen was built dark
     * @param modeNow      the Theme setting now
     * @param nightNow     whether the app should be dark now
     * @return true if the screen no longer matches the setting: light/dark differs, or the screen was
     *         built "forced" and should now follow the phone (or the other way round). A forced screen
     *         ignores the phone's own day/night switch, so that difference matters even when the
     *         colours happen to be the same right now.
     */
    public static boolean isStale(String appliedMode, boolean appliedNight, String modeNow, boolean nightNow) {
        boolean wasSystem = ThemeManager.MODE_SYSTEM.equals(appliedMode);
        boolean isSystem = ThemeManager.MODE_SYSTEM.equals(modeNow);
        return appliedNight != nightNow || wasSystem != isSystem;
    }

    /** The screen came to the front: rebuild if stale, but never underneath an open Settings window. */
    public static boolean onResume(boolean stale, boolean settingsOpen) {
        return stale && !settingsOpen;
    }

    /** Settings was closed: rebuild if stale, unless the screen is already going away. */
    public static boolean onSettingsClosed(boolean stale, boolean screenGoingAway) {
        return stale && !screenGoingAway;
    }
}
