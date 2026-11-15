package org.dnsmanager.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/** Which loaded tabs close first when there are too many ({@link TabLru}). */
public class TabLruTest {

    @Test
    public void nothingClosedWhileUnderTheLimit() {
        TabLru lru = new TabLru();
        lru.touch("main");
        lru.touch("a-1");
        lru.touch("a-2");
        assertEquals(Collections.emptyList(), lru.idsToClose(3));
    }

    @Test
    public void leastRecentlyUsedIsClosedFirst() {
        TabLru lru = new TabLru();
        lru.touch("main");
        lru.touch("a-1");
        lru.touch("a-2");
        lru.touch("a-3");
        assertEquals(Arrays.asList("main"), lru.idsToClose(3));
    }

    @Test
    public void usingATabAgainMakesItRecent() {
        TabLru lru = new TabLru();
        lru.touch("main");
        lru.touch("a-1");
        lru.touch("a-2");
        lru.touch("main");          // main used again → a-1 is now the oldest
        lru.touch("a-3");
        assertEquals(Arrays.asList("a-1"), lru.idsToClose(3));
    }

    @Test
    public void theCurrentTabIsNeverClosed() {
        TabLru lru = new TabLru();
        lru.touch("main");
        lru.touch("a-1");           // current (touched last)
        assertEquals(Arrays.asList("main"), lru.idsToClose(1));
        // Even with one tab and limit 1 the current one stays.
        TabLru single = new TabLru();
        single.touch("main");
        assertEquals(Collections.emptyList(), single.idsToClose(1));
    }

    @Test
    public void limitIsClampedToOneToTen() {
        TabLru lru = new TabLru();
        for (String id : new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"}) {
            lru.touch(id);
        }
        // 99 is treated as 10 → the two oldest of 12 close.
        assertEquals(Arrays.asList("a", "b"), lru.idsToClose(99));
        // 0 is treated as 1 → everything except the current "l" closes.
        assertEquals(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"), lru.idsToClose(0));
    }

    @Test
    public void removeForgetsATab() {
        TabLru lru = new TabLru();
        lru.touch("main");
        lru.touch("a-1");
        assertTrue(lru.contains("a-1"));
        lru.remove("a-1");
        assertFalse(lru.contains("a-1"));
        assertEquals(Collections.emptyList(), lru.idsToClose(1));
    }
}
