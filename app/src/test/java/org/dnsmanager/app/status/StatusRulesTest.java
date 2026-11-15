package org.dnsmanager.app.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dnsmanager.app.theme.ContrastTest;
import org.dnsmanager.app.theme.Scheme;
import org.dnsmanager.app.theme.Themes;
import org.junit.Test;

/** What the status icon shows ({@link StatusRules}). */
public class StatusRulesTest {

    @Test
    public void privateDnsOfTheServiceIsSecure() {
        assertEquals(StatusRules.State.SECURE, StatusRules.fromPrivateDns(true, "abc123.dns.nextdns.io"));
        assertEquals(StatusRules.State.SECURE, StatusRules.fromPrivateDns(true, "ABC123.DNS.NEXTDNS.IO"));
    }

    @Test
    public void otherPrivateDnsAndOffAreTold() {
        assertEquals(StatusRules.State.OTHER_DNS, StatusRules.fromPrivateDns(true, "dns.google"));
        assertEquals(StatusRules.State.OTHER_DNS, StatusRules.fromPrivateDns(true, null)); // "automatic"
        assertEquals(StatusRules.State.NOT_PROTECTED, StatusRules.fromPrivateDns(false, "abc.dns.nextdns.io"));
    }

    @Test
    public void theTestAnswerIsRead() {
        // The real answer format (test.nextdns.io, checked 2026-11-15).
        assertEquals(StatusRules.State.SECURE,
                StatusRules.fromTest("{\"status\":\"ok\",\"protocol\":\"DOH\",\"profile\":\"abc123\"}"));
        assertEquals(StatusRules.State.SECURE, StatusRules.fromTest("{\"status\":\"OK\",\"protocol\":\"dot\"}"));
        assertEquals(StatusRules.State.INSECURE, StatusRules.fromTest("{\"status\":\"ok\",\"protocol\":\"UDP\"}"));
        assertNull(StatusRules.fromTest("{\n\t\"status\": \"unconfigured\",\n\t\"resolver\": \"192.178.67.18\"\n}"));
        assertNull(StatusRules.fromTest("<html>"));
        assertNull(StatusRules.fromTest(""));
        assertNull(StatusRules.fromTest(null));
    }

    @Test
    public void aPositiveTestWinsOverPrivateDns() {
        // e.g. the service set up on the router: Android sees no Private DNS, the test sees the service.
        assertEquals(StatusRules.State.INSECURE,
                StatusRules.combine(StatusRules.State.NOT_PROTECTED, StatusRules.State.INSECURE));
        assertEquals(StatusRules.State.OTHER_DNS, StatusRules.combine(StatusRules.State.OTHER_DNS, null));
    }

    @Test
    public void iconColoursAreReadableOnTheTopBar() {
        for (boolean dark : new boolean[]{false, true}) {
            int bar = (dark ? Themes.DARK : Themes.LIGHT).get(Scheme.SURFACE);
            for (StatusRules.State state : StatusRules.State.values()) {
                double ratio = ContrastTest.ratio(StatusRules.color(state, dark), bar);
                // 3:1 is WCAG's minimum for icons and other non-text graphics.
                assertTrue(state + (dark ? " dark " : " light ") + ratio, ratio >= 3.0);
            }
        }
    }
}
