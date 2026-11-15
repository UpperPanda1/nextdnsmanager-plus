package org.dnsmanager.app.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.dnsmanager.app.MapPrefs;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The account list's rules ({@link AccountStore}). */
public class AccountStoreTest {

    private static List<String> ids(AccountStore store) {
        List<String> ids = new ArrayList<>();
        for (Account a : store.all()) {
            ids.add(a.id);
        }
        return ids;
    }

    @Test
    public void aNewInstallHasOneAccountInTheDefaultProfile() {
        AccountStore store = AccountStore.fromJson(null);
        assertEquals(1, store.all().size());
        Account first = store.first();
        assertEquals(Account.MAIN_ID, first.id);
        assertEquals("Account 1", first.name);
        assertTrue(first.isMain());
        assertNull("the default profile has no name", first.profileName());
    }

    @Test
    public void brokenOrEmptyTextGivesTheStartingAccount() {
        for (String json : new String[]{"", "not json", "{}", "{\"accounts\":[]}", "{\"accounts\":[{\"name\":\"no id\"}]}"}) {
            AccountStore store = AccountStore.fromJson(json);
            assertEquals(json, Arrays.asList(Account.MAIN_ID), ids(store));
        }
    }

    @Test
    public void savedListSurvivesARoundTripAndBadEntriesAreDropped() {
        String json = "{\"v\":1,\"accounts\":["
                + "{\"id\":\"main\",\"name\":\"Home\"},"
                + "{\"id\":\"a-11111111\",\"name\":\"Work\"},"
                + "{\"id\":\"a-11111111\",\"name\":\"Twice\"},"     // same id again: dropped
                + "{\"id\":\"../evil\",\"name\":\"Bad id\"},"       // not a safe profile name: dropped
                + "{\"id\":\"a-22222222\",\"name\":\"  \"}]}";      // empty name: gets a default
        AccountStore store = AccountStore.fromJson(json);
        assertEquals(Arrays.asList("main", "a-11111111", "a-22222222"), ids(store));
        assertEquals("Account 1", store.find("a-22222222").name);
        AccountStore again = AccountStore.fromJson(store.toJson());
        assertEquals(store.toJson(), again.toJson());
    }

    @Test
    public void accountsGetTheirOwnProfileName() {
        assertEquals("acct_a-11111111", new Account("a-11111111", "Work").profileName());
    }

    @Test
    public void addingNeedsProfileSupport() {
        AccountStore store = AccountStore.fromJson(null);
        assertFalse(store.canAdd(false));
        assertNull(store.add("Work", "a-11111111", false));
        assertTrue(store.canAdd(true));
        Account added = store.add("Work", "a-11111111", true);
        assertNotNull(added);
        assertEquals(Arrays.asList("main", "a-11111111"), ids(store));
        assertEquals(2, store.numberOf("a-11111111"));
    }

    @Test
    public void newAccountsGetNumberedNamesThatAreFree() {
        AccountStore store = AccountStore.fromJson(null);            // "Account 1"
        assertEquals("Account 2", store.add("", "a-1", true).name);
        assertEquals("Account 3", store.add(null, "a-2", true).name);
        store.rename("a-1", "Work");                                 // "Account 2" is free again
        assertEquals("Account 2", store.add(" ", "a-3", true).name);
    }

    @Test
    public void theDefaultProfileIsReusedAfterItsAccountWasRemoved() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("Work", "a-1", true);
        assertNotNull(store.remove(Account.MAIN_ID));
        Account added = store.add("Home", "a-2", true);
        assertEquals("the free default profile is taken first", Account.MAIN_ID, added.id);
    }

    @Test
    public void theLastAccountCanNotBeRemoved() {
        AccountStore store = AccountStore.fromJson(null);
        assertNull(store.remove(Account.MAIN_ID));
        store.add("Work", "a-1", true);
        assertNotNull(store.remove("a-1"));
        assertNull(store.remove("a-1"));        // already gone
        assertNull(store.remove("unknown"));
        assertEquals(1, store.all().size());
    }

    @Test
    public void thereIsAnUpperLimit() {
        AccountStore store = AccountStore.fromJson(null);
        for (int i = 1; i < AccountStore.MAX_ACCOUNTS; i++) {
            assertNotNull(store.add("", "a-" + i, true));
        }
        assertFalse(store.canAdd(true));
        assertNull(store.add("", "a-extra", true));
    }

    @Test
    public void addRefusesUnsafeOrDuplicateIds() {
        AccountStore store = AccountStore.fromJson(null);
        assertNull(store.add("X", null, true));
        assertNull(store.add("X", "a/b", true));
        assertNotNull(store.add("X", "a-1", true));
        assertNull("id already used", store.add("Y", "a-1", true));
    }

    @Test
    public void renameCleansTheNameAndRefusesEmptyOnes() {
        AccountStore store = AccountStore.fromJson(null);
        assertTrue(store.rename(Account.MAIN_ID, "  Home \n  office  "));
        assertEquals("Home office", store.first().name);
        assertFalse(store.rename(Account.MAIN_ID, "   "));
        assertFalse(store.rename("unknown", "Name"));
    }

    @Test
    public void namesAreCutToFortyWholeCharacters() {
        StringBuilder emoji = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            emoji.append("😀");
        }
        String clean = AccountStore.cleanName(emoji.toString());
        assertEquals(AccountStore.MAX_NAME_LENGTH, clean.codePointCount(0, clean.length()));
        assertEquals("", AccountStore.cleanName(null));
    }

    @Test
    public void reorderNeedsEveryAccountExactlyOnce() {
        AccountStore store = AccountStore.fromJson(null);
        store.add("B", "a-1", true);
        store.add("C", "a-2", true);
        assertTrue(store.reorder(Arrays.asList("a-2", "main", "a-1")));
        assertEquals(Arrays.asList("a-2", "main", "a-1"), ids(store));
        assertEquals(1, store.numberOf("a-2"));
        assertFalse("one missing", store.reorder(Arrays.asList("a-2", "main")));
        assertFalse("one twice", store.reorder(Arrays.asList("a-2", "a-2", "a-1")));
        assertFalse("unknown", store.reorder(Arrays.asList("a-2", "main", "x")));
        assertFalse(store.reorder(null));
        assertEquals("a refused order changes nothing", Arrays.asList("a-2", "main", "a-1"), ids(store));
    }

    @Test
    public void numberOfAnUnknownAccountIsZero() {
        assertEquals(0, AccountStore.fromJson(null).numberOf("nope"));
    }

    @Test
    public void newIdsAreSafeProfileNames() {
        for (int i = 0; i < 50; i++) {
            assertTrue(AccountStore.newId().matches("a-[0-9a-f]{8}"));
        }
    }

    @Test
    public void saveAndLoadUseTheSettingsFile() {
        MapPrefs prefs = new MapPrefs();
        AccountStore store = AccountStore.load(prefs);
        store.add("Work", "a-1", true);
        store.save(prefs);
        assertEquals(Arrays.asList("main", "a-1"), ids(AccountStore.load(prefs)));
    }
}
