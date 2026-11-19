package org.dnsmanager.app.web;

import android.net.Uri;
import android.util.Log;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;


/**
 * WebViewClient for the app's own pages (Settings, About). Those pages are local HTML files from {@code assets/} shown inside a dialog.
 *
 * <p><b>Why these dialogs need a client:</b> when the WebView renderer process dies (see {@link WebViewRecovery}), a WebView
 * without a client can't handle it, so Android kills the whole app, even if the dialog isn't
 * visible. This client handles it by closing the dialog.</p>
 *
 * <p><b>Links:</b> a link opening <i>inside</i> the dialog would expose the dialog's JavaScript bridge
 * to that website. So {@link #shouldOverrideUrlLoading} sends
 * web links to the browser explicitly and only lets local content load in the dialog.</p>
 */
public class DialogWebViewClient extends WebViewClient {

    private static final String TAG = "DialogWebViewClient";

    private final Runnable onRendererGone;

    /**
     * @param onRendererGone what to do after the dead WebView has been discarded; normally
     *                       {@code dialog::dismiss}. Runs on the main thread.
     */
    public DialogWebViewClient(Runnable onRendererGone) {
        this.onRendererGone = onRendererGone;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return handleLink(view, request.getUrl());
    }

    /**
     * @return true = "the app handled it, WebView must NOT load it"; false = "load it in this WebView".
     */
    private boolean handleLink(WebView view, Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if (staysInside(scheme)) {
            return false;
        }
        if (!ExternalLinks.open(view.getContext(), uri)) {
            // javascript: is never run; other schemes are dropped (web/ExternalLinks); no app = ignored.
            Log.w(TAG, "Link with scheme " + scheme + " was not opened");
        }
        return true; // never load outside content inside the dialog
    }

    /**
     * @return true for the addresses a local page may load inside its own window: our asset pages
     *         ({@code file}), {@code about:blank}, and files the page made itself ({@code data}, {@code blob}).
     *         {@code javascript:} is <b>not</b> one of them: such a link could run code inside the window,
     *         with access to its bridge.
     */
    static boolean staysInside(String scheme) {
        switch (scheme) {
            case "file":
            case "about":
            case "data":
            case "blob":
                return true;
            default:
                return false;
        }
    }

    /**
     * Called when the renderer process behind this WebView died.
     * Returning true tells Android "handled, don't kill the app".
     */
    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Log.w(TAG, "Dialog WebView renderer gone (crashed=" + detail.didCrash() + "); closing dialog");
        WebViewRecovery.discard(view);
        if (onRendererGone != null) {
            onRendererGone.run();
        }
        return true;
    }
}
