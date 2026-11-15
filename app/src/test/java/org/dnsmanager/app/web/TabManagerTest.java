package org.dnsmanager.app.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which saved page a rebuilt screen may reopen ({@link TabManager#isStartUrl}). */
public class TabManagerTest {

    @Test
    public void theServicesHttpsPagesAreAccepted() {
        assertTrue(TabManager.isStartUrl("https://my.nextdns.io/abc123/logs"));
        assertTrue(TabManager.isStartUrl("https://my.nextdns.io"));
        assertTrue(TabManager.isStartUrl("https://my.nextdns.io?x=1#top"));
    }

    @Test
    public void everythingElseFallsBackToTheStartPage() {
        assertFalse(TabManager.isStartUrl(null));
        assertFalse(TabManager.isStartUrl("http://my.nextdns.io/"));
        assertFalse(TabManager.isStartUrl("https://example.com/"));
        assertFalse(TabManager.isStartUrl("https://evilnextdns.io/"));
        assertFalse(TabManager.isStartUrl("https://my.nextdns.io@evil.com/"));
        assertFalse(TabManager.isStartUrl("https://my.nextdns.io:8443/"));
        assertFalse(TabManager.isStartUrl("javascript:alert(1)"));
        assertFalse(TabManager.isStartUrl("about:blank"));
    }
}
