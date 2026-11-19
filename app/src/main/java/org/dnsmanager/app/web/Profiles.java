package org.dnsmanager.app.web;

import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.webkit.HttpCache;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.dnsmanager.app.account.Account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Account "containers" and their caches: one WebView <b>profile</b> per account (PROJECT.md §5.2, §6).
 *
 * <p><b>What a profile is:</b> a WebView profile is like a separate browser user. Each one has its
 * own cookies (= the login), local storage and HTTP cache, in its own folder. Two WebViews with
 * different profiles can be open side by side and never see each other's data, which is what lets
 * two NextDNS logins stay signed in at the same time.
 * Docs: https://developer.android.com/reference/androidx/webkit/Profile</p>
 *
 * <p><b>Which profile:</b> the account with the id {@link Account#MAIN_ID} uses WebView's
 * <i>default</i> profile; every other account uses its own, named {@code acct_<account id>}
 * ({@link Account#profileName()}).</p>
 *
 * <p><b>Older WebView versions</b> (feature {@code MULTI_PROFILE} missing; it depends on the version
 * of the "Android System WebView" app, not on the Android version): only the default profile exists,
 * so only one account is possible (account/AccountStore.canAdd).</p>
 *
 * <p><b>The cache, in one place</b> (the rules behind "Clear cache"):</p>
 * <ul>
 *   <li>Each profile has ONE cache folder that it keeps for as long as the account exists. A tab that
 *       is unloaded and loaded again, or the app being restarted, reuses the same folder, so the
 *       website's files are read from the phone instead of downloaded again and nothing is stored
 *       twice. (The app never clears the cache by itself.)</li>
 *   <li>Each profile's cache is capped at {@link #MAX_CACHE_BYTES}; WebView deletes the oldest files
 *       itself when the cap is reached ({@link #limitCache}).</li>
 *   <li>{@link #clearAllCaches} empties the cache of every account, open or not. It never touches
 *       cookies or site storage, so every account stays signed in.</li>
 *   <li>Removing an account deletes its whole profile, cache included ({@link #forget},
 *       {@link #cleanUp}), so removed accounts leave nothing behind.</li>
 * </ul>
 */
public final class Profiles {

    private static final String TAG = "Profiles";

    /** Profile names we create all start with this ({@link Account#profileName()}). */
    private static final String ACCOUNT_PREFIX = "acct_";

    /** The key used for WebView's default profile in sets of profile names. */
    public static final String DEFAULT_KEY = Profile.DEFAULT_PROFILE_NAME;

    /**
     * Upper limit for one account's HTTP cache (the website's own program files, fonts and images;
     * never logins). The dashboard needs about 1.5 MB, so 20 MB is generous. WebView additionally
     * keeps two "code caches" (compiled JavaScript) of half this size each, so one account can use
     * up to about 40 MB in all [Chromium: the code caches are 0.5 x the HTTP cache quota].
     */
    static final long MAX_CACHE_BYTES = 20L * 1024 * 1024;

    private Profiles() {
        // Only static methods.
    }

    /** @return true if this phone's WebView supports separate profiles (= more than one account). */
    public static boolean isSupported() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
    }

    /** @return the key of the account's profile for sets of profile names ({@link #DEFAULT_KEY} or its name). */
    public static String keyOf(Account account) {
        String name = account.profileName();
        return name == null ? DEFAULT_KEY : name;
    }

    /**
     * Puts a <b>brand-new</b> WebView into the account's container. Must be the first thing done with
     * the WebView (before settings, clients, {@code loadUrl} or {@code evaluateJavascript}); WebView
     * throws otherwise. TabManager calls it right after {@code new WebView(...)}.
     */
    public static void attach(WebView webView, Account account) {
        String name = account.profileName();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            if (name != null) {
                WebViewCompat.setProfile(webView, name); // creates the profile the first time
            }
            limitCache(WebViewCompat.getProfile(webView));
        }
    }

    /**
     * @return the cookie jar this WebView uses: its profile's, or the app-wide one for the default
     *         profile. Needed e.g. to send the right login cookies with a file download.
     */
    public static CookieManager cookieManager(WebView webView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                return WebViewCompat.getProfile(webView).getCookieManager();
            } catch (RuntimeException e) {
                Log.w(TAG, "No profile for WebView; using the default cookie jar");
            }
        }
        return CookieManager.getInstance();
    }

    /**
     * Signs an account out: deletes its cookies, site storage and cache. Call it <b>after</b> the
     * account's WebView was destroyed (TabManager.closeAccount). Used for "Sign out" (the account
     * stays) and for "Remove" (the account goes). Main thread only.
     *
     * <p>The catch: {@link ProfileStore#deleteProfile} refuses a profile that this app run already
     * used (androidx.webkit 1.17.1 source). So the profile is deleted if possible and otherwise its
     * contents are wiped, which signs the account out at once either way; the empty folder of a
     * <i>removed</i> account is then deleted at the next app start by {@link #cleanUp}. The default
     * profile can never be deleted, only wiped.</p>
     */
    public static void forget(Account account) {
        String name = account.profileName();
        if (name == null) {
            wipeDefault();
            return;
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore store = ProfileStore.getInstance();
            try {
                store.deleteProfile(name); // works if the profile wasn't used in this app run
                return;
            } catch (IllegalStateException inUse) {
                // Used in this app run → wipe its contents instead.
            }
            Profile profile = store.getProfile(name);
            if (profile != null) {
                wipe(profile);
            }
        }
    }

    /**
     * Start-up housekeeping, called before any account WebView is created: deletes every
     * {@code acct_} profile whose account no longer exists (removed accounts whose folder couldn't be
     * deleted while the app was running), so removed accounts never keep taking up space.
     */
    public static void cleanUp(List<Account> accounts) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Set<String> keep = new HashSet<>();
            for (Account a : accounts) {
                keep.add(keyOf(a));
            }
            ProfileStore store = ProfileStore.getInstance();
            for (String name : store.getAllProfileNames()) {
                if (name.startsWith(ACCOUNT_PREFIX) && !keep.contains(name)) {
                    try {
                        store.deleteProfile(name);
                    } catch (RuntimeException e) {
                        // Still in use (the screen was just rebuilt in the same process): next start.
                        Log.w(TAG, "Leftover profile still in use; will retry at next start");
                    }
                }
            }
        }
    }

    /**
     * Empties the HTTP cache (the website's files, <b>never</b> cookies or site storage, so nobody is
     * signed out) of <b>every</b> account: the ones whose tab is open and the ones that are closed.
     *
     * <p>How: {@code WebView.clearCache(true)} removes the cache of the WebView's own profile only
     * (Chromium {@code AwContents::ClearCache}: data type "cache", the WebView's browser context). So
     * open tabs are asked directly, and for each remaining profile a short-lived, never-shown WebView
     * is put into that profile, asked to clear, and destroyed. Main thread only.</p>
     *
     * @param openTabs     the WebViews that exist right now
     * @param openProfiles their profile keys ({@link #keyOf}); these are not cleared a second time
     */
    public static void clearAllCaches(Context context, Collection<WebView> openTabs, Set<String> openProfiles) {
        for (WebView tab : openTabs) {
            tab.clearCache(true);
        }
        List<String> closed = new ArrayList<>();
        if (!openProfiles.contains(DEFAULT_KEY)) {
            closed.add(DEFAULT_KEY);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            for (String name : ProfileStore.getInstance().getAllProfileNames()) {
                if (name.startsWith(ACCOUNT_PREFIX) && !openProfiles.contains(name)) {
                    closed.add(name);
                }
            }
        }
        for (String name : closed) {
            WebView temp = new WebView(context);
            try {
                if (!DEFAULT_KEY.equals(name)
                        && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    WebViewCompat.setProfile(temp, name);
                }
                temp.clearCache(true);
            } catch (RuntimeException e) {
                Log.w(TAG, "Couldn't clear an account's cache", e);
            } finally {
                temp.destroy();
            }
        }
    }

    /** Deletes everything the default profile stored (the {@link Account#MAIN_ID} account's data). */
    private static void wipeDefault() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Profile profile = ProfileStore.getInstance().getProfile(DEFAULT_KEY);
            if (profile != null) {
                wipe(profile);
                return;
            }
        }
        // Older WebView: the classic app-wide calls act on the default profile.
        WebStorage.getInstance().deleteAllData();
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(null);
        cookies.flush();
    }

    /** Deletes everything a profile stored: cookies, local storage, IndexedDB, cache. */
    private static void wipe(Profile profile) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                // Newer WebView: one call removes all of it (docs: WebStorageCompat.deleteBrowsingData).
                WebStorageCompat.deleteBrowsingData(profile.getWebStorage(), () -> { });
            } else {
                // Older WebView: the classic call. It leaves the HTTP cache, which holds only the
                // website's own files (scripts, images), never logins.
                profile.getWebStorage().deleteAllData();
            }
            CookieManager cookies = profile.getCookieManager();
            cookies.removeAllCookies(null);
            cookies.flush();
        }
    }

    /**
     * Caps a profile's HTTP cache at exactly {@link #MAX_CACHE_BYTES}. A fixed number on purpose: the
     * androidx docs say the default quota "may not be a stable size across sessions" and advise against
     * feeding {@code getDefaultQuotaBytes()} back into {@code setQuotaBytes} (use {@code useDefaultQuota()}
     * for "automatic"), so the old {@code min(default, cap)} could pin a moving value. WebView clamps the
     * number to its own allowed range and saves it in the profile.
     * Docs: https://developer.android.com/reference/androidx/webkit/HttpCache
     */
    private static void limitCache(Profile profile) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.HTTP_CACHE_MANAGER)
                && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                HttpCache cache = profile.getHttpCache();
                cache.setQuotaBytes(MAX_CACHE_BYTES);
            } catch (RuntimeException e) {
                Log.w(TAG, "Couldn't set the cache limit", e);
            }
        }
    }
}
