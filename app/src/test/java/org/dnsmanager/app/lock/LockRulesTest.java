package org.dnsmanager.app.lock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the app lock asks again ({@link LockRules}). */
public class LockRulesTest {

    @Test
    public void offNeverLocks() {
        assertFalse(LockRules.mustLock(false, false, LockRules.NOT_LEFT, 0));
        assertFalse(LockRules.mustLock(false, true, 0, 10 * LockRules.GRACE_MS));
    }

    @Test
    public void aFreshStartLocks() {
        assertTrue(LockRules.mustLock(true, false, LockRules.NOT_LEFT, 0));
    }

    @Test
    public void aShortTripAwayDoesNotLockButALongOneDoes() {
        long left = 1_000_000;
        assertFalse(LockRules.mustLock(true, true, left, left + LockRules.GRACE_MS));
        assertTrue(LockRules.mustLock(true, true, left, left + LockRules.GRACE_MS + 1));
    }

    @Test
    public void stayingInTheAppDoesNotLock() {
        assertFalse(LockRules.mustLock(true, true, LockRules.NOT_LEFT, Long.MAX_VALUE));
    }
}
