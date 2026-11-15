// Settings → Accounts: one list for everything. Adds, renames, re-orders (keyboard), removes and signs out
// through the page's own buttons and checks what the page sent to the app and what it shows afterwards.
import { run, phonePage, pageUrl, expect, shot, SETTINGS_BRIDGES } from './common.mjs';

await run(async (browser) => {
    const page = await phonePage(browser);
    await page.addInitScript(SETTINGS_BRIDGES);
    await page.goto(pageUrl('settings.html'));

    const rows = () => page.$$eval('#accountList .row', rs => rs.map(r => r.querySelector('.badge').textContent + ' ' + r.querySelector('.title').textContent));
    expect(JSON.stringify(await rows()) === JSON.stringify(['1 Home']), 'one account at start: ' + await rows());
    // With one account, Remove is greyed out and the drag handle is off.
    expect(await page.$eval('#accountList .drag-handle', b => b.disabled), 'handle off with one account');
    await page.click('#accountList .row [aria-label^="Options"]');
    expect(await page.$eval('.sheet .item.danger', b => b.disabled), 'Remove greyed out for the last account');
    await page.keyboard.press('Escape');
    await page.evaluate(() => window.handleBack());

    // Add two accounts: one named, one with an empty name (gets the default).
    for (const name of ['Work', '']) {
        await page.click('#addAccountRow');
        await page.fill('.dialog input', name);
        await page.click('.dialog .actions button:last-child');
    }
    expect(JSON.stringify(await rows()) === JSON.stringify(['1 Home', '2 Work', '3 Account 3']), 'after adding: ' + await rows());
    expect(await page.isVisible('#snackbar.show'), 'snackbar after adding');

    // Rename through the sheet; an empty name is refused and the dialog stays.
    await page.click('#accountList .row:nth-child(3) [aria-label^="Options"]');
    await page.click('.sheet .item:has-text("Rename")');
    await page.fill('.dialog input', '   ');
    await page.click('.dialog .actions button:last-child');
    expect(await page.isVisible('.dialog'), 'empty name keeps the dialog open');
    await page.fill('.dialog input', 'Parents');
    await page.click('.dialog .actions button:last-child');
    expect((await rows())[2] === '3 Parents', 'renamed: ' + await rows());

    // Re-order with the keyboard on the handle (the drag uses the same save path).
    await page.focus('#accountList .row:nth-child(3) .drag-handle');
    await page.keyboard.press('ArrowUp');
    expect(JSON.stringify(await rows()) === JSON.stringify(['1 Home', '2 Parents', '3 Work']), 'after moving up: ' + await rows());
    const reorder = (await page.evaluate(() => calls)).find(c => c[0] === 'reorder');
    expect(reorder && reorder[1] === '["main","a-2","a-1"]', 'order sent to the app: ' + JSON.stringify(reorder));
    expect(await page.$eval('#accountStatus', s => s.textContent) === 'Parents: position 2 of 3', 'screen reader told the new place');

    // Remove asks first, then the list shrinks and renumbers.
    await page.click('#accountList .row:nth-child(2) [aria-label^="Options"]');
    await page.click('.sheet .item.danger');
    expect(await page.isVisible('.dialog:has-text("Remove “Parents”?")'), 'remove asks first');
    await page.click('.dialog .actions button.danger');
    expect(JSON.stringify(await rows()) === JSON.stringify(['1 Home', '2 Work']), 'after removing: ' + await rows());

    // Sign out asks first and keeps the account.
    await page.click('#accountList .row:nth-child(2) [aria-label^="Options"]');
    await page.click('.sheet .item:has-text("Sign out")');
    await page.click('.dialog .actions button:last-child');
    expect((await page.evaluate(() => calls)).some(c => c[0] === 'signOut' && c[1] === 'a-1'), 'sign out sent');
    expect((await rows()).length === 2, 'sign out keeps the account');

    // Tapping a row opens that account.
    await page.click('#accountList .row:nth-child(2) .title');
    expect((await page.evaluate(() => calls)).some(c => c[0] === 'open' && c[1] === 'a-1'), 'row opens the account');

    // A name with HTML in it is shown as typed, never run.
    await page.click('#addAccountRow');
    await page.fill('.dialog input', '<img src=x onerror=alert(1)>');
    await page.click('.dialog .actions button:last-child');
    expect((await rows())[2] === '3 <img src=x onerror=alert(1)>', 'HTML shown as text');
    expect(await page.$$eval('#accountList img', i => i.length) === 0, 'no element made from a name');

    await shot(page, 'settings-accounts');
    expect(page.errors.length === 0, 'script errors: ' + page.errors.join('; '));
});
