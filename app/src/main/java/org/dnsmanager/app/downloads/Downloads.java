package org.dnsmanager.app.downloads;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import org.dnsmanager.app.R;
import org.dnsmanager.app.site.Site;

import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Files the website offers for download. WebView doesn't download by itself: it tells the app
 * ({@code WebView.setDownloadListener}), and without this class a download button would do nothing.
 * Two kinds of address arrive:
 * <ul>
 *   <li><b>data:</b> (the file is inside the address): the dashboard's <b>2FA recovery codes</b>. Only the
 *       app can save these (the page made them; no browser could fetch them). Decoded by {@link DataUrl}
 *       and written to the phone's Downloads folder through Android's MediaStore; no storage permission
 *       is needed on Android 10+.
 *       Docs: https://developer.android.com/training/data-storage/shared/media#add-item</li>
 *   <li><b>http(s)</b> (a file on a server, e.g. the Apple configuration profile or a log export): opened
 *       in the phone's browser, which downloads it. These addresses need no login (owner, 2026-11-15), so
 *       the browser can fetch them as well as the app could, with less code here.</li>
 * </ul>
 */
public final class Downloads {

    private static final String TAG = "Downloads";

    private Downloads() {
        // Only static methods.
    }

    /**
     * Handles a download the page started. Main thread.
     *
     * @param background where a data: file is written (not on the main thread)
     */
    public static void start(Activity activity, String url, Executor background) {
        if (url != null && url.regionMatches(true, 0, "data:", 0, 5)) {
            saveDataUrl(activity, url, background);
            return;
        }
        if (!isWebAddress(url)) {
            // blob: addresses exist only inside the page; nothing outside it can read them.
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Saves a data: address as a file in Downloads. The page's own file name (its link's
     * {@code download} attribute) is not passed to the app by WebView, so the file is called
     * "NextDNS download.txt" or similar; Android adds " (1)" etc. if that name exists.
     */
    private static void saveDataUrl(Activity activity, String url, Executor background) {
        Context app = activity.getApplicationContext();
        background.execute(() -> {
            boolean ok = false;
            DataUrl file = DataUrl.parse(url);
            if (file != null) {
                ContentResolver resolver = app.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(file.mimeType));
                values.put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1); // invisible to other apps until written
                Uri item = null;
                try {
                    item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (item != null) {
                        try (OutputStream out = resolver.openOutputStream(item)) {
                            if (out != null) {
                                out.write(file.bytes);
                                ok = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Couldn't save the file", e);
                }
                if (item != null) {
                    if (ok) {
                        values.clear();
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        resolver.update(item, values, null, null);
                    } else {
                        resolver.delete(item, null, null); // no empty half-file left behind
                    }
                }
            }
            boolean saved = ok;
            activity.runOnUiThread(() -> Toast.makeText(app,
                    saved ? R.string.download_saved : R.string.download_failed, Toast.LENGTH_SHORT).show());
        });
    }

    /** @return e.g. "NextDNS download.txt" (plain Java, DownloadsTest). */
    static String fileName(String mimeType) {
        return Site.NAME + " download." + DataUrl.extensionFor(mimeType);
    }

    /** @return true for addresses a browser can open: http and https only. Plain Java (DownloadsTest). */
    public static boolean isWebAddress(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("https://") || u.startsWith("http://");
    }
}
