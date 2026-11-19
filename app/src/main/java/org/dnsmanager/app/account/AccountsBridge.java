package org.dnsmanager.app.account;

import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Settings page's "Accounts" section can ask the app to do. Added to the settings WebView
 * as {@code AndroidAccounts}.
 *
 * <p><b>How such a bridge works:</b> methods marked {@link JavascriptInterface} can be called from
 * the page's JavaScript ({@code AndroidAccounts.addAccount("Work")}). WebView runs them on its own
 * background thread, so anything that touches the screen goes through {@code runOnUiThread}. This
 * bridge is only ever given to the app's own local page, never to a website.</p>
 *
 * <p>Every method that changes something returns the fresh account list as JSON ({@link #json}),
 * with an "error" text if it didn't work, so the page always redraws from the truth.</p>
 */
public final class AccountsBridge {

    private static final String TAG = "AccountsBridge";

    /** What the bridge needs from the screen (implemented by settings/SettingsWindow's host). */
    public interface Host {
        AccountStore accounts();

        SharedPreferences prefs();

        Account currentAccount();

        /** @return web/Profiles.isSupported() */
        boolean profilesSupported();

        void runOnUiThread(Runnable action);

        /** Main thread: the list changed (names, order, removed accounts): update tabs and top bar. */
        void onAccountsChanged();

        /**
         * Main thread: close this account's tab and delete its logins and site data.
         *
         * @param removed true if the account was removed from the list (then its other per-account
         *                settings go too), false for "Sign out" (the account stays)
         */
        void forgetAccount(Account account, boolean removed);

        /** Main thread: close Settings and show this account. */
        void openAccount(Account account);
    }

    private final Host host;

    public AccountsBridge(Host host) {
        this.host = host;
    }

    @JavascriptInterface
    public String getAccountsJson() {
        return json(null);
    }

    @JavascriptInterface
    public String addAccount(String name) {
        AccountStore store = host.accounts();
        if (!host.profilesSupported()) {
            return json("Several accounts need a newer \"Android System WebView\". Update it through the app store you use.");
        }
        if (store.add(name, AccountStore.newId(), true) == null) {
            return json("Couldn't add the account.");
        }
        saveAndRefresh(store);
        return json(null);
    }

    @JavascriptInterface
    public String renameAccount(String id, String name) {
        AccountStore store = host.accounts();
        if (!store.rename(id, name)) {
            return json("Please enter a name.");
        }
        saveAndRefresh(store);
        return json(null);
    }

    /** @param idsJson a JSON list of every account id, in the new order */
    @JavascriptInterface
    public String reorderAccounts(String idsJson) {
        AccountStore store = host.accounts();
        List<String> ids = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(idsJson);
            for (int i = 0; i < list.length(); i++) {
                ids.add(list.getString(i));
            }
        } catch (Exception e) {
            return json("The new order couldn't be saved.");
        }
        if (!store.reorder(ids)) {
            return json("The accounts changed. Please try again.");
        }
        saveAndRefresh(store);
        return json(null);
    }

    @JavascriptInterface
    public void openAccount(String id) {
        Account account = host.accounts().find(id);
        if (account != null) {
            host.runOnUiThread(() -> host.openAccount(account));
        }
    }

    /** Deletes the account's logins and site data on this phone; the account stays in the list. */
    @JavascriptInterface
    public String signOutAccount(String id) {
        Account account = host.accounts().find(id);
        if (account == null) {
            return json("That account no longer exists.");
        }
        host.runOnUiThread(() -> {
            host.forgetAccount(account, false);
            host.onAccountsChanged();
        });
        return json(null);
    }

    /** Removes the account and deletes everything it stored on this phone. */
    @JavascriptInterface
    public String removeAccount(String id) {
        AccountStore store = host.accounts();
        Account removed = store.remove(id);
        if (removed == null) {
            return json("The last account can't be removed. Sign it out instead.");
        }
        store.save(host.prefs());
        host.runOnUiThread(() -> {
            host.forgetAccount(removed, true);
            host.onAccountsChanged();
        });
        return json(null);
    }

    private void saveAndRefresh(AccountStore store) {
        store.save(host.prefs());
        host.runOnUiThread(host::onAccountsChanged);
    }

    /** The account list for the page: {accounts:[{id,name,number}], current, canAdd, multiAccount, error?}. */
    private String json(String error) {
        try {
            AccountStore store = host.accounts();
            JSONArray list = new JSONArray();
            int number = 1;
            for (Account a : store.all()) {
                list.put(new JSONObject().put("id", a.id).put("name", a.name).put("number", number++));
            }
            boolean profiles = host.profilesSupported();
            Account current = host.currentAccount();
            JSONObject root = new JSONObject()
                    .put("accounts", list)
                    .put("current", current == null ? "" : current.id)
                    .put("canAdd", store.canAdd(profiles))
                    .put("multiAccount", profiles);
            if (error != null) {
                root.put("error", error);
            }
            return root.toString();
        } catch (Exception e) {
            Log.e(TAG, "Couldn't build the accounts JSON", e);
            return "{\"accounts\":[]}";
        }
    }
}
