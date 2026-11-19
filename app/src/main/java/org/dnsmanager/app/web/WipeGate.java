package org.dnsmanager.app.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps an account's page from loading while its container is still being emptied ("Sign out" or "Remove").
 *
 * <p><b>Why:</b> emptying a WebView profile is asynchronous: {@code WebStorageCompat.deleteBrowsingData} and
 * {@code CookieManager.removeAllCookies} return at once and finish in the background. The androidx docs say the
 * deletion "is not an atomic operation, so it may also delete data that is stored during deletion". If the same
 * container were opened again in that moment (sign out and open the account again at once, or remove the first
 * account and add a new one, which reuses its container: account/AccountStore.add), the page's fresh cookies could be
 * deleted half-way. So {@link TabManager#show} asks {@link #blocks} first and, while it is closed, shows the
 * "Unloaded" screen; when the deletion is done, {@link #finished} says which account to load.</p>
 *
 * <p>Keys are profile keys ({@link Profiles#keyOf}). A counter per key copes with two deletions of the same container
 * overlapping (sign out twice quickly): the gate opens only when the last one is done. Plain Java (no Android classes),
 * so WipeGateTest checks the rules on the computer. Main thread only: no locking.</p>
 */
public final class WipeGate {

    /** Profile key → how many deletions of it are still running. */
    private final Map<String, Integer> running = new HashMap<>();
    /** Profile key → the account that asked to be shown while its container was closed. */
    private final Map<String, String> wanted = new HashMap<>();

    /** A deletion of this container starts. */
    public void started(String key) {
        Integer n = running.get(key);
        running.put(key, n == null ? 1 : n + 1);
    }

    /** @return true while a deletion of this container is running (its page must not load). */
    public boolean blocks(String key) {
        return running.containsKey(key);
    }

    /** An account was asked for while its container was closed: load it when the gate opens. */
    public void wantShown(String key, String accountId) {
        if (blocks(key)) {
            wanted.put(key, accountId);
        }
    }

    /**
     * A deletion of this container is done.
     *
     * @return the id of the account to load now (it was asked for meanwhile), or null: another deletion of the same
     *         container is still running, nobody asked, or the key wasn't being deleted at all
     */
    public String finished(String key) {
        Integer n = running.get(key);
        if (n == null) {
            return null;
        }
        if (n > 1) {
            running.put(key, n - 1);
            return null;
        }
        running.remove(key);
        return wanted.remove(key);
    }

    /**
     * Runs {@code done} once, after {@code parts} calls to {@link #partDone()}, or earlier through {@link #force()}
     * (the safety time limit, so a callback WebView never sends can't keep an account closed for ever). Calls after
     * that are ignored. Main thread only.
     */
    public static final class Join {
        private final Runnable done;
        private int left;
        private boolean ran;

        public Join(int parts, Runnable done) {
            this.left = parts;
            this.done = done;
        }

        public void partDone() {
            if (!ran && --left <= 0) {
                run();
            }
        }

        public void force() {
            if (!ran) {
                run();
            }
        }

        private void run() {
            ran = true;
            done.run();
        }
    }
}
