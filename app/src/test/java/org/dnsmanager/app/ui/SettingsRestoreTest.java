package org.dnsmanager.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Reopening Settings after a rebuild ({@link SettingsRestore}). */
public class SettingsRestoreTest {

    @Test
    public void onlyAnOpenWindowIsSaved() {
        assertNull(SettingsRestore.toSave(false, "accounts"));
        assertEquals("accounts", SettingsRestore.toSave(true, "accounts"));
        assertEquals("", SettingsRestore.toSave(true, null));
    }

    @Test
    public void onlyPlainSectionNamesReachThePageAddress() {
        assertEquals("accounts", SettingsRestore.safeSection("accounts"));
        assertNull(SettingsRestore.safeSection(""));
        assertNull(SettingsRestore.safeSection(null));
        assertNull(SettingsRestore.safeSection("x\"><script>"));
        assertNull(SettingsRestore.safeSection("Accounts"));
        assertNull(SettingsRestore.safeSection("abcdefghijklmnopqrstuvwxyz"));
    }
}
