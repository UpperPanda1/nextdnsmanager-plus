package org.dnsmanager.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The rules that keep a page from loading into a container that is still being emptied ({@link WipeGate}). */
public class WipeGateTest {

    @Test
    public void closedWhileDeletingOpenAfterwards() {
        WipeGate gate = new WipeGate();
        assertFalse(gate.blocks("acct_a"));
        gate.started("acct_a");
        assertTrue(gate.blocks("acct_a"));
        assertFalse("other containers stay open", gate.blocks("acct_b"));
        assertNull("nobody asked, nothing to load", gate.finished("acct_a"));
        assertFalse(gate.blocks("acct_a"));
    }

    @Test
    public void anAccountAskedForMeanwhileIsLoadedWhenDone() {
        WipeGate gate = new WipeGate();
        gate.started("Default");
        gate.wantShown("Default", "main");
        assertEquals("main", gate.finished("Default"));
        assertNull("asked for once, loaded once", gate.finished("Default"));
    }

    @Test
    public void overlappingDeletionsOpenOnlyAfterTheLast() {
        WipeGate gate = new WipeGate();
        gate.started("acct_a");
        gate.started("acct_a"); // signed out twice quickly
        gate.wantShown("acct_a", "a-1");
        assertNull("the second deletion is still running", gate.finished("acct_a"));
        assertTrue(gate.blocks("acct_a"));
        assertEquals("a-1", gate.finished("acct_a"));
        assertFalse(gate.blocks("acct_a"));
    }

    @Test
    public void askingWhileOpenRemembersNothing() {
        WipeGate gate = new WipeGate();
        gate.wantShown("acct_a", "a-1"); // not being deleted: shown at once by the caller
        gate.started("acct_a");
        assertNull(gate.finished("acct_a"));
    }

    @Test
    public void finishingAnUnknownKeyIsHarmless() {
        WipeGate gate = new WipeGate();
        assertNull(gate.finished("acct_never"));
        assertFalse(gate.blocks("acct_never"));
    }

    @Test
    public void joinRunsOnceAfterAllParts() {
        int[] runs = {0};
        WipeGate.Join join = new WipeGate.Join(2, () -> runs[0]++);
        join.partDone();
        assertEquals("one deletion still running", 0, runs[0]);
        join.partDone();
        assertEquals(1, runs[0]);
        join.partDone();
        join.force(); // the safety time limit firing later changes nothing
        assertEquals(1, runs[0]);
    }

    @Test
    public void joinTimeLimitRunsItIfACallbackNeverComes() {
        int[] runs = {0};
        WipeGate.Join join = new WipeGate.Join(2, () -> runs[0]++);
        join.partDone();
        join.force();
        assertEquals(1, runs[0]);
        join.partDone(); // the late callback is ignored
        assertEquals(1, runs[0]);
    }
}
