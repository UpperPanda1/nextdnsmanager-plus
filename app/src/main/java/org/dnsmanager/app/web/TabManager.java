package org.dnsmanager.app.web;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.dnsmanager.app.R;
import org.dnsmanager.app.account.Account;
import org.dnsmanager.app.account.AccountStore;
import org.dnsmanager.app.site.Site;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "tabs": one WebView per loaded account, all stacked in one container, only the current one
 * visible (PROJECT.md §5.3). Switching accounts just swaps which WebView is shown, so it is instant
 * and each page keeps its place.
 *
 * <p><b>Memory:</b> every WebView is a whole browser page. At most {@code maxLiveTabs} stay loaded
 * (Settings → "Accounts kept loaded"); the one used longest ago is closed first (web/TabLru). A
 * closed account is not gone: its login lives in its profile, and picking it again loads the page
 * anew (from the cache, see web/Profiles).</p>
 *
 * <p><b>Battery:</b> a hidden tab is paused ({@code onPause}; {@code setVisibility(GONE)} alone does
 * not make a page "hidden" [Chromium BrowserViewRenderer]) and its renderer may be given up by
 * Android first.</p>
 */
public final class TabManager {

    /** Sets up a brand-new tab WebView (settings, clients). Implemented by MainActivity. */
    public interface Configurer {
        void configure(WebView webView, Account account);
    }

    /** Told when the visible account changes (MainActivity updates the top bar). */
    public interface Listener {
        void onAccountShown(Account account);

        /** A tab crashed three times in a minute and was closed; MainActivity shows a message. */
        void onTabGaveUp(Account account);
    }

    private final ViewGroup container;
    private final Configurer configurer;
    private final Listener listener;
    // account id → its WebView / its Account (the Account object is refreshed after renames).
    private final Map<String, WebView> liveTabs = new HashMap<>();
    private final Map<String, Account> liveAccounts = new HashMap<>();
    private final TabLru lru = new TabLru();
    private final ArrayDeque<Long> recoveryTimes = new ArrayDeque<>();
    private Account current;
    private int maxLiveTabs;
    // Shown instead of a page while the account on screen is unloaded; may be null.
    private View placeholder;
    // Shown instead of a page that failed to load; may be null.
    private View retryPlaceholder;
    // True after destroyAll (the screen is gone): a late "container emptied" message must not create a WebView.
    private boolean destroyed;

    /**
     * @param container the view that holds the tabs (R.id.tabContainer)
     * @param first     the account that is "current" until {@link #show} is called; no page is loaded yet
     */
    public TabManager(ViewGroup container, Account first, Configurer configurer, Listener listener,
                      int maxLiveTabs) {
        this.container = container;
        this.current = first;
        this.configurer = configurer;
        this.listener = listener;
        this.maxLiveTabs = maxLiveTabs;
    }

    public Account current() {
        return current;
    }

    /** @return the WebView on screen, or null while the current account is unloaded. */
    public WebView currentWebView() {
        return liveTabs.get(current.id);
    }

    public List<WebView> liveWebViews() {
        return new ArrayList<>(liveTabs.values());
    }

    /** @return the profile keys (web/Profiles.keyOf) of the tabs that exist right now. */
    public Set<String> liveProfileKeys() {
        Set<String> keys = new HashSet<>();
        for (Account account : liveAccounts.values()) {
            keys.add(Profiles.keyOf(account));
        }
        return keys;
    }

    public void setPlaceholder(View placeholder) {
        this.placeholder = placeholder;
        updatePlaceholder();
    }

    public void setRetryPlaceholder(View retryPlaceholder) {
        this.retryPlaceholder = retryPlaceholder;
        updatePlaceholder();
    }

    // ------------------------------------------------------------ failed page loads ("Tap to retry")

    /** A page's main document failed to load: remember it on the WebView and show the retry screen. */
    public void markLoadFailed(WebView view, String url) {
        LoadErrors.TabState state = loadState(view);
        if (state == null) {
            state = new LoadErrors.TabState();
            view.setTag(R.id.tag_load_failed, state); // dies with the WebView
        }
        state.failed(url);
        updatePlaceholder();
    }

    public void pageStarted(WebView view) {
        LoadErrors.TabState state = loadState(view);
        if (state != null) {
            state.pageStarted();
        }
    }

    /** A page was drawn: if it wasn't the failed load, the retry screen goes. */
    public void pageVisible(WebView view) {
        LoadErrors.TabState state = loadState(view);
        if (state != null && state.pageVisible()) {
            updatePlaceholder();
        }
    }

    public void retryCurrent() {
        WebView view = currentWebView();
        LoadErrors.TabState state = view == null ? null : loadState(view);
        if (state == null || !state.showsRetry()) {
            return;
        }
        view.loadUrl(!state.failedUrl().isEmpty() ? state.failedUrl() : Site.HOME_URL);
    }

    private static LoadErrors.TabState loadState(WebView view) {
        Object tag = view.getTag(R.id.tag_load_failed);
        return tag instanceof LoadErrors.TabState ? (LoadErrors.TabState) tag : null;
    }

    // ------------------------------------------------------------ loading, showing, unloading

    public boolean isLoaded(String accountId) {
        return liveTabs.containsKey(accountId);
    }

    public boolean isCurrentUnloaded() {
        return !isLoaded(current.id);
    }

    public Account accountOf(WebView webView) {
        for (Map.Entry<String, WebView> e : liveTabs.entrySet()) {
            if (e.getValue() == webView) {
                return liveAccounts.get(e.getKey());
            }
        }
        return null;
    }

    /**
     * Shows an account's tab, creating and loading it if it isn't loaded.
     *
     * @return the WebView now on screen
     */
    public WebView show(Account account) {
        return show(account, null);
    }

    /**
     * Like {@link #show(Account)}, but a tab that has to be created opens {@code startUrl} instead of
     * the dashboard's start page (used after a screen rebuild, so the user stays where they were).
     * Only the service's own https pages are accepted ({@link #isStartUrl}); anything else = start page.
     *
     * <p>If the account's container is still being emptied ("Sign out" / "Remove", web/WipeGate), no page is
     * created yet: the account comes on screen with the "Unloaded" screen, and {@link #loadAfterWipe} loads it as
     * soon as the deletion is done.</p>
     *
     * @return the WebView now on screen, or null while the account's container is being emptied
     */
    public WebView show(Account account, String startUrl) {
        WebView target = liveTabs.get(account.id);
        boolean isNew = target == null;
        if (isNew) {
            String key = Profiles.keyOf(account);
            if (Profiles.wipes().blocks(key)) {
                Profiles.wipes().wantShown(key, account.id);
                showWithoutPage(account);
                return null;
            }
            target = createTab(account);
        }
        liveAccounts.put(account.id, account); // keep the newest name
        if (!account.id.equals(current.id)) {
            WebView previous = currentWebView();
            if (previous != null) {
                previous.setVisibility(View.GONE);
                previous.onPause();
            }
        }
        target.setVisibility(View.VISIBLE);
        target.onResume();
        current = account;
        updatePlaceholder();
        lru.touch(account.id);
        if (isNew) {
            target.loadUrl(isStartUrl(startUrl) ? startUrl : Site.HOME_URL);
        }
        closeExcessTabs();
        if (listener != null) {
            listener.onAccountShown(account);
        }
        return target;
    }

    /**
     * Puts an account on screen without a page (its container is being emptied): the previous tab is hidden
     * and paused as in {@link #show}, the "Unloaded" screen shows, the top bar follows.
     */
    private void showWithoutPage(Account account) {
        if (!account.id.equals(current.id)) {
            WebView previous = currentWebView();
            if (previous != null) {
                previous.setVisibility(View.GONE);
                previous.onPause();
            }
        }
        current = account;
        updatePlaceholder();
        if (listener != null) {
            listener.onAccountShown(account);
        }
    }

    /**
     * An account's container was emptied (web/Profiles.forget reported back). If that account was asked for
     * meanwhile and is still the one on screen, its page loads now. Main thread.
     *
     * @param accountId the account to load ({@link WipeGate#finished}), or null for nothing
     */
    public void loadAfterWipe(String accountId) {
        if (destroyed || accountId == null || !accountId.equals(current.id) || isLoaded(accountId)) {
            return;
        }
        show(current);
    }

    /**
     * Closes an account's page to free memory (the ✕ in the account menu). The account stays the
     * current one if it was; the "Unloaded. Tap to load" screen shows instead of its page.
     *
     * @return false if it wasn't loaded
     */
    public boolean unload(String accountId) {
        if (!liveTabs.containsKey(accountId)) {
            return false;
        }
        closeTab(accountId);
        updatePlaceholder();
        return true;
    }

    /** @return true if {@code url} may be opened directly: an https page of the service (site/Site). */
    static boolean isStartUrl(String url) {
        if (url == null || !url.startsWith("https://")) {
            return false;
        }
        int end = url.length();
        for (char stop : new char[]{'/', '?', '#'}) {
            int i = url.indexOf(stop, 8);
            if (i >= 0 && i < end) {
                end = i;
            }
        }
        String host = url.substring(8, end);
        return !host.contains("@") && !host.contains(":") && Site.ownsHost(host);
    }

    public void setMaxLiveTabs(int maxLiveTabs) {
        this.maxLiveTabs = maxLiveTabs;
        closeExcessTabs();
    }

    /**
     * The account list changed (Settings): close the tabs of removed accounts, take over new names,
     * and if the account on screen was removed, show the first account instead.
     */
    public void updateAccounts(AccountStore accounts) {
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            Account fresh = accounts.find(id);
            if (fresh == null) {
                closeTab(id);
            } else {
                liveAccounts.put(id, fresh);
            }
        }
        Account fresh = accounts.find(current.id);
        if (fresh == null) {
            show(accounts.first());
            return;
        }
        current = fresh;
        updatePlaceholder();
        if (listener != null) {
            listener.onAccountShown(current); // e.g. refresh the top bar after a rename or re-order
        }
    }

    /** Closes hidden tabs when Android is short of memory (MainActivity.onTrimMemory). */
    public void closeHiddenTabs() {
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            if (!id.equals(current.id)) {
                closeTab(id);
            }
        }
    }

    /** Writes every open account's cookies to disk now (MainActivity.onStop), so no login is lost. */
    public void flushCookies() {
        for (WebView webView : liveTabs.values()) {
            Profiles.cookieManager(webView).flush();
        }
    }

    /**
     * A tab's renderer process died (crash, or Android took its memory). All WebViews must handle
     * this or the whole app is killed. A hidden tab is just closed; the visible one is replaced by a
     * fresh WebView on the same page, unless it died three times within a minute (then it is closed
     * and the user is told, instead of reloading for ever).
     */
    public void onRendererGone(WebView dead) {
        Account account = accountOf(dead);
        if (account == null) {
            WebViewRecovery.discard(dead);
            return;
        }
        if (!account.id.equals(current.id)) {
            closeTab(account.id);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        recoveryTimes.addLast(now);
        while (!recoveryTimes.isEmpty() && now - recoveryTimes.peekFirst() > 60_000) {
            recoveryTimes.removeFirst();
        }
        if (recoveryTimes.size() >= 3) {
            recoveryTimes.clear();
            closeTab(account.id);
            updatePlaceholder(); // "Unloaded. Tap to load"
            if (listener != null) {
                listener.onTabGaveUp(account);
            }
            return;
        }
        String lastUrl = dead.getUrl(); // read before destroying
        WebView fresh = WebViewRecovery.replace(dead);
        prepare(fresh, account);
        liveTabs.put(account.id, fresh);
        updatePlaceholder(); // a fresh WebView has no "failed" mark
        boolean isWebPage = lastUrl != null && lastUrl.startsWith("https://");
        fresh.loadUrl(isWebPage ? lastUrl : Site.HOME_URL);
    }

    /** Destroys every tab (MainActivity.onDestroy). */
    public void destroyAll() {
        destroyed = true;
        for (String id : liveTabs.keySet().toArray(new String[0])) {
            closeTab(id);
        }
    }

    private WebView createTab(Account account) {
        WebView webView = new WebView(container.getContext());
        // Index 0 = underneath the placeholder screens and the loading bar.
        container.addView(webView, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        prepare(webView, account);
        liveTabs.put(account.id, webView);
        liveAccounts.put(account.id, account);
        return webView;
    }

    private void prepare(WebView webView, Account account) {
        Profiles.attach(webView, account); // must come first (see Profiles.attach)
        configurer.configure(webView, account);
        // "Waived when not visible": Android may take a hidden tab's renderer before the visible one's.
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
    }

    private void updatePlaceholder() {
        boolean unloaded = isCurrentUnloaded();
        if (placeholder != null) {
            placeholder.setVisibility(unloaded ? View.VISIBLE : View.GONE);
        }
        if (retryPlaceholder != null) {
            WebView view = currentWebView();
            LoadErrors.TabState state = view == null ? null : loadState(view);
            boolean failed = !unloaded && state != null && state.showsRetry();
            retryPlaceholder.setVisibility(failed ? View.VISIBLE : View.GONE);
        }
    }

    private void closeExcessTabs() {
        for (String id : lru.idsToClose(maxLiveTabs)) {
            closeTab(id);
        }
    }

    private void closeTab(String id) {
        WebView webView = liveTabs.remove(id);
        liveAccounts.remove(id);
        lru.remove(id);
        if (webView != null) {
            WebViewRecovery.discard(webView); // removes it from the screen and frees its memory
        }
    }
}
