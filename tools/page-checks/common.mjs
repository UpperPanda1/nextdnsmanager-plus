// Shared helpers for the page checks (tools/page-checks). Development-time only: nothing here is part of the app.
//
// The app's HTML pages (settings.html, about.html) and injected scripts (assets/js/*.js) normally run inside
// Android's WebView and talk to Java through "bridges" (window.AndroidSettings, window.AndroidAccounts…). These
// checks open them in desktop Chromium instead, with small JavaScript stand-ins for those bridges, and look at what
// the page shows and what it sends to the app. Chromium and WebView share the same engine, so this is a good check
// of the page's own logic; it is NOT a check of the Android side (that needs a phone).
import { chromium } from 'playwright';
import { fileURLToPath, pathToFileURL } from 'url';
import { mkdirSync, readFileSync } from 'fs';
import path from 'path';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(HERE, '../..');
export const ASSETS = path.join(ROOT, 'app/src/main/assets');

/** file:// address of a page in app/src/main/assets (e.g. pageUrl('settings.html')). */
export function pageUrl(name) {
    return pathToFileURL(path.join(ASSETS, name)).href;
}

/** Path of a file in app/src/main/assets (e.g. assetPath('js/zoom_viewport.js')). */
export function assetPath(name) {
    return path.join(ASSETS, name);
}

/** Starts Chromium: the one in CHROMIUM_PATH if set (Claude's sandbox), else Playwright's own download. */
export function launch() {
    return chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
}

/** A phone-sized page (412 x 915, like a Pixel) that records script errors in page.errors. */
export async function phonePage(browser, colorScheme = 'light') {
    const page = await browser.newPage({ viewport: { width: 412, height: 915 }, colorScheme });
    page.errors = [];
    page.on('pageerror', e => page.errors.push(String(e)));
    return page;
}

/** Saves a screenshot to tools/page-checks/screenshots/ (git-ignored) for a person to look at. */
export async function shot(page, name) {
    const dir = path.join(HERE, 'screenshots');
    mkdirSync(dir, { recursive: true });
    await page.screenshot({ path: path.join(dir, name + '.png') });
}

/** Throws with a readable message when a check fails (the script then exits with code 1). */
export function expect(ok, message) {
    if (!ok) throw new Error('FAILED: ' + message);
}

/** Runs a check and exits 0/1, so run-all.mjs can count it. */
export async function run(fn) {
    const browser = await launch();
    try {
        await fn(browser);
        console.log('ok');
    } catch (e) {
        console.error(e.message || e);
        process.exitCode = 1;
    } finally {
        await browser.close();
    }
}

/**
 * Stand-ins for the settings page's bridges, installed before the page's own script runs. They keep a
 * fake account list with the same rules as account/AccountStore (numbering, last one stays, order),
 * so the page can be clicked through. Every call is logged in window.calls.
 */
export const SETTINGS_BRIDGES = `
window.calls = [];
(function () {
    let accounts = [{ id: 'main', name: 'Home' }];
    let current = 'main';
    let next = 1;
    const settings = { service: 'NextDNS', app_name: 'NextDNS Manager+', version: '0.1.0', theme_mode: 'system',
        pinch_zoom_page: false, max_live_tabs: 3, max_live_tabs_limit: 10, app_lock: false };
    function json(error) {
        const o = { accounts: accounts.map((a, i) => ({ id: a.id, name: a.name, number: i + 1 })),
            current, canAdd: accounts.length < 20, multiAccount: true };
        if (error) o.error = error;
        return JSON.stringify(o);
    }
    window.AndroidAccounts = {
        getAccountsJson: () => json(),
        addAccount: (name) => { calls.push(['add', name]); const clean = String(name || '').trim();
            accounts.push({ id: 'a-' + (next++), name: clean || 'Account ' + (accounts.length + 1) }); return json(); },
        renameAccount: (id, name) => { calls.push(['rename', id, name]); const a = accounts.find(x => x.id === id);
            if (!String(name).trim()) return json('Please enter a name.'); a.name = String(name).trim(); return json(); },
        reorderAccounts: (idsJson) => { calls.push(['reorder', idsJson]); const ids = JSON.parse(idsJson);
            accounts = ids.map(id => accounts.find(a => a.id === id)); return json(); },
        openAccount: (id) => calls.push(['open', id]),
        signOutAccount: (id) => { calls.push(['signOut', id]); return json(); },
        removeAccount: (id) => { calls.push(['remove', id]);
            if (accounts.length < 2) return json('The last account can\\'t be removed. Sign it out instead.');
            accounts = accounts.filter(a => a.id !== id); if (current === id) current = accounts[0].id; return json(); }
    };
    window.AndroidSettings = {
        getSettingsJson: () => JSON.stringify(settings),
        setThemeMode: (mode) => { calls.push(['theme', mode]); settings.theme_mode = mode;
            return JSON.stringify({ mode, css: ':root{--md-surface:' + (mode === 'dark' ? '#212529' : '#FFFFFF') + ';}' }); },
        setPinchZoomPage: (on) => calls.push(['pinch', on]),
        setMaxLiveTabs: (n) => calls.push(['liveTabs', n]),
        // The app answers later, after the phone's unlock sheet: here it "unlocks" at once.
        setAppLock: (on) => { calls.push(['appLock', on]); setTimeout(() => window.onAppLock(on, null), 10); },
        openAbout: () => calls.push(['about']),
        openLink: (name) => calls.push(['link', name]),
        close: () => calls.push(['close'])
    };
    window.AndroidStorage = {
        measureCache: () => setTimeout(() => window.onCacheSize(5 * 1024 * 1024), 10),
        clearCache: () => { calls.push(['clearCache']); setTimeout(() => window.onCacheSize(200 * 1024), 10); }
    };
})();
`;

/**
 * The CSS the app gives its pages for a scheme ('LIGHT' or 'DARK'), built from theme/Themes.java the same way
 * Scheme.toCss() does, so the checks see the app's real colours (and break if the Java file changes shape).
 */
export function schemeCss(name) {
    const src = readFileSync(path.join(ROOT, 'app/src/main/java/org/dnsmanager/app/theme/Themes.java'), 'utf8');
    const accent = src.match(/ACCENT = 0x([0-9A-F]{8})/)[1].slice(2);
    const start = src.indexOf('public static final Scheme ' + name);
    const block = src.slice(start, src.indexOf('));', start));
    const roles = [...block.matchAll(/"([a-zA-Z]+)", (?:0x([0-9A-F]{8})|ACCENT)/g)];
    if (roles.length !== 32) throw new Error('expected 32 colour roles in Themes.' + name + ', found ' + roles.length);
    const kebab = r => r.replace(/[A-Z]/g, c => '-' + c.toLowerCase());
    return ':root{color-scheme:' + (name === 'DARK' ? 'dark' : 'light') + ';'
        + roles.map(m => '--md-' + kebab(m[1]) + ':#' + (m[2] ? m[2].slice(2) : accent) + ';').join('') + '}';
}
