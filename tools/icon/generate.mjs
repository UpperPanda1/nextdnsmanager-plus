// generate.mjs — makes every icon file of the app from ONE drawing, artwork/icon.svg.
//
// Writes (and nothing else; the "App icon" workflow checks that):
//   app/src/main/res/drawable/ic_launcher_background.xml   background layer (vector)
//   app/src/main/res/drawable/ic_launcher_foreground.xml   foreground layer (vector), also the themed icon
//   fastlane/metadata/android/en-US/images/icon.png        512 x 512 store icon (F-Droid)
//   tools/icon/preview.png                                 the icon in a circle, a rounded square and themed (git-ignored)
// res/mipmap-anydpi/ic_launcher.xml (which puts the two layers together) is fixed and not generated.
//
// Android can't use SVG files directly; it has its own "vector drawable" XML with the same path language
// (the "d" attribute), so paths are copied across unchanged. Chromium (through Playwright) measures the
// foreground, so a drawing that reaches outside the visible "safe zone" is refused, and draws the PNGs.
//
// Run: cd tools/icon && npm install && npx playwright install chromium && npm run generate
// (Claude's sandbox: ln -sfn /opt/node-tools/node_modules node_modules && CHROMIUM_PATH=/opt/pw-browsers/chromium node generate.mjs)
import { chromium } from 'playwright';
import { readFileSync, writeFileSync, mkdirSync } from 'fs';
import { fileURLToPath } from 'url';
import path from 'path';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const SVG_FILE = path.join(ROOT, 'artwork/icon.svg');
const RES = path.join(ROOT, 'app/src/main/res/drawable');
const STORE_ICON = path.join(ROOT, 'fastlane/metadata/android/en-US/images/icon.png');
const SAFE = { min: 21, max: 87 }; // the 66 dp circle's square; launchers may cut anything outside

function fail(message) {
    console.error('ERROR: ' + message);
    process.exit(1);
}

// Comments are dropped first: the SVG's own instructions show example tags that must not be read as the drawing.
const svg = readFileSync(SVG_FILE, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
const attr = (tag, name) => { const m = tag.match(new RegExp('\\s' + name + '="([^"]*)"')); return m ? m[1] : null; };
const colour = (value, what) => {
    if (!/^#[0-9A-Fa-f]{6}$/.test(value || '')) fail(what + ' must be a colour like #0056B3, found "' + value + '"');
    return '#FF' + value.slice(1).toUpperCase();
};

if (attr(svg.match(/<svg[^>]*>/)[0], 'viewBox') !== '0 0 108 108') fail('the <svg> needs viewBox="0 0 108 108"');

const bgTag = (svg.match(/<rect[^>]*id="background"[^>]*\/?>/) || [])[0] || fail('missing <rect id="background" …/>');
const background = colour(attr(bgTag, 'fill'), 'the background fill');

const fg = svg.match(/<g([^>]*)id="foreground"([^>]*)>([\s\S]*?)<\/g>/) || fail('missing <g id="foreground">…</g>');
const groupTag = fg[1] + fg[2];
let translate = [0, 0], scale = 1;
const transform = attr(' ' + groupTag, 'transform');
if (transform) {
    const t = transform.match(/^\s*(?:translate\(\s*([-\d.]+)[\s,]+([-\d.]+)\s*\))?\s*(?:scale\(\s*([-\d.]+)\s*\))?\s*$/);
    if (!t) fail('the foreground transform may only be "translate(x y) scale(s)", found "' + transform + '"');
    translate = [Number(t[1] || 0), Number(t[2] || 0)];
    scale = Number(t[3] || 1);
}
const inner = fg[3].replace(/<!--[\s\S]*?-->/g, '');
const paths = [...inner.matchAll(/<path\b[^>]*\/?>/g)].map(m => m[0]);
if (!paths.length) fail('the foreground has no <path>');
if (inner.replace(/<path\b[^>]*\/?>/g, '').replace(/<\/path>/g, '').trim()) fail('the foreground may only contain <path> elements');
const layers = paths.map(p => ({
    d: attr(p, 'd') || fail('a <path> has no d'),
    fill: colour(attr(p, 'fill'), 'a path fill'),
    evenOdd: attr(p, 'fill-rule') === 'evenodd',
}));

const header = (what) => `<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED from artwork/icon.svg by tools/icon/generate.mjs: ${what}. Don't edit by hand; edit the SVG and run
     the "App icon" workflow (or the generator). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
`;
const backgroundXml = header('launcher icon background') + `    <path
        android:fillColor="${background}"
        android:pathData="M0,0h108v108h-108z" />
</vector>
`;
const pathXml = (l, indent) => `${indent}<path
${indent}    android:fillColor="${l.fill}"${l.evenOdd ? `\n${indent}    android:fillType="evenOdd"` : ''}
${indent}    android:pathData="${l.d}" />
`;
const groupOpen = (scale !== 1 || translate[0] || translate[1])
    ? `    <group
        android:scaleX="${scale}"
        android:scaleY="${scale}"
        android:translateX="${translate[0]}"
        android:translateY="${translate[1]}">
` : '';
const foregroundXml = header('launcher icon foreground (also the themed icon)') + groupOpen
    + layers.map(l => pathXml(l, groupOpen ? '        ' : '    ')).join('') + (groupOpen ? '    </group>\n' : '') + '</vector>\n';

// Chromium: measure the foreground and draw the PNGs from the same SVG.
const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const page = await browser.newPage({ viewport: { width: 512, height: 512 } });
const svgBody = svg.replace(/^[\s\S]*?(<svg)/, '$1');
await page.setContent(`<body style="margin:0">${svgBody.replace('<svg', '<svg id="art" style="width:512px;height:512px;display:block"')}</body>`);
const box = await page.evaluate(() => {
    const r = document.querySelector('#foreground').getBoundingClientRect();
    const k = 108 / 512;
    return { left: r.left * k, top: r.top * k, right: r.right * k, bottom: r.bottom * k };
});
const out = Object.values(box).some(v => v < SAFE.min - 0.01 || v > SAFE.max + 0.01);
console.log('Foreground spans x ' + box.left.toFixed(2) + '–' + box.right.toFixed(2) + ', y ' + box.top.toFixed(2) + '–'
    + box.bottom.toFixed(2) + ' (safe zone ' + SAFE.min + '–' + SAFE.max + ')');
if (out) { await browser.close(); fail('the foreground reaches outside the safe zone: make it smaller'); }

mkdirSync(path.dirname(STORE_ICON), { recursive: true });
await page.screenshot({ path: STORE_ICON, clip: { x: 0, y: 0, width: 512, height: 512 } });

// Preview: circle, rounded square, and themed (one colour on a light background), as launchers show it.
const shapes = ['<circle cx="54" cy="54" r="36"/>', '<rect x="18" y="18" width="72" height="72" rx="16"/>'];
const themed = svgBody.replace(/<rect[^>]*id="background"[^>]*\/?>/, '<rect width="108" height="108" fill="#D6E4FF"/>')
    .replace(/fill="#[0-9A-Fa-f]{6}"(?=[^>]*\sd=)/g, 'fill="#0B2545"');
const clip = (art, shape, i) => art.replace(/<svg[^>]*>/, m => m + `<defs><clipPath id="s${i}">${shape}</clipPath></defs><g clip-path="url(#s${i})">`)
    .replace('</svg>', '</g></svg>').replace('<svg', '<svg style="width:200px;height:200px"');
await page.setViewportSize({ width: 660, height: 220 });
await page.setContent('<body style="margin:0;padding:10px;display:flex;gap:20px;background:#eee">'
    + clip(svgBody, shapes[0], 0) + clip(svgBody, shapes[1], 1) + clip(themed, shapes[0], 2) + '</body>');
await page.screenshot({ path: path.join(HERE, 'preview.png') });
await browser.close();

writeFileSync(path.join(RES, 'ic_launcher_background.xml'), backgroundXml);
writeFileSync(path.join(RES, 'ic_launcher_foreground.xml'), foregroundXml);
console.log('Wrote drawable/ic_launcher_background.xml, drawable/ic_launcher_foreground.xml, the store icon and preview.png');
