package org.dnsmanager.app.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.dnsmanager.app.R;
import org.dnsmanager.app.site.Site;

import java.util.Set;

/**
 * Setting "Pinch to zoom" (Settings → Behaviour, off by default): a two-finger pinch zooms the whole
 * page freely, like in a phone browser (the page is magnified as it is, nothing re-flows).
 *
 * <p><b>Why it needs a script:</b> the dashboard forbids zooming in its {@code <meta name="viewport">}
 * ({@code maximum-scale=1}). Android's WebView has no "force zoom" switch (unlike Chrome's
 * accessibility option; checked in Chromium's {@code AwSettings}), so
 * {@code assets/js/zoom_viewport.js} lifts the ban. It must run at <b>document start</b>, before the
 * page's own code: measured in Chromium, changing the tag after the page has loaded no longer
 * changes the zoom limits. So a tab whose setting changes is reloaded once ({@link #sync}), the same
 * as the Desktop site button does. WebView's own zoom support is switched on for every tab in
 * {@code MainActivity.configureWebView}; without the script the page's ban simply wins.</p>
 *
 * <p>One setting for all accounts (preference {@value #PREF}).</p>
 * <p>Docs: https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E)</p>
 */
public final class PageZoom {

    /** Preference: true = pinch zooms the page, false = the page keeps its own zoom ban. */
    public static final String PREF = "pinch_zoom_page";

    /** What a tab currently uses, kept on the tab's WebView itself (freed together with the tab). */
    private static final class State {
        boolean on;
        ScriptHandler script; // to remove the script when switching back
    }

    private PageZoom() {
        // Only static methods.
    }

    /** @return the saved setting. */
    public static boolean isOn(SharedPreferences prefs) {
        return prefs.getBoolean(PREF, false);
    }

    /** Saves the setting (call {@link #sync} for the open tabs afterwards). */
    public static void setOn(SharedPreferences prefs, boolean on) {
        prefs.edit().putBoolean(PREF, on).apply();
    }

    /** @return true if a pinch on this tab should zoom the page (the app's text-size pinch stays out). */
    public static boolean isActive(WebView webView) {
        Object tag = webView.getTag(R.id.tag_page_zoom);
        return tag instanceof State && ((State) tag).on;
    }

    /**
     * Sets a tab up for page zoom or text-size pinch. Call while configuring a new tab (before it loads),
     * or through {@link #sync} for a tab that is already open.
     */
    public static void apply(Context context, WebView webView, boolean on) {
        if (isActive(webView) == on) {
            return; // nothing changes (a new tab starts as "off": nothing to undo)
        }
        Object tag = webView.getTag(R.id.tag_page_zoom);
        State state = tag instanceof State ? (State) tag : new State();
        webView.setTag(R.id.tag_page_zoom, state);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (on) {
                // Only the service's own https sites get it (site/Site.originRules).
                Set<String> rules = Site.originRules();
                state.script = WebViewCompat.addDocumentStartJavaScript(webView,
                        Scripts.get(context, Scripts.ZOOM_VIEWPORT), rules);
            } else if (state.script != null) {
                state.script.remove();
                state.script = null;
            }
        }
        state.on = on;
    }

    /**
     * Makes an open tab follow the setting. A tab that changes is reloaded once: the zoom limits come
     * from the page's viewport when it loads (see the class comment).
     */
    public static void sync(Context context, WebView tab, boolean on) {
        if (tab != null && isActive(tab) != on) {
            apply(context, tab, on);
            tab.reload();
        }
    }

    /**
     * Call from the tab's onPageFinished. Only a fallback for old WebViews without document-start
     * scripts (best effort: see the class comment); with them it just re-applies, which changes nothing.
     */
    public static void onPageFinished(Context context, WebView webView) {
        if (isActive(webView)) {
            webView.evaluateJavascript(Scripts.get(context, Scripts.ZOOM_VIEWPORT), null);
        }
    }
}
