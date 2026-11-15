package org.dnsmanager.app.account;

/**
 * One account in the app: a name the user chose and an id that never changes. Each account is a
 * separate login to the DNS service with its own cookies and site data (see web/Profiles).
 *
 * <p>Immutable: to "change" one, a new object is made ({@link #withName}).</p>
 */
public final class Account {

    /**
     * Id of the account that lives in WebView's <i>default</i> profile. There is always at most one,
     * and it is the only account possible on a phone whose WebView is too old for several profiles.
     */
    public static final String MAIN_ID = "main";

    public final String id;
    public final String name;

    public Account(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /** @return true for the account stored in WebView's default profile. */
    public boolean isMain() {
        return MAIN_ID.equals(id);
    }

    /**
     * @return the name of this account's WebView profile, or null for the default profile.
     *         Profile names we create all start with "acct_" (web/Profiles relies on it).
     */
    public String profileName() {
        return isMain() ? null : "acct_" + id;
    }

    Account withName(String newName) {
        return new Account(id, newName);
    }
}
