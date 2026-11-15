package org.dnsmanager.app.status;

import org.dnsmanager.app.site.Site;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Decides what the top bar's status icon shows: is this phone's DNS going through the service, and
 * is that connection encrypted? Plain Java (StatusRulesTest); status/ConnectionStatus collects the
 * two facts from Android and the network and asks this class.
 *
 * <p><b>Two sources</b></p>
 * <ol>
 *   <li>Android's <b>Private DNS</b> setting (Settings → Network → Private DNS): on with a host name
 *       of the service, on with something else, or off. Known at once, without the network.</li>
 *   <li>The service's <b>test address</b> ({@link Site#TEST_URL}): a small JSON text saying whether
 *       the request's DNS lookup came through the service and with which protocol. This also catches
 *       set-ups Android can't see (the service configured on the router, or in a VPN app), so when it
 *       says "in use" it wins over source 1.</li>
 * </ol>
 */
public final class StatusRules {

    /** What the icon can say, from best to worst. */
    public enum State {
        /** Using the service over an encrypted protocol. Green shield with a tick. */
        SECURE,
        /** Using the service, but unencrypted (plain DNS). Orange warning. */
        INSECURE,
        /** Private DNS is on but points somewhere else. Yellow warning. */
        OTHER_DNS,
        /** Not using the service. Red warning. */
        NOT_PROTECTED
    }

    private StatusRules() {
        // Only static methods.
    }

    /**
     * @param active     Android's {@code LinkProperties.isPrivateDnsActive()}
     * @param serverName Android's {@code getPrivateDnsServerName()}; null in "automatic" mode
     */
    public static State fromPrivateDns(boolean active, String serverName) {
        if (!active) {
            return State.NOT_PROTECTED;
        }
        boolean ours = serverName != null
                && serverName.toLowerCase(Locale.ROOT).contains(Site.PRIVATE_DNS_MARKER);
        return ours ? State.SECURE : State.OTHER_DNS;
    }

    /**
     * @param json the text {@link Site#TEST_URL} answered with
     * @return SECURE or INSECURE if the answer says the service is in use; null if it isn't in use
     *         or the text can't be read (then the Private DNS state stands)
     */
    public static State fromTest(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!Site.TEST_STATUS_IN_USE.equalsIgnoreCase(o.optString(Site.TEST_STATUS_FIELD))) {
                return null;
            }
            String protocol = o.optString(Site.TEST_PROTOCOL_FIELD).toUpperCase(Locale.ROOT);
            return Site.SECURE_PROTOCOLS.contains(protocol) ? State.SECURE : State.INSECURE;
        } catch (Exception e) {
            return null;
        }
    }

    /** @return what to show: the test's answer if it has one, else the Private DNS state. */
    public static State combine(State privateDns, State test) {
        return test != null ? test : privateDns;
    }

    /**
     * @return the icon's colour (0xAARRGGBB) for a state, readable on the top bar in that theme
     *         (StatusRulesTest checks the contrast). The icon's shape differs too (tick vs. warning
     *         sign), so the state never depends on colour alone.
     */
    public static int color(State state, boolean darkTheme) {
        switch (state) {
            case SECURE:
                return darkTheme ? 0xFF75B798 : 0xFF198754;
            case INSECURE:
                return darkTheme ? 0xFFFD9843 : 0xFFC2570A;
            case OTHER_DNS:
                return darkTheme ? 0xFFFFDA6A : 0xFF946C00;
            default:
                return darkTheme ? 0xFFEA868F : 0xFFDC3545;
        }
    }
}
