// Settings → Appearance, Behaviour, Security, Data, About & legal: every switch and dialog sends the right call,
// the theme repaints at once, the app lock switch waits for the app's answer, and Back closes things in order.
import { run, phonePage, pageUrl, expect, shot, SETTINGS_BRIDGES } from './common.mjs';

await run(async (browser) => {
    const page = await phonePage(browser);
    await page.addInitScript(SETTINGS_BRIDGES);
    await page.goto(pageUrl('settings.html'));
    const calls = () => page.evaluate(() => calls);

    // The service name is filled in everywhere (it comes from site/Site in the app).
    expect(await page.$$eval('.svc', s => s.every(x => x.textContent === 'NextDNS')), 'service name everywhere');
    expect(await page.$eval('#aboutSub', s => s.textContent).then(t => t.startsWith('Version 0.1.0')), 'version shown');

    // Theme: three choices named as decided (White / Black / Match system); the page repaints with the answer.
    expect(await page.$eval('#themeSub', s => s.textContent) === 'Match system', 'theme row');
    await page.click('#themeRow');
    const names = await page.$$eval('.radio-row .title', t => t.map(x => x.textContent));
    expect(JSON.stringify(names) === JSON.stringify(['Match system', 'White', 'Black']), 'theme choices: ' + names);
    await page.click('.radio-row:has-text("Black")');
    expect(await page.$eval('#themeSub', s => s.textContent) === 'Black', 'theme row updated');
    expect(await page.$eval('#app-theme', s => s.textContent.includes('#212529')), 'new colours applied');
    expect(await page.evaluate(() => window.handleBack()) === true, 'Back closes the dialog first');

    // Pinch to zoom: a tap anywhere on the row flips the switch.
    await page.click('text=Pinch to zoom');
    expect((await calls()).some(c => c[0] === 'pinch' && c[1] === true), 'pinch sent');

    // Accounts kept loaded: 1–10.
    await page.click('#liveTabsRow');
    expect(await page.$$eval('.number-grid button', b => b.length) === 10, 'ten choices');
    await page.click('.number-grid button:has-text("5")');
    expect(await page.$eval('#liveTabsSub', s => s.textContent) === '5 accounts', 'live tabs row');
    expect((await calls()).some(c => c[0] === 'liveTabs' && c[1] === 5), 'live tabs sent');

    // App lock: the switch only moves once the app answers (after the phone's unlock sheet).
    await page.evaluate(() => document.getElementById('app_lock').click());
    expect(await page.$eval('#app_lock', b => !b.checked && b.disabled), 'waits for the app');
    await page.waitForFunction(() => document.getElementById('app_lock').checked && !document.getElementById('app_lock').disabled);
    expect((await calls()).some(c => c[0] === 'appLock' && c[1] === true), 'app lock asked');
    // The app refuses (no screen lock on the phone): the switch goes back and says why.
    await page.evaluate(() => window.onAppLock(false, 'Set a screen lock first.'));
    expect(await page.$eval('#app_lock', b => !b.checked), 'refused → off');
    expect(await page.$eval('#snackbar', s => s.textContent.includes('Set a screen lock first.')), 'reason shown');

    // Clear cache: asks, then reports the new size.
    await page.waitForFunction(() => document.getElementById('cacheSize').textContent.startsWith('Now 5.0 MB'));
    await page.click('#clearCacheRow');
    expect(await page.isVisible('.dialog:has-text("about 5.0 MB")'), 'dialog says how much');
    await page.click('.dialog .actions button:last-child');
    await page.waitForFunction(() => document.getElementById('cacheSize').textContent.startsWith('Now 200 KB'));
    expect((await calls()).some(c => c[0] === 'clearCache'), 'clear sent');

    // Legal links go by name only; sub-screens open and Back returns.
    await page.click('text=NextDNS privacy policy');
    await page.click('text=NextDNS user agreement');
    const links = (await calls()).filter(c => c[0] === 'link').map(c => c[1]);
    expect(JSON.stringify(links) === JSON.stringify(['service_privacy', 'service_terms']), 'links: ' + links);
    for (const view of ['privacy', 'permissions', 'disclaimer']) {
        await page.evaluate(v => openSubView(v), view);
        expect(await page.isVisible('#' + view + 'View') && await page.isHidden('#mainView'), view + ' opens');
        await shot(page, 'settings-' + view);
        expect(await page.evaluate(() => window.handleBack()) === true, view + ': Back returns');
        expect(await page.isVisible('#mainView'), view + ': back on the main screen');
    }
    // Nothing left to close: Back lets the window go.
    expect(await page.evaluate(() => window.handleBack()) === false, 'Back closes Settings at the top');

    await page.click('text=About NextDNS Manager+');
    expect((await calls()).some(c => c[0] === 'about'), 'about opened');

    expect(page.errors.length === 0, 'script errors: ' + page.errors.join('; '));
});
