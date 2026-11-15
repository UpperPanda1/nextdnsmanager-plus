# Page checks

Development-time checks for the app's own HTML pages and injected scripts. Each `.mjs` file opens a page in
Chromium with stand-ins for the Android bridges, clicks through it and fails with a clear message if something is
wrong. They test the pages' own logic, not the Android side (that needs a phone). CI runs them on every push.

| Check | What it proves |
|---|---|
| `settings-accounts.mjs` | Add, rename, re-order, remove, sign out, open; numbering; last account protected; names shown as text |
| `settings-options.mjs` | Theme (White/Black/Match system), Pinch to zoom, Accounts kept loaded, App lock, Clear cache, links, sub-screens, Back order |
| `settings-permissions.mjs` | Settings → Permissions lists exactly the manifest's permissions |
| `settings-dark.mjs` | The app's colours win over the page defaults; no sideways scrolling at 360 px |
| `about.mjs` | Name, version, icon, repository links, Report an issue, Star, Back, light and dark |
| `zoom-viewport.mjs` | `zoom_viewport.js` lifts the dashboard's zoom ban, also for tags added later |

Run all:

```bash
cd tools/page-checks && npm install && npx playwright install chromium && npm run check
```

In Claude's cloud sandbox (Chromium and Playwright are pre-installed):

```bash
cd tools/page-checks && ln -sfn /opt/node-tools/node_modules node_modules && CHROMIUM_PATH=/opt/pw-browsers/chromium node run-all.mjs; rm node_modules
```

Screenshots land in `screenshots/` (git-ignored). Adding a check: copy one, keep it standalone, use `run()` and
`expect()` from `common.mjs`.
