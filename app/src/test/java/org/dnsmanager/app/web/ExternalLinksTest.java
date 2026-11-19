package org.dnsmanager.app.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which link schemes may leave the app ({@link ExternalLinks}). */
public class ExternalLinksTest {

    @Test
    public void ordinaryLinksMayLeave() {
        assertTrue(ExternalLinks.isAllowedScheme("https"));
        assertTrue(ExternalLinks.isAllowedScheme("HTTP"));
        assertTrue(ExternalLinks.isAllowedScheme("mailto"));
        assertTrue(ExternalLinks.isAllowedScheme("tel"));
    }

    @Test
    public void everythingElseIsDropped() {
        // file: would crash the app (FileUriExposedException); the others start things nobody asked for.
        for (String scheme : new String[]{"file", "content", "intent", "android-app", "javascript", "data",
                "blob", "about", "market", "sms", "", null}) {
            assertFalse("scheme " + scheme, ExternalLinks.isAllowedScheme(scheme));
        }
    }
}
