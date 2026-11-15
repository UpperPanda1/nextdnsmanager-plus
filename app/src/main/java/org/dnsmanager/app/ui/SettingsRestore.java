package org.dnsmanager.app.ui;

/**
 * Brings the Settings window back when Android rebuilt the screen. Plain Java so the rules are
 * unit-tested (SettingsRestoreTest); MainActivity does the Android part.
 *
 * <p><b>Why it's needed:</b> Settings is a plain {@code Dialog}. Android saves and restores an
 * activity's own views, but not dialogs it shows, so after a rebuild (a theme change, the process
 * ended in the background…) the screen would come back without Settings. MainActivity writes
 * "Settings was open, at this section" into the saved state ({@code onSaveInstanceState}) and
 * reopens it in {@code onCreate}.
 * Docs: https://developer.android.com/guide/components/activities/activity-lifecycle#save-simple,-lightweight-ui-state-using-onsaveinstancestate</p>
 */
public final class SettingsRestore {

    /** Saved-state key: the open section, "" for the top; missing = Settings wasn't open. */
    public static final String KEY = "settings_open_section";

    private SettingsRestore() {
    }

    /** @return what to save: null if Settings is closed, else its section ("" for the top). */
    public static String toSave(boolean open, String section) {
        if (!open) {
            return null;
        }
        return section != null ? section : "";
    }

    /**
     * @return the section if it is a plain lower-case word, else null (= the top of Settings).
     *         It ends up in the page's address ({@code settings.html#accounts}), so nothing else is let through.
     */
    public static String safeSection(String section) {
        return section != null && section.matches("[a-z]{1,20}") ? section : null;
    }
}
