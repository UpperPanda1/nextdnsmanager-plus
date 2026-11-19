package org.dnsmanager.app.site;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the app knows about the DNS service it manages. <b>This is the one file to change to
 * build the app for another service</b> (the planned AdGuard DNS Manager: see CLAUDE.md §8): the rest
 * of the code asks this class instead of naming NextDNS itself.
 *
 * <p>Plain Java (no Android classes), so SiteTest checks the rules on the computer.</p>
 */
public final class Site {

    /** The service's name as shown to the user ("This phone uses NextDNS…"). */
    public static final String NAME = "NextDNS";

    /** The dashboard: the page every account opens, and where the home button goes. */
    public static final String HOME_URL = "https://my.nextdns.io/";

    /**
     * Domains that open inside the app (each one including all its subdomains). Any other link is
     * handed to the phone's browser. Login happens on my.nextdns.io itself (e-mail + password), so
     * no sign-in domains of other companies are needed here.
     */
    public static final List<String> DOMAINS = Collections.singletonList("nextdns.io");

    /** Answers with a small JSON text that says whether this phone's DNS goes through the service. */
    public static final String TEST_URL = "https://test.nextdns.io/";

    /** The service's own speed test page (three-dots menu → Ping test). */
    public static final String PING_URL = "https://ping.nextdns.io/";

    /** A Private DNS host name containing this text belongs to the service (e.g. abc123.dns.nextdns.io). */
    public static final String PRIVATE_DNS_MARKER = "nextdns";

    /** TEST_URL's JSON: field that says whether the service is in use, and the value that means yes. */
    public static final String TEST_STATUS_FIELD = "status";
    public static final String TEST_STATUS_IN_USE = "ok";

    /** TEST_URL's JSON: field with the DNS protocol, and the values that are encrypted. */
    public static final String TEST_PROTOCOL_FIELD = "protocol";
    public static final List<String> SECURE_PROTOCOLS = Arrays.asList("DOH", "DOT", "DOQ", "DOH3");

    /**
     * Web pages the Settings screen may open in the browser, by name. The settings page only ever
     * sends the name, never an address, so it can't be used to open something else.
     */
    public static final Map<String, String> LINKS;

    static {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("service_privacy", "https://nextdns.io/privacy");
        links.put("service_terms", "https://help.nextdns.io/terms");
        LINKS = Collections.unmodifiableMap(links);
    }

    private Site() {
        // Only static members.
    }

    /**
     * @return true if a page on {@code host} belongs to the service and may load inside the app.
     *         Exact match or a real subdomain: "my.nextdns.io" yes, "evilnextdns.io" no.
     */
    public static boolean ownsHost(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        for (String domain : DOMAINS) {
            if (h.equals(domain) || h.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the "origin rules" for scripts the app adds to the service's pages: each domain and
     *         its subdomains, https only. Format: androidx.webkit's addDocumentStartJavaScript.
     */
    public static Set<String> originRules() {
        Set<String> rules = new LinkedHashSet<>();
        for (String domain : DOMAINS) {
            rules.add("https://" + domain);
            rules.add("https://*." + domain);
        }
        return rules;
    }
}
