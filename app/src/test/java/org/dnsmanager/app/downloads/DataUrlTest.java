package org.dnsmanager.app.downloads;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/** Reading data: addresses ({@link DataUrl}). */
public class DataUrlTest {

    private static String text(DataUrl d) {
        return new String(d.bytes, StandardCharsets.UTF_8);
    }

    @Test
    public void theDashboardsRecoveryCodesLinkIsRead() {
        // The exact shape the dashboard builds: "data:text/plain;charset=UTF-8," + codes.join("%0A").
        DataUrl d = DataUrl.parse("data:text/plain;charset=UTF-8,abcd-1234%0Aefgh-5678");
        assertEquals("text/plain", d.mimeType);
        assertEquals("abcd-1234\nefgh-5678", text(d));
    }

    @Test
    public void plusStaysAPlusAndUtf8Survives() {
        assertEquals("a+b é", text(DataUrl.parse("data:,a+b%20%C3%A9")));
    }

    @Test
    public void base64IsDecoded() {
        DataUrl d = DataUrl.parse("data:application/json;base64,eyJhIjoxfQ==");
        assertEquals("application/json", d.mimeType);
        assertEquals("{\"a\":1}", text(d));
        assertArrayEquals(new byte[]{1, 2, 3}, DataUrl.parse("data:image/png;base64,AQID").bytes);
    }

    @Test
    public void aMissingOrOddTypeMeansText() {
        assertEquals("text/plain", DataUrl.parse("data:,x").mimeType);
        assertEquals("text/plain", DataUrl.parse("data:<script>,x").mimeType);
    }

    @Test
    public void brokenAddressesAreRefused() {
        assertNull(DataUrl.parse(null));
        assertNull(DataUrl.parse("https://my.nextdns.io/"));
        assertNull(DataUrl.parse("data:text/plain"));        // no comma
        assertNull(DataUrl.parse("data:,%zz"));              // bad escape
        assertNull(DataUrl.parse("data:,abc%4"));            // cut-off escape
        assertNull(DataUrl.parse("data:;base64,@@@"));       // bad base64
    }

    @Test
    public void extensionsFollowTheType() {
        assertEquals("txt", DataUrl.extensionFor("text/plain"));
        assertEquals("csv", DataUrl.extensionFor("text/csv"));
        assertEquals("bin", DataUrl.extensionFor("application/x-unknown"));
        assertEquals("bin", DataUrl.extensionFor(null));
    }
}
