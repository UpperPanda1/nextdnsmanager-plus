package org.dnsmanager.app.storage;

import android.content.Context;

import java.io.File;

/**
 * How big the app's cache is: the number shown in Settings → Clear cache, and the same "Cache" figure
 * Android shows in App info → Storage.
 *
 * <p><b>What's in the cache folders</b> ({@code getCacheDir()} and {@code getExternalCacheDir()}):
 * WebView's HTTP cache and compiled-code caches for every account, i.e. the website's own program
 * files, fonts and images ({@code cache/WebView/…}; Chromium {@code aw_browser_context.cc}). Logins
 * are <b>not</b> there: cookies and site storage live in {@code app_webview/}. The cache is emptied
 * through WebView (web/Profiles.clearAllCaches), never by deleting its files while WebView may be
 * using them. The app itself writes no temporary files.</p>
 *
 * <p>Only plain {@link File} code apart from the Context helper, so AppCacheTest checks it on the
 * computer with real temporary folders.</p>
 */
public final class AppCache {

    private AppCache() {
        // Only static methods.
    }

    /** @return the size in bytes of both cache folders (run on a background thread: it walks every file). */
    public static long sizeBytes(Context context) {
        return folderSize(context.getCacheDir()) + folderSize(context.getExternalCacheDir());
    }

    /** Total size of all files in a folder and its sub-folders (0 for a missing folder). */
    static long folderSize(File dir) {
        if (dir == null) {
            return 0;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return dir.isFile() ? dir.length() : 0;
        }
        long total = 0;
        for (File child : children) {
            // Don't follow links out of the folder (a link's real path differs from its own path).
            total += child.isDirectory() ? (isLink(child) ? 0 : folderSize(child)) : child.length();
        }
        return total;
    }

    /**
     * @return true if {@code file} is a symbolic link. Compared against its <i>canonical</i> parent,
     *         because the cache folder's own path may itself go through a link on Android
     *         (/data/user/0/… → /data/data/…), which must not make every file look like a link.
     */
    static boolean isLink(File file) {
        try {
            File parent = file.getParentFile();
            File expected = parent == null ? file.getAbsoluteFile()
                    : new File(parent.getCanonicalFile(), file.getName());
            return !file.getCanonicalFile().equals(expected);
        } catch (Exception e) {
            return true;
        }
    }
}
