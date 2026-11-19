package org.dnsmanager.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.dnsmanager.app.account.Account;
import org.dnsmanager.app.account.AccountStore;
import org.dnsmanager.app.downloads.Downloads;
import org.dnsmanager.app.lock.AppLock;
import org.dnsmanager.app.lock.LockRules;
import org.dnsmanager.app.site.Site;
import org.dnsmanager.app.status.ConnectionStatus;
import org.dnsmanager.app.theme.Scheme;
import org.dnsmanager.app.theme.ThemeManager;
import org.dnsmanager.app.theme.ThemeRebuild;
import org.dnsmanager.app.ui.AboutWindow;
import org.dnsmanager.app.ui.AccountBadge;
import org.dnsmanager.app.ui.FindBar;
import org.dnsmanager.app.ui.LockScreen;
import org.dnsmanager.app.ui.NativeTheme;
import org.dnsmanager.app.ui.PingWindow;
import org.dnsmanager.app.ui.SettingsRestore;
import org.dnsmanager.app.ui.SettingsWindow;
import org.dnsmanager.app.ui.SwitcherPopup;
import org.dnsmanager.app.ui.UnloadedPlaceholder;
import org.dnsmanager.app.ui.Windows;
import org.dnsmanager.app.web.DesktopMode;
import org.dnsmanager.app.web.ExternalLinks;
import org.dnsmanager.app.web.FindInPage;
import org.dnsmanager.app.web.PageZoom;
import org.dnsmanager.app.web.Profiles;
import org.dnsmanager.app.web.TabManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The app's one screen: a native top bar above the DNS service's dashboard, one page ("tab") per
 * account (PROJECT.md §4 is the map of the whole app).
 *
 * <p>This class is the <b>wiring</b>: it creates the parts, connects buttons to them and passes
 * Android's lifecycle events on. The rules live in small classes that can be read and tested alone:
 * {@code account/} (the account list), {@code web/} (tabs, profiles, cache, zoom, desktop mode),
 * {@code theme/} (colours), {@code lock/} (app lock), {@code status/} (the status icon), {@code ui/}
 * (menus and windows), {@code site/Site} (everything about the DNS service). When adding a feature,
 * put its logic into such a class and only connect it here.</p>
 *
 * <p>Activity lifecycle in one line: {@code onCreate} builds the screen, {@code onStart}/{@code onStop}
 * bracket "visible", {@code onResume}/{@code onPause} bracket "in front", {@code onDestroy} tears down.
 * Docs: https://developer.android.com/guide/components/activities/activity-lifecycle</p>
 */
public class MainActivity extends Activity {

    /** Saved-state keys: the account that was on screen and its page, so a rebuilt screen shows them again. */
    private static final String STATE_ACCOUNT = "tab_account";
    private static final String STATE_URL = "tab_url";

    /** One background thread for the whole app (cache measuring, the connection test). */
    private static final Executor BACKGROUND = Executors.newSingleThreadExecutor();

    // App lock state. Static = it belongs to the app process, not to one screen object: a rebuilt
    // screen (theme change) stays unlocked, a fresh process starts locked. See lock/LockRules.
    private static boolean unlocked;
    private static long leftAtMs = LockRules.NOT_LEFT;

    private AccountStore accounts;
    private TabManager tabManager;
    private ConnectionStatus connectionStatus;
    private ProgressBar progressBar;
    private ViewGroup tabContainer;
    private FindBar findBar;       // created on first use
    private LockScreen lockScreen;

    // Open windows and menus, kept so they can be closed when the screen is destroyed or locked.
    private Dialog settingsDialog;
    private String settingsSection;
    private Dialog aboutDialog;
    private Dialog pingDialog;
    private Dialog statusDialog;
    private PopupWindow switcherPopup;
    private PopupMenu overflowMenu;

    // The theme the screen was built with (theme/ThemeRebuild compares it with the setting).
    private String appliedMode;
    private boolean appliedNight;

    // The unlock sheet that is showing, or null.
    private CancellationSignal unlockPrompt;

    // ------------------------------------------------------------ lifecycle

    /** Runs before onCreate: forces light or dark for this screen if the Theme setting says so. */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        Configuration override = ThemeManager.nightOverride(newBase);
        if (override != null) {
            applyOverrideConfiguration(override);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeManager.fitContent(findViewById(R.id.mainRoot));
        progressBar = findViewById(R.id.progressBar);
        tabContainer = findViewById(R.id.tabContainer);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG); // chrome://inspect, debug builds only

        SharedPreferences prefs = Prefs.get(this);
        accounts = AccountStore.load(prefs);
        // Before any WebView exists: delete the containers of accounts that were removed.
        Profiles.cleanUp(accounts.all());

        // Which account to show: the one a rebuilt screen had, else the one used last, else the first.
        String wanted = savedInstanceState != null ? savedInstanceState.getString(STATE_ACCOUNT)
                : prefs.getString(Prefs.LAST_ACCOUNT, null);
        Account start = wanted == null ? null : accounts.find(wanted);
        if (start == null) {
            start = accounts.first();
        }

        tabManager = new TabManager(tabContainer, start, this::configureWebView, new TabManager.Listener() {
            @Override
            public void onAccountShown(Account account) {
                onTabShown(account);
            }

            @Override
            public void onTabGaveUp(Account account) {
                Toast.makeText(MainActivity.this, getString(R.string.tab_gave_up, account.name),
                        Toast.LENGTH_LONG).show();
            }
        }, prefs.getInt(Prefs.MAX_LIVE_TABS, Prefs.DEFAULT_MAX_LIVE_TABS));

        // The two black screens that can cover the page: "Unloaded. Tap to load" and "Tap to retry".
        // Inserted just under the loading bar, so they lie above every tab.
        ViewGroup.LayoutParams fill = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        View unloaded = UnloadedPlaceholder.create(this, () -> tabManager.show(tabManager.current()));
        tabContainer.addView(unloaded, tabContainer.indexOfChild(progressBar), fill);
        tabManager.setPlaceholder(unloaded);
        View retry = UnloadedPlaceholder.create(this, R.string.tab_load_failed, () -> tabManager.retryCurrent());
        tabContainer.addView(retry, tabContainer.indexOfChild(progressBar), fill);
        tabManager.setRetryPlaceholder(retry);

        // The lock cover goes on top of everything.
        lockScreen = new LockScreen(this, this::promptUnlock);
        lockScreen.covers(findViewById(R.id.mainRoot)); // hidden from TalkBack and keyboard while locked
        ((ViewGroup) findViewById(R.id.screen)).addView(lockScreen.view(), fill);

        connectionStatus = new ConnectionStatus(this, findViewById(R.id.statusButton), BACKGROUND);
        setupTopBar();

        appliedMode = ThemeManager.mode(this);
        appliedNight = ThemeManager.isNight(this);
        applyTheme();
        applySecureFlag();

        // Loads the dashboard (behind the lock cover if the app is locked); a rebuilt screen reopens its page.
        tabManager.show(start, savedInstanceState != null ? savedInstanceState.getString(STATE_URL) : null);

        String settingsToRestore = savedInstanceState != null
                ? savedInstanceState.getString(SettingsRestore.KEY) : null;
        if (settingsToRestore != null) {
            // Posted: a dialog can only be shown once the window is attached.
            getWindow().getDecorView().post(() -> {
                if (!isFinishing() && !isDestroyed() && !lockScreen.isVisible()) {
                    openSettings(settingsToRestore);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectionStatus.start();
        if (LockRules.mustLock(AppLock.isEnabled(this), unlocked, leftAtMs, SystemClock.elapsedRealtime())) {
            lock();
        }
        leftAtMs = LockRules.NOT_LEFT;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean settingsOpen = settingsDialog != null && settingsDialog.isShowing();
        if (ThemeRebuild.onResume(isThemeStale(), settingsOpen)) {
            recreate();
            return;
        }
        applyTheme();
    }

    @Override
    protected void onStop() {
        super.onStop();
        connectionStatus.stop();
        tabManager.flushCookies(); // logins are on disk before Android may end the process
        if (leftAtMs == LockRules.NOT_LEFT) {
            leftAtMs = SystemClock.elapsedRealtime();
        }
    }

    /**
     * The phone rotated, a keyboard was plugged in, or it switched day/night while the app was open.
     * The manifest's {@code configChanges} keeps the screen and its pages alive for all of these.
     * Only a day/night switch (with Theme = Match system) needs more: the website picks its design
     * from the screen's theme, which is fixed when the screen is built, so the screen is rebuilt
     * (theme/ThemeRebuild; waits while Settings is open). The page the user was on is kept.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean settingsOpen = settingsDialog != null && settingsDialog.isShowing();
        if (ThemeRebuild.onResume(isThemeStale(), settingsOpen)) {
            recreate();
            return;
        }
        applyTheme();
    }

    /** Android is short of memory: give up the pages that aren't on screen. */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            tabManager.closeHiddenTabs();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ACCOUNT, tabManager.current().id);
        WebView tab = tabManager.currentWebView();
        if (tab != null && tab.getUrl() != null) {
            outState.putString(STATE_URL, tab.getUrl()); // checked again before use (TabManager.isStartUrl)
        }
        String settings = SettingsRestore.toSave(settingsDialog != null && settingsDialog.isShowing(), settingsSection);
        if (settings != null) {
            outState.putString(SettingsRestore.KEY, settings);
        }
    }

    @Override
    protected void onDestroy() {
        if (unlockPrompt != null) {
            unlockPrompt.cancel();
            unlockPrompt = null;
        }
        dismissWindows(); // a dialog left open would leak its window
        tabManager.destroyAll();
        super.onDestroy();
    }

    /** Back: close the find bar, else go back inside the page, else leave the app. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (lockScreen.isVisible()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                finish();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (FindInPage.isFindShortcut(event.getAction(), keyCode, event.getMetaState())) {
            openFindInPage(); // Ctrl+F while the focus isn't in the page (inside it: onUnhandledKeyEvent)
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (findBar != null && findBar.isOpen()) {
                findBar.close();
                return true;
            }
            WebView current = tabManager.currentWebView();
            if (current != null && current.canGoBack()) {
                current.goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------ theme

    /** Paints every native part with the current colours. Cheap; called whenever they may have changed. */
    private void applyTheme() {
        Scheme scheme = ThemeManager.scheme(this);
        ThemeManager.styleWindow(this, scheme);
        ThemeManager.applyStatusBar(this);
        NativeTheme.styleTopBar(findViewById(R.id.topBar), scheme);
        NativeTheme.styleProgressBar(progressBar, scheme);
        connectionStatus.setDarkTheme(scheme.dark);
        lockScreen.restyle(scheme);
        if (findBar != null) {
            findBar.restyle(scheme);
        }
        updateTopBar(tabManager.current());
    }

    private boolean isThemeStale() {
        return ThemeRebuild.isStale(appliedMode, appliedNight, ThemeManager.mode(this), ThemeManager.isNight(this));
    }

    // ------------------------------------------------------------ top bar

    private void setupTopBar() {
        findViewById(R.id.accountChip).setOnClickListener(v ->
                switcherPopup = SwitcherPopup.show(v, accounts.all(), tabManager.current(),
                        ThemeManager.scheme(this), switcherHost));
        findViewById(R.id.statusButton).setOnClickListener(v -> showStatusDialog());
        findViewById(R.id.homeButton).setOnClickListener(v -> goHome());
        findViewById(R.id.menuButton).setOnClickListener(this::showOverflowMenu);
    }

    /** The chip: the current account's number and name. */
    private void updateTopBar(Account account) {
        TextView badge = findViewById(R.id.accountBadge);
        TextView name = findViewById(R.id.accountName);
        int number = accounts.numberOf(account.id);
        AccountBadge.apply(badge, number, ThemeManager.scheme(this), AccountBadge.dp(this, 28));
        name.setText(account.name);
        findViewById(R.id.accountChip).setContentDescription(getString(R.string.switch_account) + ": "
                + getString(R.string.account_number, number) + ", " + account.name);
    }

    /** The three-dots menu. Ids are the string resources, so one switch handles the taps. */
    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int item : new int[]{R.string.find_in_page, R.string.menu_ping, R.string.menu_private_dns,
                R.string.menu_settings}) {
            menu.getMenu().add(Menu.NONE, item, Menu.NONE, item);
        }
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.string.find_in_page) {
                openFindInPage();
            } else if (id == R.string.menu_ping) {
                if (pingDialog == null || !pingDialog.isShowing()) {
                    pingDialog = PingWindow.show(this, ThemeManager.scheme(this));
                }
            } else if (id == R.string.menu_private_dns) {
                openPrivateDnsSettings();
            } else if (id == R.string.menu_settings) {
                openSettings(null);
            }
            return true;
        });
        overflowMenu = menu;
        menu.show();
    }

    /** Android has no direct door to the Private DNS setting; the network settings page holds it. */
    private void openPrivateDnsSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
        }
    }

    private void goHome() {
        if (tabManager.isCurrentUnloaded()) {
            tabManager.show(tabManager.current()); // a fresh tab starts at the dashboard anyway
            return;
        }
        tabManager.currentWebView().loadUrl(Site.HOME_URL);
    }

    /** Tapping the status icon explains what it says. */
    private void showStatusDialog() {
        if (statusDialog != null && statusDialog.isShowing()) {
            return;
        }
        statusDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.status_title)
                .setMessage(connectionStatus.description() + "\n\n" + getString(R.string.status_hint, Site.NAME))
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.menu_private_dns, (d, which) -> openPrivateDnsSettings())
                .show();
        // A dialog is a window of its own: the main screen's "secure" mark doesn't cover it (ui/Windows.setSecure).
        Windows.setSecure(statusDialog, AppLock.isEnabled(this));
    }

    // ------------------------------------------------------------ find in page

    private void openFindInPage() {
        WebView tab = tabManager.currentWebView();
        if (tab != null) {
            showFindBar(tab);
        }
    }

    private void showFindBar(WebView tab) {
        if (findBar == null) {
            findBar = new FindBar(tabContainer, ThemeManager.scheme(this));
        }
        findBar.open(tab);
    }

    // ------------------------------------------------------------ account menu

    private final SwitcherPopup.Host switcherHost = new SwitcherPopup.Host() {
        @Override
        public void pick(Account account) {
            tabManager.show(account);
        }

        @Override
        public boolean isLoaded(String accountId) {
            return tabManager.isLoaded(accountId);
        }

        @Override
        public void unload(String accountId) {
            WebView tab = tabManager.currentWebView();
            if (findBar != null && tab != null && accountId.equals(tabManager.current().id)) {
                findBar.closeIfSearching(tab); // its WebView is about to be destroyed
            }
            tabManager.unload(accountId);
        }

        @Override
        public boolean isDesktopOn() {
            return DesktopMode.isOn(Prefs.get(MainActivity.this), tabManager.current().id);
        }

        @Override
        public void toggleDesktop() {
            DesktopMode.toggle(Prefs.get(MainActivity.this), tabManager.current().id);
            syncDesktopMode(tabManager.current()); // reloads the tab with the other layout
        }

        @Override
        public void reload() {
            reloadCurrentTab();
        }

        @Override
        public boolean isStatusBarHidden() {
            return ThemeManager.isStatusBarHidden(MainActivity.this);
        }

        @Override
        public void toggleStatusBar() {
            ThemeManager.setStatusBarHidden(MainActivity.this, !ThemeManager.isStatusBarHidden(MainActivity.this));
            ThemeManager.applyStatusBar(MainActivity.this);
        }

        @Override
        public void clearCache() {
            clearAllCaches();
            Toast.makeText(MainActivity.this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void manageAccounts() {
            openSettings("accounts");
        }
    };

    /** Reload: an unloaded account is loaded, a blank tab goes to the dashboard, else a normal reload. */
    private void reloadCurrentTab() {
        WebView tab = tabManager.currentWebView();
        if (tab == null) {
            tabManager.show(tabManager.current());
            return;
        }
        String url = tab.getUrl();
        if (url == null || url.isEmpty() || url.startsWith("about:")) {
            tab.loadUrl(Site.HOME_URL);
        } else {
            tab.reload(); // re-checks the page with the server; unchanged files come from the cache
        }
    }

    /**
     * "Clear cache" (account menu and Settings): every account's website files, for open tabs and
     * closed accounts alike. Cookies and site storage are never touched, so nobody is signed out.
     * The details are in web/Profiles.clearAllCaches.
     */
    private void clearAllCaches() {
        Profiles.clearAllCaches(this, tabManager.liveWebViews(), tabManager.liveProfileKeys());
    }

    private void syncDesktopMode(Account account) {
        DesktopMode.sync(this, Prefs.get(this), tabManager.currentWebView(), account);
    }

    /** An account's tab came to the front (TabManager.Listener). */
    private void onTabShown(Account account) {
        updateTopBar(account);
        if (findBar != null) {
            findBar.closeUnlessSearching(tabManager.currentWebView()); // the bar belongs to the tab it searched
        }
        syncDesktopMode(account);
        progressBar.setVisibility(View.GONE);
        Prefs.get(this).edit().putString(Prefs.LAST_ACCOUNT, account.id).apply();
    }

    // ------------------------------------------------------------ tabs (WebView set-up)

    /**
     * Sets up a brand-new tab (called by TabManager right after the WebView was put into its
     * account's profile). Everything a page is allowed to do is decided here.
     */
    @SuppressLint("SetJavaScriptEnabled") // the dashboard is a JavaScript app; only the service's own pages load here
    private void configureWebView(WebView webView, Account account) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);      // the dashboard keeps its state in local storage
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);        // honour the page's own <meta name="viewport">
        // Zooming is possible in principle; the dashboard forbids it until "Pinch to zoom" lifts the ban.
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);   // no +/- buttons, only the pinch
        settings.setAllowFileAccess(false);       // a website has no business reading the phone's files
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        // LOAD_DEFAULT = normal browser caching: files are reused from the account's cache while they
        // are fresh and re-checked with the server when not. Never "no cache", never cleared by the app.
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOWNLOAD_FAVICONS_ENABLED)) {
            WebSettingsCompat.setDownloadFaviconsEnabled(settings, false); // the app never shows the site icon
        }

        SharedPreferences prefs = Prefs.get(this);
        PageZoom.apply(this, webView, PageZoom.isOn(prefs));
        DesktopMode.applySaved(this, prefs, webView, account);

        CookieManager cookies = Profiles.cookieManager(webView);
        cookies.setAcceptCookie(true);                           // the login is a cookie
        cookies.setAcceptThirdPartyCookies(webView, false);      // other sites embedded in the page get none

        webView.setWebViewClient(new TabClient());
        webView.setWebChromeClient(new TabChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                Downloads.start(this, url, BACKGROUND));
    }

    private boolean isVisibleTab(WebView view) {
        return view == tabManager.currentWebView();
    }

    /** Page events of a tab. One object per tab; it holds no state of its own. */
    private class TabClient extends WebViewClient {

        /**
         * Which addresses load inside the app: only the service's own https pages (site/Site.ownsHost).
         * Everything else (help articles on other sites, mail links…) is handed to the phone's apps,
         * so no foreign website ever runs inside an account's container.
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return false; // a frame inside the page (e.g. a payment form) stays part of the page
            }
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("https".equals(scheme) && Site.ownsHost(uri.getHost())) {
                return false; // load it here
            }
            if ("about".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme)) {
                return false; // made by the page itself
            }
            // Only https/http/mailto/tel leave the app (web/ExternalLinks); file:, intent: etc. are dropped.
            if (!ExternalLinks.open(MainActivity.this, uri)) {
                Toast.makeText(MainActivity.this, R.string.no_app_for_action, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            tabManager.pageStarted(view);
            if (isVisibleTab(view)) {
                progressBar.setVisibility(View.VISIBLE);
            }
        }

        /** The new page has drawn something: a "Tap to retry" screen from an earlier failure goes. */
        @Override
        public void onPageCommitVisible(WebView view, String url) {
            tabManager.pageVisible(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (isVisibleTab(view)) {
                progressBar.setVisibility(View.GONE);
            }
            tabManager.pageVisible(view);
            // Fallbacks for WebViews too old to run a script at document start.
            DesktopMode.onPageFinished(MainActivity.this, view);
            PageZoom.onPageFinished(MainActivity.this, view);
        }

        /** The page itself couldn't be loaded (offline, server down): show "Tap to retry". */
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                tabManager.markLoadFailed(view, request.getUrl().toString());
            }
        }

        /** Ctrl+F on a keyboard, if the page didn't use the key itself. */
        @Override
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            if (FindInPage.isFindShortcut(event.getAction(), event.getKeyCode(), event.getMetaState())
                    && isVisibleTab(view) && !lockScreen.isVisible()) {
                showFindBar(view);
                return;
            }
            super.onUnhandledKeyEvent(view, event);
        }

        /** The page's process died. Must be handled, or Android ends the whole app (web/WebViewRecovery). */
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (findBar != null) {
                findBar.closeIfSearching(view);
            }
            if (isVisibleTab(view)) {
                progressBar.setVisibility(View.GONE);
            }
            tabManager.onRendererGone(view);
            return true;
        }
    }

    /**
     * The browser-window side of a tab. The defaults are what we want: the page's own
     * alert/confirm boxes show, and requests for camera, microphone, location or file pickers are
     * refused (the dashboard needs none of them).
     */
    private static class TabChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage message) {
            return true; // the page's console output is not copied into the phone's log
        }
    }

    // ------------------------------------------------------------ Settings and other windows

    private void openSettings(String section) {
        if (lockScreen.isVisible() || (settingsDialog != null && settingsDialog.isShowing())) {
            return; // never while locked: Settings can sign out or remove accounts
        }
        settingsSection = SettingsRestore.safeSection(section);
        settingsDialog = SettingsWindow.open(settingsHost, BACKGROUND, settingsSection);
    }

    private final SettingsWindow.Host settingsHost = new SettingsWindow.Host() {
        @Override
        public Activity activity() {
            return MainActivity.this;
        }

        @Override
        public AccountStore accounts() {
            return accounts;
        }

        @Override
        public SharedPreferences prefs() {
            return Prefs.get(MainActivity.this);
        }

        @Override
        public Account currentAccount() {
            return tabManager.current();
        }

        @Override
        public boolean profilesSupported() {
            return Profiles.isSupported();
        }

        @Override
        public void runOnUiThread(Runnable action) {
            MainActivity.this.runOnUiThread(action);
        }

        @Override
        public void onAccountsChanged() {
            tabManager.updateAccounts(accounts); // new names and order, removed tabs, top bar
        }

        @Override
        public void forgetAccount(Account account, boolean removed) {
            WebView tab = tabManager.currentWebView();
            if (findBar != null && tab != null && account.id.equals(tabManager.current().id)) {
                findBar.closeIfSearching(tab);
            }
            tabManager.unload(account.id);   // the WebView must be gone before its data is deleted
            // Asynchronous: until the container is empty TabManager won't load a page into it (web/WipeGate);
            // if the account was opened meanwhile, it loads now. A screen that is gone ignores the message.
            Profiles.forget(account, showId -> {
                if (!isDestroyed()) {
                    tabManager.loadAfterWipe(showId);
                }
            });
            if (removed) {
                DesktopMode.forget(Prefs.get(MainActivity.this), account.id); // signed out: the choice stays
            }
        }

        @Override
        public void openAccount(Account account) {
            if (settingsDialog != null) {
                settingsDialog.dismiss();
            }
            tabManager.show(account);
        }

        @Override
        public void clearAllCaches() {
            MainActivity.this.clearAllCaches();
        }

        @Override
        public void onThemeChanged() {
            applyTheme(); // native colours now; the pages follow when Settings closes (onSettingsClosed)
        }

        @Override
        public void onPinchZoomChanged() {
            boolean on = PageZoom.isOn(Prefs.get(MainActivity.this));
            for (WebView tab : tabManager.liveWebViews()) {
                PageZoom.sync(MainActivity.this, tab, on); // reloads each tab once with the new rule
            }
        }

        @Override
        public void onMaxLiveTabsChanged(int maxLiveTabs) {
            tabManager.setMaxLiveTabs(maxLiveTabs);
        }

        @Override
        public void requestAppLock(boolean on, SettingsWindow.AppLockResult result) {
            changeAppLock(on, result);
        }

        @Override
        public void openAbout() {
            if (aboutDialog == null || !aboutDialog.isShowing()) {
                aboutDialog = AboutWindow.show(MainActivity.this);
            }
        }

        @Override
        public void onSettingsClosed(Dialog dialog) {
            if (settingsDialog == dialog) {
                settingsDialog = null;
            }
            // A changed theme needs the screen rebuilt so the pages switch too.
            if (ThemeRebuild.onSettingsClosed(isThemeStale(), isFinishing() || isDestroyed())) {
                recreate();
            }
        }
    };

    /** Closes every window and menu that may be open (before locking, and when the screen goes). */
    private void dismissWindows() {
        for (Dialog dialog : new Dialog[]{aboutDialog, pingDialog, statusDialog, settingsDialog}) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
        if (switcherPopup != null) {
            switcherPopup.dismiss();
        }
        if (overflowMenu != null) {
            overflowMenu.dismiss();
        }
        if (findBar != null) {
            findBar.close();
        }
    }

    // ------------------------------------------------------------ app lock

    /** Covers the screen and asks for the phone's unlock. */
    private void lock() {
        unlocked = false;
        dismissWindows(); // Settings etc. are separate windows the cover wouldn't hide
        lockScreen.setVisible(true);
        // Posted: the unlock sheet needs the screen's window to be up (lock() runs from onStart).
        getWindow().getDecorView().post(() -> {
            if (!isFinishing() && !isDestroyed() && lockScreen.isVisible()) {
                promptUnlock();
            }
        });
    }

    private void unlock() {
        unlocked = true;
        leftAtMs = LockRules.NOT_LEFT;
        lockScreen.setVisible(false);
    }

    /** Shows Android's unlock sheet (also the lock cover's Unlock button). */
    private void promptUnlock() {
        if (unlockPrompt != null) {
            return; // the sheet is already up
        }
        if (!AppLock.isAvailable(this)) {
            // The phone's screen lock was removed. Only its owner can do that (Android asks for the
            // old PIN first), so there is nothing left to protect with: let them in and say why.
            Toast.makeText(this, R.string.lock_unavailable, Toast.LENGTH_LONG).show();
            unlock();
            return;
        }
        unlockPrompt = AppLock.prompt(this, getString(R.string.lock_title, getString(R.string.app_name)),
                getString(R.string.lock_subtitle), new AppLock.Callback() {
                    @Override
                    public void onUnlocked() {
                        unlockPrompt = null;
                        unlock();
                    }

                    @Override
                    public void onCancelled() {
                        unlockPrompt = null; // the cover stays; its button asks again
                    }
                });
    }

    /**
     * Settings → App lock was switched. Both directions need the phone's unlock first: turning it on
     * proves the user can pass it (nobody locks themselves out), turning it off proves it is the owner.
     */
    private void changeAppLock(boolean on, SettingsWindow.AppLockResult result) {
        if (!AppLock.isAvailable(this)) {
            if (!on) {
                AppLock.setEnabled(this, false); // nothing to confirm with (see promptUnlock)
                applySecureFlag();
            }
            result.done(false, on ? getString(R.string.lock_unavailable) : null);
            return;
        }
        if (unlockPrompt != null) {
            result.done(AppLock.isEnabled(this), null);
            return;
        }
        unlockPrompt = AppLock.prompt(this, getString(R.string.lock_title, getString(R.string.app_name)),
                getString(R.string.lock_subtitle), new AppLock.Callback() {
                    @Override
                    public void onUnlocked() {
                        unlockPrompt = null;
                        AppLock.setEnabled(MainActivity.this, on);
                        unlocked = true;
                        applySecureFlag();
                        result.done(on, null);
                    }

                    @Override
                    public void onCancelled() {
                        unlockPrompt = null;
                        result.done(AppLock.isEnabled(MainActivity.this), null);
                    }
                });
    }

    /**
     * While the app lock is on, the screen (and its extra windows, see ui/Windows.setSecure) is marked
     * "secure": Android then leaves it out of screenshots and shows a blank card in the recent-apps
     * overview, so the dashboard can't be read there without unlocking.
     */
    private void applySecureFlag() {
        if (AppLock.isEnabled(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        // Settings, About, the Ping test and the status dialog are windows of their own. The lock is switched
        // inside Settings, so the windows that are open right now must follow the new setting at once.
        for (Dialog dialog : new Dialog[]{settingsDialog, aboutDialog, pingDialog, statusDialog}) {
            if (dialog != null && dialog.isShowing()) {
                Windows.setSecure(dialog, AppLock.isEnabled(this));
            }
        }
    }
}
