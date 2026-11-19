package org.dnsmanager.app.web;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The one place that hands a link to another app (the browser, the mail app, the dialler).
 *
 * <p><b>Why a safe list:</b> a link on a web page can use any address scheme: {@code file:},
 * {@code content:}, {@code intent:}, {@code android-app:}… Passing such an address to
 * {@code startActivity} can crash the app (for {@code file:} Android throws
 * {@code FileUriExposedException}, which no catch block for "no app found" covers) or start things the
 * user never asked for. So only the schemes a person would expect from a link are passed on; anything
 * else is dropped. Plain Java rule ({@link #isAllowedScheme}), checked by ExternalLinksTest.</p>
 */
public final class ExternalLinks {

    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("https", "http", "mailto", "tel"));

    private ExternalLinks() {
        // Only static methods.
    }

    /** @return true for the schemes that may leave the app: https, http, mailto, tel (any letter case). */
    public static boolean isAllowedScheme(String scheme) {
        return scheme != null && ALLOWED.contains(scheme.toLowerCase(Locale.ROOT));
    }

    /**
     * Opens {@code uri} in whichever app handles it. Main thread.
     *
     * @return true if another app was started; false if the scheme is not on the safe list or no app
     *         can open it (the caller decides whether to tell the user)
     */
    public static boolean open(Context context, Uri uri) {
        if (uri == null || !isAllowedScheme(uri.getScheme())) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // a non-Activity context needs this flag
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
