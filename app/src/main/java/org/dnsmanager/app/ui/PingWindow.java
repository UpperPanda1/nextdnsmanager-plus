package org.dnsmanager.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.dnsmanager.app.R;
import org.dnsmanager.app.site.Site;
import org.dnsmanager.app.theme.Scheme;
import org.dnsmanager.app.web.ExternalLinks;

/**
 * Three-dots menu → "Ping test": a full-screen window with two of the service's own diagnostic
 * pages, one above the other: the speed test ({@link Site#PING_URL}: how fast each server location
 * answers) and the connection test ({@link Site#TEST_URL}: what the service sees of this phone).
 *
 * <p>Both WebViews use WebView's default profile and need no login. They are separate from the
 * account tabs and are destroyed when the window closes.</p>
 */
public final class PingWindow {

    private PingWindow() {
        // Only static methods.
    }

    /** @return the open window (MainActivity closes it when the screen is destroyed or locked). */
    public static Dialog show(Activity activity, Scheme scheme) {
        Dialog dialog = new Dialog(activity);
        Windows.prepare(dialog);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(scheme.get(Scheme.SURFACE));
        root.setFitsSystemWindows(true); // stay clear of the status and navigation bars

        // Header: back arrow + title, like the Settings page's.
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(activity);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setImageTintList(ColorStateList.valueOf(scheme.get(Scheme.ON_SURFACE_VARIANT)));
        TypedValue ripple = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        back.setBackgroundResource(ripple.resourceId);
        back.setContentDescription(activity.getString(R.string.back));
        back.setOnClickListener(v -> dialog.dismiss());
        int touch = AccountBadge.dp(activity, 48);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(touch, touch);
        backParams.setMarginStart(AccountBadge.dp(activity, 4));
        header.addView(back, backParams);
        TextView title = new TextView(activity);
        title.setText(R.string.ping_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(scheme.get(Scheme.ON_SURFACE));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMarginStart(AccountBadge.dp(activity, 8));
        header.addView(title, titleParams);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AccountBadge.dp(activity, 64)));

        WebView ping = page(activity, dialog);
        WebView test = page(activity, dialog);
        // The speed test gets three fifths of the height, the connection test two fifths.
        root.addView(ping, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f));
        View divider = new View(activity);
        divider.setBackgroundColor(scheme.get(Scheme.OUTLINE_VARIANT));
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, AccountBadge.dp(activity, 1))));
        root.addView(test, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            // Free both pages; a WebView must leave the screen before destroy().
            root.removeView(ping);
            root.removeView(test);
            ping.destroy();
            test.destroy();
        });
        dialog.show();
        Windows.fill(dialog);
        ping.loadUrl(Site.PING_URL);
        test.loadUrl(Site.TEST_URL);
        return dialog;
    }

    @SuppressLint("SetJavaScriptEnabled") // the speed test page is a JavaScript program
    private static WebView page(Activity activity, Dialog dialog) {
        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        // These simple pages may have no dark design of their own (the connection test is bare text).
        // "Algorithmic darkening" lets WebView darken such a page itself when the app is dark; a page
        // that has a dark design is left alone.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && Site.ownsHost(uri.getHost())) {
                    return false; // the service's own pages stay in the window
                }
                ExternalLinks.open(view.getContext(), uri); // safe schemes only; no app = the tap is ignored
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                dialog.dismiss(); // the pages are dead; closing the window is the recovery
                return true;      // handled: without this Android would end the whole app
            }
        });
        return webView;
    }
}
