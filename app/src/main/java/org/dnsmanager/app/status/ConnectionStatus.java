package org.dnsmanager.app.status;

import android.content.Context;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import org.dnsmanager.app.R;
import org.dnsmanager.app.site.Site;
import org.dnsmanager.app.theme.Scheme;
import org.dnsmanager.app.theme.Themes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/**
 * Keeps the top bar's status icon up to date: "is this phone using the DNS service?" The rules are
 * in {@link StatusRules}; this class gathers the facts.
 *
 * <p><b>How:</b> between {@link #start} and {@link #stop} (the screen is visible) it listens to the
 * phone's active network. Whenever the network or its Private DNS setting changes, it shows the
 * Private DNS state at once and then, on a background thread, asks the service's test address
 * ({@link Site#TEST_URL}). Network calls must never run on the main thread (Android throws
 * {@code NetworkOnMainThreadException}), hence the executor; the result is posted back to the main
 * thread, the only thread allowed to touch views.
 * Docs: https://developer.android.com/develop/connectivity/network-ops/reading-network-state</p>
 *
 * <p>Uses Java's built-in {@link HttpURLConnection}: one tiny request doesn't justify a library.</p>
 */
public final class ConnectionStatus {

    private static final String TAG = "ConnectionStatus";
    /** Several network callbacks arrive in a burst; test once after things settle. */
    private static final long TEST_DELAY_MS = 400;
    private static final int TIMEOUT_MS = 6000;
    private static final int MAX_ANSWER_BYTES = 16 * 1024;

    private final Context context;
    private final ImageView icon;
    private final Executor background;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable startTest = this::runTest;

    private ConnectivityManager.NetworkCallback callback;
    private StatusRules.State privateDnsState = StatusRules.State.NOT_PROTECTED;
    private StatusRules.State state; // null until the first network callback
    private boolean darkTheme;
    // Counts network changes: a test answer that belongs to an older network is thrown away.
    private int generation;

    /**
     * @param icon       the top bar's status button
     * @param background where the network request runs
     */
    public ConnectionStatus(Context context, ImageView icon, Executor background) {
        this.context = context.getApplicationContext();
        this.icon = icon;
        this.background = background;
    }

    /** Starts listening (MainActivity.onStart). Safe to call twice. */
    public void start() {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null || callback != null) {
            return;
        }
        callback = new ConnectivityManager.NetworkCallback() {
            // These run on a system thread, so everything is handed to the main thread.
            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties link) {
                boolean active = link.isPrivateDnsActive();
                String server = link.getPrivateDnsServerName();
                main.post(() -> onNetwork(StatusRules.fromPrivateDns(active, server)));
            }

            @Override
            public void onLost(Network network) {
                main.post(() -> onNetwork(StatusRules.State.NOT_PROTECTED));
            }
        };
        try {
            // "Default network" = the one the phone uses for the internet right now (Wi-Fi, mobile, VPN).
            manager.registerDefaultNetworkCallback(callback);
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't watch the network", e);
            callback = null;
        }
    }

    /** Stops listening (MainActivity.onStop), so nothing runs while the app is in the background. */
    public void stop() {
        main.removeCallbacks(startTest);
        generation++; // an answer still on its way is ignored
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager != null && callback != null) {
            try {
                manager.unregisterNetworkCallback(callback);
            } catch (RuntimeException e) {
                Log.w(TAG, "Network callback was already gone");
            }
        }
        callback = null;
    }

    /** @return the state on screen, or null while nothing is known yet. */
    public StatusRules.State state() {
        return state;
    }

    /** @return the sentence that explains the current state (also what screen readers say). */
    public String description() {
        if (state == null) {
            return context.getString(R.string.status_unknown);
        }
        switch (state) {
            case SECURE:
                return context.getString(R.string.status_secure, Site.NAME);
            case INSECURE:
                return context.getString(R.string.status_insecure, Site.NAME);
            case OTHER_DNS:
                return context.getString(R.string.status_other_dns, Site.NAME);
            default:
                return context.getString(R.string.status_not_protected, Site.NAME);
        }
    }

    /** Repaints the icon for the theme (MainActivity.applyTheme). */
    public void setDarkTheme(boolean dark) {
        darkTheme = dark;
        show();
    }

    private void onNetwork(StatusRules.State fromPrivateDns) {
        generation++;
        privateDnsState = fromPrivateDns;
        state = fromPrivateDns;
        show();
        main.removeCallbacks(startTest);
        main.postDelayed(startTest, TEST_DELAY_MS);
    }

    private void runTest() {
        int mine = generation;
        background.execute(() -> {
            String answer = fetch(Site.TEST_URL);
            main.post(() -> {
                if (mine != generation || answer == null) {
                    return; // the network changed meanwhile, or no answer: keep what is shown
                }
                state = StatusRules.combine(privateDnsState, StatusRules.fromTest(answer));
                show();
            });
        });
    }

    private void show() {
        icon.setImageResource(state == StatusRules.State.SECURE
                ? R.drawable.ic_status_ok : R.drawable.ic_status_warn);
        // Nothing known yet (the first moment after start): the quiet grey of the other icons, not red.
        int color = state != null ? StatusRules.color(state, darkTheme)
                : (darkTheme ? Themes.DARK : Themes.LIGHT).get(Scheme.ON_SURFACE_VARIANT);
        icon.setImageTintList(ColorStateList.valueOf(color));
        icon.setContentDescription(description());
    }

    /** @return the address's answer as text, or null if it can't be fetched. Background thread only. */
    private static String fetch(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int read;
                while ((read = in.read(buffer)) != -1 && out.size() < MAX_ANSWER_BYTES) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null; // offline, blocked, timed out: all mean "no answer"
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
