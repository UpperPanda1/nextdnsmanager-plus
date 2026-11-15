package org.dnsmanager.app.lock;

/**
 * When the app lock asks for the phone's unlock again. Plain Java (LockRulesTest); lock/AppLock does
 * the Android part.
 *
 * <p>The app is locked when it is opened fresh (after Android ended its process, or the phone was
 * restarted), and when the user comes back after it was out of sight for longer than
 * {@link #GRACE_MS}. A quick trip to another app and back (copying a DNS address, say) does not ask
 * again.</p>
 */
public final class LockRules {

    /** How long the app may be out of sight before it locks again: one minute. */
    public static final long GRACE_MS = 60_000;

    /** "Not in the background" marker for {@code leftAtMs}. */
    public static final long NOT_LEFT = -1;

    private LockRules() {
        // Only static methods.
    }

    /**
     * @param enabled  the App lock setting
     * @param unlocked whether the user unlocked since this app process started
     * @param leftAtMs when the screen went out of sight (same clock as {@code nowMs}), or {@link #NOT_LEFT}
     * @return true if the lock screen must show now
     */
    public static boolean mustLock(boolean enabled, boolean unlocked, long leftAtMs, long nowMs) {
        if (!enabled) {
            return false;
        }
        if (!unlocked) {
            return true;
        }
        return leftAtMs != NOT_LEFT && nowMs - leftAtMs > GRACE_MS;
    }
}
