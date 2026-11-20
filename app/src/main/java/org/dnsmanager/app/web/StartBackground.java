package org.dnsmanager.app.web;

import android.content.Context;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.dnsmanager.app.site.Site;
import org.dnsmanager.app.theme.Scheme;
import org.json.JSONObject;

/**
 * What a tab shows <b>before the dashboard has drawn itself</b>: the top bar's colour (white, or #1C1F22 in dark
 * mode) instead of a white flash.
 *
 * <p>Two white moments are covered:</p>
 * <ol>
 *   <li><b>The WebView itself.</b> A new WebView paints white until the first page arrives;
 *       {@link WebView#setBackgroundColor} changes that colour.</li>
 *   <li><b>The dashboard's start.</b> Its body is white until its code has written {@code data-bs-theme="dark"}
 *       (about 1.5 s measured in Chromium). In dark mode only, {@code assets/js/start_background.js} is added at
 *       document start and paints the dark colour during that wait; it explains when it stops.</li>
 * </ol>
 * <p>The colour is fixed when the tab is made: a theme change rebuilds the screen and its tabs
 * (theme/ThemeRebuild). Docs: https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E)</p>
 */
public final class StartBackground {

    private StartBackground() {
        // Only static methods.
    }

    /** Call while configuring a new tab, before it loads anything. */
    public static void apply(Context context, WebView webView, Scheme scheme) {
        int color = scheme.get(Scheme.TOP_BAR);
        webView.setBackgroundColor(color);
        if (!scheme.dark) {
            return; // the site starts white anyway
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            String script = call(Scripts.get(context, Scripts.START_BACKGROUND), color);
            if (script != null) {
                // Only the service's own https sites get it (site/Site.originRules).
                WebViewCompat.addDocumentStartJavaScript(webView, script, Site.originRules());
            }
        }
    }

    /**
     * @param function the script's text: a function expression taking the colour
     * @return the script called with the colour as a quoted JavaScript string, or null if the script is missing
     */
    static String call(String function, int color) {
        if (function == null || function.trim().isEmpty()) {
            return null;
        }
        return function.trim() + "(" + JSONObject.quote(Scheme.hex(color)) + ");";
    }
}
