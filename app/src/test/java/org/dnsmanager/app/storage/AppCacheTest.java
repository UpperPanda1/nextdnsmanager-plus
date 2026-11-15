package org.dnsmanager.app.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Collections;

/** Checks the cache-folder rules of {@link AppCache} with real temporary folders. */
public class AppCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File file(File dir, String name, int bytes, long modified) throws Exception {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        assertTrue(f.setLastModified(modified));
        return f;
    }

    @Test
    public void sizeCountsSubFoldersButNotLinksOutOfTheFolder() throws Exception {
        File cache = tmp.newFolder("cache");
        long now = System.currentTimeMillis();
        file(cache, "shared_file_1.pdf", 100, now);
        File http = new File(cache, "WebView/Default/HTTP Cache");
        assertTrue(http.mkdirs());
        file(http, "entry", 50, now);
        File outside = tmp.newFolder("outside");
        file(outside, "big", 1000, now);
        Files.createSymbolicLink(new File(cache, "link").toPath(), outside.toPath());
        assertEquals(150, AppCache.folderSize(cache));
        assertEquals(0, AppCache.folderSize(null));
    }

    @Test
    public void aFolderReachedThroughALinkIsStillMeasured() throws Exception {
        // Android: getCacheDir() is /data/user/0/<app>/cache, and /data/user/0 is a link to /data/data.
        File real = tmp.newFolder("data");
        File app = new File(real, "app/cache");
        assertTrue(app.mkdirs());
        File sub = new File(app, "WebView");
        assertTrue(sub.mkdirs());
        file(sub, "entry", 70, System.currentTimeMillis());
        File user0 = new File(tmp.getRoot(), "user0");
        Files.createSymbolicLink(user0.toPath(), real.toPath());
        File viaLink = new File(user0, "app/cache");
        assertFalse(AppCache.isLink(new File(viaLink, "WebView")));
        assertEquals(70, AppCache.folderSize(viaLink));
    }

}
