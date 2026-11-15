package org.dnsmanager.app.downloads;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Reads a {@code data:} address: a file written directly into the address instead of stored on a
 * server, e.g. {@code data:text/plain;charset=UTF-8,code1%0Acode2}. The dashboard offers the 2FA
 * recovery codes this way (a link with a {@code download} attribute). Android's DownloadManager can
 * only fetch http(s), so the app decodes these itself (downloads/Downloads.saveDataUrl).
 *
 * <p>Format (RFC 2397): {@code data:[<type>][;charset=…][;base64],<data>}. Without ";base64" the data is
 * percent-encoded text ("%0A" = line break); a "+" stays a "+" (unlike in web forms). Plain Java,
 * tested by DataUrlTest.</p>
 */
public final class DataUrl {

    /** Larger files are refused (the dashboard's are a few hundred bytes). */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public final String mimeType;
    public final byte[] bytes;

    private DataUrl(String mimeType, byte[] bytes) {
        this.mimeType = mimeType;
        this.bytes = bytes;
    }

    /** @return the decoded file, or null if {@code url} is not a valid, small enough data: address */
    public static DataUrl parse(String url) {
        if (url == null || !url.regionMatches(true, 0, "data:", 0, 5)) {
            return null;
        }
        int comma = url.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String[] meta = url.substring(5, comma).split(";");
        String type = meta[0].trim().toLowerCase(Locale.ROOT);
        boolean base64 = false;
        for (int i = 1; i < meta.length; i++) {
            if ("base64".equalsIgnoreCase(meta[i].trim())) {
                base64 = true;
            }
        }
        if (type.isEmpty() || !type.matches("[a-z0-9.+-]+/[a-z0-9.+-]+")) {
            type = "text/plain"; // the standard's default (and anything odd counts as unknown text)
        }
        String payload = url.substring(comma + 1);
        if (payload.length() > MAX_BYTES * 2) {
            return null;
        }
        try {
            byte[] bytes = base64
                    // The strict decoder: a broken file is refused, not saved half-decoded. Line breaks
                    // and spaces (allowed inside data: addresses) are removed first.
                    ? Base64.getDecoder().decode(new String(percentDecode(payload), StandardCharsets.US_ASCII)
                            .replaceAll("\\s", ""))
                    : percentDecode(payload);
            return bytes.length > MAX_BYTES ? null : new DataUrl(type, bytes);
        } catch (IllegalArgumentException e) {
            return null; // broken base64 or percent escape
        }
    }

    /** "%41b%0A" → bytes "Ab\n". A "%" not followed by two hex digits is an error. */
    static byte[] percentDecode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < raw.length; i++) {
            byte b = raw[i];
            if (b == '%') {
                if (i + 2 >= raw.length) {
                    throw new IllegalArgumentException("Cut-off escape");
                }
                int hi = Character.digit(raw[i + 1], 16);
                int lo = Character.digit(raw[i + 2], 16);
                if (hi < 0 || lo < 0) {
                    throw new IllegalArgumentException("Bad escape");
                }
                out.write((hi << 4) | lo);
                i += 2;
            } else {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    /** @return a file extension for the type ("txt" for text/plain), or "bin" when unknown. */
    public static String extensionFor(String mimeType) {
        switch (mimeType == null ? "" : mimeType) {
            case "text/plain":
                return "txt";
            case "text/csv":
                return "csv";
            case "application/json":
                return "json";
            case "application/x-apple-aspen-config":
                return "mobileconfig";
            case "image/png":
                return "png";
            default:
                return "bin";
        }
    }
}
