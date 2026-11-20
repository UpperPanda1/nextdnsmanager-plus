package org.dnsmanager.app.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StartBackgroundTest {

    @Test
    public void callsTheFunctionWithTheColourAsAQuotedString() {
        assertEquals("(function(c){})(\"#1C1F22\");", StartBackground.call("(function(c){})\n", 0xFF1C1F22));
    }

    @Test
    public void aMissingScriptAddsNothing() {
        assertNull(StartBackground.call("", 0xFF1C1F22));
        assertNull(StartBackground.call(null, 0xFF1C1F22));
    }
}
