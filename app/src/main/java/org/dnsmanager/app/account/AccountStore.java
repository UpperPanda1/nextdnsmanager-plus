package org.dnsmanager.app.account;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The list of accounts and every rule about it (PROJECT.md §5.2). Saved as one JSON text in the
 * settings file under {@link #PREF_KEY}.
 *
 * <p><b>Rules</b></p>
 * <ul>
 *   <li>There is always at least one account. The list order is the order of the account menu, and
 *       an account's number (the badge in the top bar) is its place in this list.</li>
 *   <li>One account at most has the id {@link Account#MAIN_ID}: it uses WebView's default profile.
 *       A new account takes that id if it is free, otherwise a random one.</li>
 *   <li>More than one account needs a WebView that supports profiles ({@link #canAdd}).</li>
 * </ul>
 *
 * <p>Only the load/save helpers touch Android; everything else is plain Java, checked by
 * AccountStoreTest. All methods are synchronized: the Settings page calls in from its own thread.</p>
 */
public final class AccountStore {

    public static final String PREF_KEY = "accounts";
    public static final int MAX_NAME_LENGTH = 40;
    /** A sane upper limit; every loaded account is a whole browser tab. */
    public static final int MAX_ACCOUNTS = 20;
    private static final int FORMAT_VERSION = 1;
    private static final String DEFAULT_NAME = "Account";

    private final List<Account> accounts = new ArrayList<>();

    private AccountStore() {
    }

    public static AccountStore load(SharedPreferences prefs) {
        return fromJson(prefs.getString(PREF_KEY, null));
    }

    public synchronized void save(SharedPreferences prefs) {
        prefs.edit().putString(PREF_KEY, toJson()).apply();
    }

    /**
     * Reads the saved list. Anything unreadable or broken (no text, damaged JSON, entries without an
     * id, the same id twice) is dropped, and an empty result becomes the one starting account, so the
     * app always has a valid list.
     */
    public static AccountStore fromJson(String json) {
        AccountStore store = new AccountStore();
        if (json != null) {
            try {
                JSONArray list = new JSONObject(json).getJSONArray("accounts");
                Set<String> seenIds = new HashSet<>();
                for (int i = 0; i < list.length() && store.accounts.size() < MAX_ACCOUNTS; i++) {
                    JSONObject o = list.optJSONObject(i);
                    String id = o == null ? "" : o.optString("id", "");
                    if (id.isEmpty() || !id.matches("[A-Za-z0-9-]{1,40}") || !seenIds.add(id)) {
                        continue;
                    }
                    String name = cleanName(o.optString("name", ""));
                    store.accounts.add(new Account(id, name.isEmpty() ? store.defaultNameLocked() : name));
                }
            } catch (Exception e) {
                store.accounts.clear();
            }
        }
        if (store.accounts.isEmpty()) {
            store.accounts.add(new Account(Account.MAIN_ID, DEFAULT_NAME + " 1"));
        }
        return store;
    }

    public synchronized String toJson() {
        try {
            JSONArray list = new JSONArray();
            for (Account a : accounts) {
                list.put(new JSONObject().put("id", a.id).put("name", a.name));
            }
            return new JSONObject().put("v", FORMAT_VERSION).put("accounts", list).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return a copy of the list, in menu order. */
    public synchronized List<Account> all() {
        return Collections.unmodifiableList(new ArrayList<>(accounts));
    }

    public synchronized Account find(String id) {
        int index = indexOfLocked(id);
        return index < 0 ? null : accounts.get(index);
    }

    /** @return the first account in the list (there always is one). */
    public synchronized Account first() {
        return accounts.get(0);
    }

    /** @return the account's number as shown on its badge: 1 for the first; 0 if it isn't in the list. */
    public synchronized int numberOf(String id) {
        return indexOfLocked(id) + 1;
    }

    /**
     * @param profilesSupported web/Profiles.isSupported(), passed in so this class stays plain Java
     * @return true if one more account may be added
     */
    public synchronized boolean canAdd(boolean profilesSupported) {
        return profilesSupported && accounts.size() < MAX_ACCOUNTS;
    }

    /**
     * Adds an account at the end of the list.
     *
     * @param name  the user's name for it; empty = "Account N"
     * @param newId a fresh id from {@link #newId()} (passed in so tests can choose it)
     * @return the new account, or null if it can't be added
     */
    public synchronized Account add(String name, String newId, boolean profilesSupported) {
        if (!canAdd(profilesSupported) || newId == null || !newId.matches("[A-Za-z0-9-]{1,40}")) {
            return null;
        }
        // The default profile is free again after its account was removed: reuse it first.
        String id = indexOfLocked(Account.MAIN_ID) < 0 ? Account.MAIN_ID : newId;
        if (indexOfLocked(id) >= 0) {
            return null;
        }
        String clean = cleanName(name);
        Account added = new Account(id, clean.isEmpty() ? defaultNameLocked() : clean);
        accounts.add(added);
        return added;
    }

    public synchronized boolean rename(String id, String name) {
        int index = indexOfLocked(id);
        String clean = cleanName(name);
        if (index < 0 || clean.isEmpty()) {
            return false;
        }
        accounts.set(index, accounts.get(index).withName(clean));
        return true;
    }

    /** Removes an account. The last one can't be removed. @return the removed account, or null */
    public synchronized Account remove(String id) {
        int index = indexOfLocked(id);
        if (index < 0 || accounts.size() <= 1) {
            return null;
        }
        return accounts.remove(index);
    }

    /**
     * Puts the accounts into a new order.
     *
     * @param ids every account id exactly once, in the wanted order
     * @return false (nothing changed) if the ids aren't exactly the current accounts
     */
    public synchronized boolean reorder(List<String> ids) {
        if (ids == null || ids.size() != accounts.size()) {
            return false;
        }
        List<Account> inNewOrder = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            int index = id == null ? -1 : indexOfLocked(id);
            if (index < 0 || !seen.add(id)) {
                return false; // unknown or listed twice
            }
            inNewOrder.add(accounts.get(index));
        }
        accounts.clear();
        accounts.addAll(inNewOrder);
        return true;
    }

    /** @return a new random account id, e.g. "a-3f9c2b1d". */
    public static String newId() {
        return "a-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Tidies a name typed by the user: line breaks and control characters become spaces, several
     * spaces become one, and it is cut to {@link #MAX_NAME_LENGTH} characters (whole characters,
     * never half an emoji).
     */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean lastWasSpace = true; // true = drop spaces at the start
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            boolean space = Character.isWhitespace(cp) || Character.isISOControl(cp)
                    || Character.isSpaceChar(cp);
            if (space) {
                if (!lastWasSpace) {
                    sb.append(' ');
                }
                lastWasSpace = true;
            } else {
                sb.appendCodePoint(cp);
                lastWasSpace = false;
            }
        }
        String s = sb.toString().trim();
        if (s.codePointCount(0, s.length()) > MAX_NAME_LENGTH) {
            s = s.substring(0, s.offsetByCodePoints(0, MAX_NAME_LENGTH)).trim();
        }
        return s;
    }

    /** @return "Account N" with the smallest N (from 1) that no account is called yet. */
    private String defaultNameLocked() {
        Set<String> used = new HashSet<>();
        for (Account a : accounts) {
            used.add(a.name);
        }
        for (int n = 1; ; n++) {
            String candidate = DEFAULT_NAME + " " + n;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
    }

    private int indexOfLocked(String id) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
