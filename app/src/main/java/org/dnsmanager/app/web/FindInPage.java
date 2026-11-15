package org.dnsmanager.app.web;

import android.view.KeyEvent;


/**
 * Rules for "Find in page" (three-dots menu, or Ctrl+F on a keyboard). Plain Java, tested by
 * FindInPageTest.
 *
 * <p>The search itself is the app's find bar ({@code ui/FindBar}), which uses WebView's built-in
 * find: Chromium's own find-in-page engine, the same one behind Chrome's "Find in page"
 * ({@code WebView.findAllAsync}).</p>
 */
public final class FindInPage {

    private FindInPage() {
    }

    /**
     * @return true for Ctrl+F being pressed (a hardware keyboard, or the key the search button sends). Ctrl+Shift+F
     *         and Ctrl+Alt+F are left to the page: they mean other things on some sites.
     */
    public static boolean isFindShortcut(int action, int keyCode, int metaState) {
        return action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_F
                && (metaState & KeyEvent.META_CTRL_ON) != 0
                && (metaState & (KeyEvent.META_SHIFT_ON | KeyEvent.META_ALT_ON)) == 0;
    }

    /**
     * The style sheet that colours the app's find bar matches (assets/js/find_highlight.js): every match in one colour
     * pair, the current match in another. Chromium/WebView 144+ lets a page style its find highlights with the CSS
     * pseudo-elements {@code ::search-text} and {@code ::search-text:current} (https://chromestatus.com/feature/5195073796177920);
     * older WebViews ignore the rules and keep Chromium's yellow/orange.
     *
     * @return the CSS, or "" if a colour isn't exactly "#RRGGBB" (the text goes into the page, so nothing else may)
     */
    public static String highlightCss(String allBackground, String allText, String currentBackground,
                                      String currentText) {
        for (String colour : new String[] { allBackground, allText, currentBackground, currentText }) {
            if (colour == null || !colour.matches("#[0-9A-Fa-f]{6}")) {
                return "";
            }
        }
        return "::search-text{background-color:" + allBackground + ";color:" + allText + "}"
                + "::search-text:current{background-color:" + currentBackground + ";color:" + currentText + "}";
    }

    /**
     * The find bar's count, e.g. "3/12".
     *
     * @param activeOrdinal the current match as WebView reports it: counted from 0
     * @param total         the number of matches found so far
     * @param done          false while Chromium is still counting (it counts in small slices so the page never
     *                      freezes, and reports partial totals on the way)
     * @return "current/total", "0/0" when nothing was found, or "" while nothing was found yet
     */
    public static String countText(int activeOrdinal, int total, boolean done) {
        if (total <= 0) {
            return done ? "0/0" : "";
        }
        return (activeOrdinal + 1) + "/" + total;
    }
}
