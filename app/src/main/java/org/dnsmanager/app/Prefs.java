package org.dnsmanager.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The app's one settings file. Every setting, the account list and the theme are stored here, so
 * there is exactly one place to look (Android keeps it as an XML file in the app's private folder).
 *
 * <p>Each feature owns its own key name as a constant in its own class (for example
 * {@code PageZoom.PREF}); this class only knows the file.</p>
 * Docs: https://developer.android.com/training/data-storage/shared-preferences
 */
public final class Prefs {

    /** File name of the settings file. Changing it would make every user lose their settings. */
    public static final String FILE = "app_prefs";

    /** How many accounts keep their page loaded at once (Settings → Behaviour). */
    public static final String MAX_LIVE_TABS = "max_live_tabs";
    public static final int DEFAULT_MAX_LIVE_TABS = 3;

    /** The account that was on screen last; it is opened again at the next start. */
    public static final String LAST_ACCOUNT = "last_account";

    private Prefs() {
        // Only static members.
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
