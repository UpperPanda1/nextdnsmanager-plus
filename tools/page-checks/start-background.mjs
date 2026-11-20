// assets/js/start_background.js (dark mode, no white flash) on a page built like the NextDNS dashboard: an empty
// <div id="root">, and a body painted with var(--bs-body-bg), white until the page writes <html data-bs-theme>.
// Checks: dark while the page is waiting; the site's own colour once it picks its theme; content that appears just
// before the theme keeps the dark colour, but content with no theme at all gets the site's white after 0.5 s (its
// text would be dark-on-dark).
import { readFileSync } from 'fs';
import { run, phonePage, assetPath, expect } from './common.mjs';

const SHELL = '<!doctype html><html><head><style>'
    + ':root,[data-bs-theme=light]{--bs-body-bg:#fff}[data-bs-theme=dark]{--bs-body-bg:#212529}'
    + 'body{background-color:#fff;background-color:var(--bs-body-bg);margin:0}'
    + '</style></head><body><div id="root"></div></body></html>';
const APP_DARK = 'rgb(28, 31, 34)';   // #1C1F22, theme/Themes DARK topBar
const SITE_DARK = 'rgb(33, 37, 41)';  // #212529
const WHITE = 'rgb(255, 255, 255)';

await run(async (browser) => {
    const script = readFileSync(assetPath('js/start_background.js'), 'utf8').trim() + '("#1C1F22");'; // as StartBackground.call
    const page = await phonePage(browser, 'dark');
    await page.route('https://my.nextdns.io/', r => r.fulfill({ contentType: 'text/html', body: SHELL }));
    await page.addInitScript(script); // the app adds it at document start (web/StartBackground)
    const body = () => page.evaluate(() => getComputedStyle(document.body).backgroundColor);

    await page.goto('https://my.nextdns.io/');
    expect(await body() === APP_DARK, 'waiting: app colour, got ' + await body());

    await page.evaluate(() => document.documentElement.setAttribute('data-bs-theme', 'dark'));
    expect(await body() === SITE_DARK, 'site chose dark: its own colour, got ' + await body());

    await page.evaluate(() => document.documentElement.setAttribute('data-bs-theme', 'light'));
    expect(await body() === WHITE, 'site chose light: white, got ' + await body());

    expect(await page.$$eval('#__dnsm-start-bg', s => s.length) === 0, 'rule removed once the site chose');

    await page.goto('https://my.nextdns.io/');
    await page.evaluate(script); // run twice (e.g. re-injected): still one rule
    expect(await page.$$eval('#__dnsm-start-bg', s => s.length) === 1, 'one style element');
    await page.evaluate(() => { document.getElementById('root').textContent = 'Some text'; });
    expect(await body() === APP_DARK, 'content just before the theme: still dark, got ' + await body());
    await page.waitForTimeout(700);
    expect(await body() === WHITE, 'content and still no theme: the site\'s white, got ' + await body());
    expect(page.errors.length === 0, 'script errors: ' + page.errors.join('; '));
});
