// About: the app's name, version and icon arrive from the app; every GitHub link points at the project's
// repository (issue button included); the page works in light and dark.
import { run, phonePage, pageUrl, expect, shot } from './common.mjs';

const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

await run(async (browser) => {
    for (const scheme of ['light', 'dark']) {
        const page = await phonePage(browser, scheme);
        await page.addInitScript(`window.calls = [];
            window.AndroidAbout = { info: () => JSON.stringify({ name: 'NextDNS Manager+', version: '0.1.0', repo: 'https://github.com/example-owner/example-repo', author: 'Example Author', icon: '${PNG}' }),
                                   close: () => calls.push('close') };`);
        await page.goto(pageUrl('about.html'));
        expect(await page.$eval('#appName', e => e.textContent) === 'NextDNS Manager+', 'name');
        expect(await page.$eval('#version', e => e.textContent) === 'Version 0.1.0', 'version');
        expect(await page.$$eval('.hero img', i => i.length) === 1, 'icon');
        const hrefs = await page.$$eval('[data-link]', a => a.map(x => x.href));
        expect(hrefs.length === 3 && hrefs.every(h => h.startsWith('https://github.com/example-owner/example-repo')),
            'links: ' + hrefs);
        expect(hrefs.some(h => h.endsWith('/issues/new/choose')), 'report an issue');
        expect(await page.$eval('#author', e => e.textContent) === 'Example Author', 'author comes from the app');
        expect(await page.$eval('#repoName', e => e.textContent) === 'example-owner/example-repo', 'repo name comes from the app');
        expect(await page.isVisible('.star-btn'), 'star button');
        await page.click('.appbar .icon-btn');
        expect((await page.evaluate(() => calls)).includes('close'), 'back closes');
        await shot(page, 'about-' + scheme);
        expect(page.errors.length === 0, 'script errors: ' + page.errors.join('; '));
        await page.close();
    }
});
