package org.dnsmanager.app.downloads;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;

import org.dnsmanager.app.R;
import org.dnsmanager.app.site.Site;

import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Files the website offers for download. WebView doesn't download by itself: it tells the app
 * ({@code WebView.setDownloadListener}), and the app saves the file into the phone's Downloads
 * folder. No storage permission is needed for that on Android 10+. Two kinds of address arrive:
 * <ul>
 *   <li><b>http(s)</b> (a file on the server, e.g. the Apple configuration profile): handed to
 *       Android's {@link DownloadManager}, which downloads in the background with the account's login
 *       cookie and shows its own notification.
 *       Docs: https://developer.android.com/reference/android/app/DownloadManager</li>
 *   <li><b>data:</b> (the file is inside the address, e.g. the 2FA recovery codes): decoded by
 *       {@link DataUrl} and written through Android's MediaStore.
 *       Docs: https://developer.android.com/training/data-storage/shared/media#add-item</li>
 * </ul>
 * (The dashboard's log export opens a separate download address, which shows up here or, if it is
 * not on the service's own domain, opens in the browser.)
 */
public final class Downloads {

    private static final String TAG = "Downloads";

    private Downloads() {
        // Only static methods.
    }

    /**
     * Starts a download. Main thread.
     *
     * @param cookies            the cookie jar of the account's tab (web/Profiles.cookieManager), so the
     *                           download is made as the signed-in user
     * @param userAgent          the tab's "who am I" text, sent along so the server sees the same client
     * @param contentDisposition the server's file-name header, if any
     * @param background         where a data: file is written (not on the main thread)
     */
    public static void start(Activity activity, CookieManager cookies, String url, String userAgent,
                             String contentDisposition, String mimeType, Executor background) {
        if (url != null && url.regionMatches(true, 0, "data:", 0, 5)) {
            saveDataUrl(activity, url, background);
            return;
        }
        if (!isDownloadable(url)) {
            // blob: addresses exist only inside the page; nothing outside it can read them.
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url.trim()));
            String cookie = cookies == null ? null : cookies.getCookie(url);
            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }
            // The name the server suggests, else the last part of the address, with a fitting extension.
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = activity.getSystemService(DownloadManager.class);
            if (manager == null) {
                throw new IllegalStateException("No DownloadManager");
            }
            manager.enqueue(request);
            Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Log.w(TAG, "Download not started", e); // the address is not logged (it may hold a token)
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show();
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

    /** @return true for addresses DownloadManager can fetch: http and https only. Plain Java (DownloadsTest). */
    public static boolean isDownloadable(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("https://") || u.startsWith("http://");
    }
}
