package org.dnsmanager.app.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which addresses DownloadManager may fetch, and data: file names ({@link Downloads}). */
public class DownloadsTest {

    @Test
    public void onlyHttpAndHttpsAreDownloadedByAndroid() {
        assertTrue(Downloads.isDownloadable("https://apple.nextdns.io/?profile=abc"));
        assertTrue(Downloads.isDownloadable(" HTTP://example.com/file "));
        assertFalse(Downloads.isDownloadable("blob:https://my.nextdns.io/123"));
        assertFalse(Downloads.isDownloadable("data:,x"));
        assertFalse(Downloads.isDownloadable("file:///sdcard/x"));
        assertFalse(Downloads.isDownloadable(null));
    }

    @Test
    public void dataFilesGetTheServicesName() {
        assertEquals("NextDNS download.txt", Downloads.fileName("text/plain"));
    }
}
