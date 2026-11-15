package org.dnsmanager.app.storage;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

import java.util.concurrent.Executor;

/**
 * What the Settings page's "Clear cache" row can ask the app to do. Added to the settings WebView as
 * {@code AndroidStorage} (a local page only; see account/AccountsBridge for how these bridges work).
 *
 * <p>Both methods return at once and answer later by calling {@code window.onCacheSize(bytes)} in the
 * page: measuring walks through many cache files, which must not freeze the page.</p>
 */
public final class StorageBridge {

    /** Wait before measuring again after clearing: WebView deletes its cache in the background. */
    static final long MEASURE_AFTER_CLEAR_MS = 1500;

    /** What the bridge needs from the screen (implemented by MainActivity). */
    public interface Host {
        void runOnUiThread(Runnable action);

        /**
         * Main thread: empty the website cache of every account, open or closed, keeping all logins.
         * The one place that does it (MainActivity.clearAllCaches → web/Profiles.clearAllCaches); the
         * account menu's "Clear cache" icon calls the same method.
         */
        void clearAllCaches();

        /** Main thread: run a line of JavaScript in the settings page. */
        void sendToPage(String script);
    }

    private final Context context;
    private final Executor background;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());

    public StorageBridge(Context context, Executor background, Host host) {
        this.context = context.getApplicationContext();
        this.background = background;
        this.host = host;
    }

    /** Measures the cache folders; the page gets {@code onCacheSize(bytes)}. */
    @JavascriptInterface
    public void measureCache() {
        background.execute(() -> {
            long bytes = AppCache.sizeBytes(context);
            host.runOnUiThread(() -> host.sendToPage("window.onCacheSize && onCacheSize(" + bytes + ")"));
        });
    }

    /** Clears every account's website cache. Logins, accounts and settings are not touched. */
    @JavascriptInterface
    public void clearCache() {
        host.runOnUiThread(() -> {
            host.clearAllCaches();
            main.postDelayed(this::measureCache, MEASURE_AFTER_CLEAR_MS);
        });
    }
}
