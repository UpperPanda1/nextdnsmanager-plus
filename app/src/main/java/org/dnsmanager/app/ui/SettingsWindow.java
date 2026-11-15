package org.dnsmanager.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.dnsmanager.app.BuildConfig;
import org.dnsmanager.app.Prefs;
import org.dnsmanager.app.account.AccountsBridge;
import org.dnsmanager.app.lock.AppLock;
import org.dnsmanager.app.site.Site;
import org.dnsmanager.app.storage.StorageBridge;
import org.dnsmanager.app.theme.ThemeBridge;
import org.dnsmanager.app.theme.ThemeManager;
import org.dnsmanager.app.web.DialogWebViewClient;
import org.dnsmanager.app.web.PageZoom;
import org.dnsmanager.app.web.TabLru;
import org.json.JSONObject;

import java.util.concurrent.Executor;

/**
 * The Settings screen: a full-screen window showing the local page {@code assets/settings.html}
 * (PROJECT.md §5.5). The page holds all texts and the layout; this class opens it and gives it the
 * "bridges" it calls into the app with:
 * <ul>
 *   <li>{@code AndroidSettings} (in this file): the simple settings, links, About;</li>
 *   <li>{@code AndroidAccounts} (account/AccountsBridge): the account manager;</li>
 *   <li>{@code AndroidStorage} (storage/StorageBridge): cache size and Clear cache;</li>
 *   <li>{@code AndroidTheme} (theme/ThemeBridge): the colours.</li>
 * </ul>
 * What a setting <i>does</i> is the {@link Host}'s business (MainActivity): this class only stores
 * the value and reports the change.
 *
 * <p><b>Adding a setting:</b> a row in settings.html → a value in {@link Bridge#getSettingsJson} → a
 * setter in {@link Bridge} → if the screen must react, a method in {@link Host}. PROJECT.md §7 walks
 * through an example.</p>
 */
public final class SettingsWindow {

    private static final String TAG = "SettingsWindow";

    /** What Settings needs from the screen (implemented by MainActivity). All methods: main thread. */
    public interface Host extends AccountsBridge.Host {
        Activity activity();

        /** Empty every account's website cache (logins stay). */
        void clearAllCaches();

        /** The Theme setting changed: repaint the native parts now. */
        void onThemeChanged();

        /** "Pinch to zoom" changed: apply it to the open tabs. */
        void onPinchZoomChanged();

        void onMaxLiveTabsChanged(int maxLiveTabs);

        /**
         * The App lock switch was moved. The host asks for the phone's unlock and saves the setting
         * only if that succeeded.
         *
         * @param result gets the setting as it is afterwards, and a message for the user or null
         */
        void requestAppLock(boolean on, AppLockResult result);

        void openAbout();

        /** The window was closed (by Back, the arrow, or code). */
        void onSettingsClosed(Dialog dialog);
    }

    /** See {@link Host#requestAppLock}. */
    public interface AppLockResult {
        void done(boolean nowOn, String message);
    }

    private SettingsWindow() {
        // Only static methods.
    }

    /**
     * Opens Settings. Main thread.
     *
     * @param section the part to scroll to (an element id in settings.html, e.g. "accounts"), or null
     * @return the open window
     */
    @SuppressLint("SetJavaScriptEnabled") // only ever shows our own settings.html; links go to the browser
    public static Dialog open(Host host, Executor background, String section) {
        Activity activity = host.activity();
        Dialog dialog = new Dialog(activity);
        Windows.prepare(dialog);

        WebView webView = new WebView(activity);
        // If WebView's renderer dies, the page is dead: close the window (the user can reopen it).
        webView.setWebViewClient(new DialogWebViewClient(dialog::dismiss));
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);    // file:///android_asset/ pages still load
        ws.setAllowContentAccess(false);

        webView.addJavascriptInterface(new Bridge(host, webView, dialog), "AndroidSettings");
        webView.addJavascriptInterface(new AccountsBridge(host), "AndroidAccounts");
        webView.addJavascriptInterface(new ThemeBridge(activity), "AndroidTheme");
        webView.addJavascriptInterface(new StorageBridge(activity, background, new StorageBridge.Host() {
            @Override
            public void runOnUiThread(Runnable action) {
                host.runOnUiThread(action);
            }

            @Override
            public void clearAllCaches() {
                host.clearAllCaches();
            }

            @Override
            public void sendToPage(String script) {
                if (dialog.isShowing()) {
                    webView.evaluateJavascript(script, null);
                }
            }
        }), "AndroidStorage");

        dialog.setOnDismissListener(d -> {
            webView.destroy(); // frees the page's memory now, not whenever Java's garbage collector runs
            host.onSettingsClosed(dialog);
        });
        // Back: first ask the page (it closes an open dialog or sub-screen); only if it had nothing to
        // close does the window go.
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                webView.evaluateJavascript("window.handleBack ? handleBack() : false", handled -> {
                    if (!"true".equals(handled) && dialog.isShowing()) {
                        dialog.dismiss();
                    }
                });
            }
            return true;
        });

        String safe = SettingsRestore.safeSection(section);
        webView.loadUrl("file:///android_asset/settings.html" + (safe != null ? "#" + safe : ""));
        dialog.setContentView(webView);
        dialog.show();
        Windows.fill(dialog);
        return dialog;
    }

    /**
     * {@code AndroidSettings} in the page. Methods run on WebView's background thread, so anything
     * touching the screen goes through {@code runOnUiThread}. Every value from the page is checked
     * here; the page is ours, but the rule costs nothing.
     */
    private static final class Bridge {
        private final Host host;
        private final WebView page;
        private final Dialog dialog;

        Bridge(Host host, WebView page, Dialog dialog) {
            this.host = host;
            this.page = page;
            this.dialog = dialog;
        }

        /** Everything the page shows at start. */
        @JavascriptInterface
        public String getSettingsJson() {
            Activity activity = host.activity();
            SharedPreferences prefs = host.prefs();
            JSONObject o = new JSONObject();
            try {
                o.put("service", Site.NAME);
                o.put("app_name", String.valueOf(activity.getApplicationInfo().loadLabel(activity.getPackageManager())));
                o.put("version", BuildConfig.VERSION_NAME);
                o.put("theme_mode", ThemeManager.mode(activity));
                o.put(PageZoom.PREF, PageZoom.isOn(prefs));
                o.put(Prefs.MAX_LIVE_TABS, prefs.getInt(Prefs.MAX_LIVE_TABS, Prefs.DEFAULT_MAX_LIVE_TABS));
                o.put("max_live_tabs_limit", TabLru.MAX_LIVE_TABS);
                o.put(AppLock.PREF, AppLock.isEnabled(activity));
            } catch (Exception e) {
                Log.e(TAG, "Couldn't build the settings JSON", e);
            }
            return o.toString();
        }

        /** @return the new mode and the new colours as CSS, so the page can repaint itself at once */
        @JavascriptInterface
        public String setThemeMode(String mode) {
            Activity activity = host.activity();
            ThemeManager.setMode(activity, mode); // ignores unknown values
            host.runOnUiThread(host::onThemeChanged);
            try {
                return new JSONObject().put("mode", ThemeManager.mode(activity))
                        .put("css", ThemeManager.scheme(activity).toCss()).toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void setPinchZoomPage(boolean on) {
            PageZoom.setOn(host.prefs(), on);
            host.runOnUiThread(host::onPinchZoomChanged);
        }

        @JavascriptInterface
        public void setMaxLiveTabs(int value) {
            int clamped = Math.max(TabLru.MIN_LIVE_TABS, Math.min(TabLru.MAX_LIVE_TABS, value));
            host.prefs().edit().putInt(Prefs.MAX_LIVE_TABS, clamped).apply();
            host.runOnUiThread(() -> host.onMaxLiveTabsChanged(clamped));
        }

        /** Answers later through {@code window.onAppLock(on, message)}: the unlock sheet takes time. */
        @JavascriptInterface
        public void setAppLock(boolean on) {
            host.runOnUiThread(() -> host.requestAppLock(on, (nowOn, message) -> {
                if (dialog.isShowing()) {
                    page.evaluateJavascript("window.onAppLock && onAppLock(" + nowOn + ","
                            + (message == null ? "null" : JSONObject.quote(message)) + ")", null);
                }
            }));
        }

        @JavascriptInterface
        public void openAbout() {
            host.runOnUiThread(host::openAbout);
        }

        /** Opens one of the service's web pages in the browser, chosen by NAME (site/Site.LINKS). */
        @JavascriptInterface
        public void openLink(String name) {
            String url = Site.LINKS.get(name);
            if (url == null) {
                return;
            }
            host.runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    host.activity().startActivity(intent);
                } catch (ActivityNotFoundException | SecurityException e) {
                    Log.w(TAG, "No app can open the link");
                }
            });
        }

        @JavascriptInterface
        public void close() {
            host.runOnUiThread(dialog::dismiss);
        }
    }
}
