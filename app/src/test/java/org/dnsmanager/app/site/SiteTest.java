package org.dnsmanager.app.site;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

/** Which pages belong to the service ({@link Site}). */
public class SiteTest {

    @Test
    public void theServiceAndItsSubdomainsAreOwned() {
        assertTrue(Site.ownsHost("nextdns.io"));
        assertTrue(Site.ownsHost("my.nextdns.io"));
        assertTrue(Site.ownsHost("api.nextdns.io"));
        assertTrue(Site.ownsHost("MY.NEXTDNS.IO"));
    }

    @Test
    public void lookalikesAndOtherSitesAreNot() {
        assertFalse(Site.ownsHost("evilnextdns.io"));
        assertFalse(Site.ownsHost("nextdns.io.evil.com"));
        assertFalse(Site.ownsHost("google.com"));
        assertFalse(Site.ownsHost(""));
        assertFalse(Site.ownsHost(null));
    }

    @Test
    public void theFixedAddressesAreTheServicesOwn() {
        for (String url : new String[]{Site.HOME_URL, Site.TEST_URL, Site.PING_URL}) {
            assertTrue(url, url.startsWith("https://"));
            String host = url.substring(8, url.indexOf('/', 8));
            assertTrue(url, Site.ownsHost(host));
        }
        for (String url : Site.LINKS.values()) {
            assertTrue(url, url.startsWith("https://"));
        }
    }

    @Test
    public void scriptRulesCoverTheDomainAndItsSubdomainsOverHttpsOnly() {
        assertEquals(new LinkedHashSet<>(Arrays.asList("https://nextdns.io", "https://*.nextdns.io")),
                Site.originRules());
    }
}
