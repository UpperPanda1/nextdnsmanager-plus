// Settings → Permissions must list exactly the permissions in AndroidManifest.xml: a permission added to the app
// without telling the user (or a stale row) fails this check.
import { readFileSync } from 'fs';
import path from 'path';
import { run, phonePage, pageUrl, expect, ROOT, SETTINGS_BRIDGES } from './common.mjs';

await run(async (browser) => {
    const manifest = readFileSync(path.join(ROOT, 'app/src/main/AndroidManifest.xml'), 'utf8');
    const declared = [...manifest.matchAll(/<uses-permission\s+android:name="([^"]+)"/g)].map(m => m[1]).sort();
    const page = await phonePage(browser);
    await page.addInitScript(SETTINGS_BRIDGES);
    await page.goto(pageUrl('settings.html'));
    const listed = (await page.$$eval('#permissionList [data-permission]', r => r.map(x => x.dataset.permission))).sort();
    expect(declared.length > 0, 'manifest read');
    expect(JSON.stringify(declared) === JSON.stringify(listed),
        'manifest ' + JSON.stringify(declared) + ' vs page ' + JSON.stringify(listed));
});
