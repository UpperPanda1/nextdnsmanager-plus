// The settings page in the app's dark colours: the colours the app writes (theme/ThemeBridge) win over the page's
// defaults, and screen-reader-only text stays invisible. Also a narrow 360 px phone: nothing sticks out sideways.
import { run, phonePage, pageUrl, expect, shot, SETTINGS_BRIDGES, schemeCss } from './common.mjs';

await run(async (browser) => {
    const page = await phonePage(browser, 'light'); // the PHONE is light; the app's theme says dark
    // Exactly the CSS the app sends in dark mode (theme/Themes.DARK → Scheme.toCss), read from the Java source.
    const css = schemeCss('DARK');
    await page.addInitScript(SETTINGS_BRIDGES + `window.AndroidTheme = { css: () => ${JSON.stringify(css)} };`);
    await page.goto(pageUrl('settings.html'));
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bg === 'rgb(33, 37, 41)', 'app colours win: ' + bg);
    // Screen-reader-only text must not be visible.
    const hidden = await page.$eval('#accountStatus', e => { const r = e.getBoundingClientRect(); return r.width <= 1 && r.height <= 1; });
    expect(hidden, 'screen-reader text is hidden');
    await shot(page, 'settings-dark');

    await page.setViewportSize({ width: 360, height: 780 });
    const wide = await page.evaluate(() => document.documentElement.scrollWidth);
    expect(wide <= 360, 'no sideways scrolling at 360 px: ' + wide);
    expect(page.errors.length === 0, 'script errors: ' + page.errors.join('; '));
});
