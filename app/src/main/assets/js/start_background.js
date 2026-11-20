// start_background.js — injected into the dashboard by the app (web/StartBackground), in dark mode only.
//
// Stops the white flash while the dashboard starts. Its HTML is an empty shell (<div id="root"></div>) and its CSS
// paints <body> with var(--bs-body-bg), which is WHITE until the dashboard's code has loaded and written
// <html data-bs-theme="dark"> (seen in its main.*.js, 2026-11-20). Measured in Chromium in dark mode: white for about
// 1 to 1.5 s, then #212529.
//
// This adds one CSS rule: while <html> has no data-bs-theme, paint the app's dark colour (the argument, e.g.
// "#1C1F22"). The moment the site picks its theme the rule stops matching (and is removed), so the site's own
// colours take over; if the user chose "light" inside the dashboard, it switches to white as usual.
//
// Safety: a page that shows content but never picks a theme would have the site's dark light-theme text on our dark
// background. So once #root has content, the rule gets GRACE_MS more and is then removed whatever happens. (The
// grace is needed: measured on the live site, content can appear about 10 ms before the theme attribute.)
//
// Runs at document start (before the page's own code). At that moment even <html> may not exist yet (measured in
// Chromium: document.documentElement is null), hence the observer. It stops as soon as the rule is gone.

(function(color) {
    if (document.getElementById('__dnsm-start-bg')) { return; }
    var GRACE_MS = 500;
    var style = document.createElement('style');
    style.id = '__dnsm-start-bg';
    style.textContent = 'html:not([data-bs-theme]),html:not([data-bs-theme]) body{background-color:' + color
        + '!important}';
    var added = false;
    var timer = null;
    var watch = new MutationObserver(check);

    function done() {
        watch.disconnect();
        clearTimeout(timer);
        style.remove();
    }

    function check() {
        var html = document.documentElement;
        if (!html) { return; }
        if (!added) {
            (document.head || html).appendChild(style);
            added = true;
        }
        if (html.hasAttribute('data-bs-theme')) { done(); return; }
        var root = document.getElementById('root');
        if (timer === null && root && root.firstChild) { timer = setTimeout(done, GRACE_MS); }
    }

    watch.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-bs-theme'] });
    check();
})
