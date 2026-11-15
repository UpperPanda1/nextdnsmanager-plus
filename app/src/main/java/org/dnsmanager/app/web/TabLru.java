package org.dnsmanager.app.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers in which order tabs (accounts) were used, and decides which ones to close when too
 * many are loaded at once.
 *
 * <p><b>Why:</b> each loaded tab is a full web page in memory (tens to hundreds of MB). Keeping a few loaded makes switching instant, but keeping all of them could make Android
 * kill the app. "LRU" = <i>Least Recently Used</i>: when over the limit, close the tab that was
 * used longest ago (default limit 3, preference {@code max_live_tabs}).</p>
 *
 * <p>The tab currently on screen (the most recently used) is never closed.</p>
 *
 * <p>Plain Java, so it's unit-tested on the computer (TabLruTest).</p>
 */
public final class TabLru {

    /**
     * Smallest and largest allowed values of the "Accounts kept loaded" setting (default 3). All tabs
     * share one page process, so many loaded tabs use a lot of memory; Settings says so, and
     * onTrimMemory still closes hidden tabs when Android asks.
     */
    public static final int MIN_LIVE_TABS = 1;
    public static final int MAX_LIVE_TABS = 10;

    // Tab ids from least recently used (index 0) to most recently used (last).
    private final List<String> order = new ArrayList<>();

    /** Marks a tab as just used (moves it to the "most recent" end). */
    public void touch(String id) {
        order.remove(id);
        order.add(id);
    }

    /** Forgets a tab (it was closed). */
    public void remove(String id) {
        order.remove(id);
    }

    /** @return true if the tab is currently counted as loaded. */
    public boolean contains(String id) {
        return order.contains(id);
    }

    /**
     * @param maxLiveTabs the user's limit; values outside 1–10 are clamped into that range
     * @return the tabs to close now, oldest first, so that at most {@code maxLiveTabs} stay loaded
     *         (the current tab always stays)
     */
    public List<String> idsToClose(int maxLiveTabs) {
        int limit = Math.max(MIN_LIVE_TABS, Math.min(MAX_LIVE_TABS, maxLiveTabs));
        List<String> toClose = new ArrayList<>();
        int excess = order.size() - limit;
        String current = order.isEmpty() ? null : order.get(order.size() - 1);
        for (String id : order) {
            if (excess <= 0) {
                break;
            }
            if (id.equals(current)) {
                continue;
            }
            toClose.add(id);
            excess--;
        }
        return toClose;
    }
}
