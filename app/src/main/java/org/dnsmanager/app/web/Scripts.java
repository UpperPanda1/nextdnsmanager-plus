package org.dnsmanager.app.web;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the JavaScript the app adds to the website's pages from {@code assets/js/}.
 *
 * <p>The scripts live in their own {@code .js} files, never inside Java strings, so they can be read,
 * checked ({@code node --check}) and tested (tools/page-checks) like any other code. Each file is
 * read once and then kept in memory.</p>
 *
 * <p><b>Adding a script:</b> put the file into {@code app/src/main/assets/js/}, add a constant here
 * and list it in {@link #ALL} (ScriptsTest checks that every listed file exists and that no file is
 * unlisted).</p>
 */
public final class Scripts {

    private static final String TAG = "Scripts";

    /** Desktop site: sets the page's width to a computer's (web/DesktopMode). */
    public static final String DESKTOP_VIEWPORT = "desktop_viewport.js";
    /** Pinch to zoom: removes the page's zoom ban (web/PageZoom). */
    public static final String ZOOM_VIEWPORT = "zoom_viewport.js";
    /** Find in page: colours the matches with the app's accent colour (ui/FindBar). */
    public static final String FIND_HIGHLIGHT = "find_highlight.js";

    public static final String[] ALL = {DESKTOP_VIEWPORT, ZOOM_VIEWPORT, FIND_HIGHLIGHT};

    static final String ASSET_DIR = "js/";

    private static final Map<String, String> cache = new HashMap<>();

    private Scripts() {
        // Only static methods.
    }

    /** @return the script's text, or "" if the file is missing (logged; nothing is injected then). */
    public static synchronized String get(Context context, String name) {
        String cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        String content;
        try (InputStream in = context.getApplicationContext().getAssets().open(ASSET_DIR + name)) {
            content = readUtf8(in);
        } catch (Exception e) {
            Log.e(TAG, "Missing injected script: " + name, e);
            return "";
        }
        cache.put(name, content);
        return content;
    }

    static String readUtf8(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
