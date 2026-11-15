package org.dnsmanager.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The text on an account's badge ({@link AccountBadge}). */
public class AccountBadgeTest {

    @Test
    public void theBadgeShowsTheNumber() {
        assertEquals("1", AccountBadge.label(1));
        assertEquals("12", AccountBadge.label(12));
        assertEquals("?", AccountBadge.label(0)); // not in the list (yet)
    }
}
