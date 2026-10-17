package com.bowe.meetstudent.security;

import com.bowe.meetstudent.config.ContactProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a request into a rate-limit key: always a short, normalised, literal IP, or the fixed {@link #UNKNOWN}.
 *
 * <p>Sources, in order: the first {@code X-Forwarded-For} hop when {@code app.contact.trust-forwarded-for} is on
 * (prod: Traefik is the only way in and replaces any client-supplied value), then {@code getRemoteAddr()}. Under
 * {@code server.forward-headers-strategy=framework} Spring's ForwardedHeaderFilter hides {@code X-Forwarded-For}
 * and itself puts its first hop into {@code getRemoteAddr()}, so in prod the value really comes from that second
 * source; the RFC 7239 {@code Forwarded} header is dropped before the filter sees it (see
 * {@code ForwardedHeaderConfig}). Both sources go through the same literal-IP check: anything else, a
 * client-chosen string included, becomes {@link #UNKNOWN}.
 *
 * <p>IPv4-mapped IPv6 ({@code ::ffff:a.b.c.d}) is the IPv4 address; any other IPv6 address is keyed by its /64
 * prefix, because one subscriber usually owns a whole /64 and could otherwise rotate through billions of keys.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    public static final String UNKNOWN = "unknown";

    private static final int MAX_LENGTH = 45;
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final ContactProperties properties;

    public String resolve(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            var header = request.getHeader("X-Forwarded-For");
            if (header != null) {
                var comma = header.indexOf(',');
                var key = normalise((comma < 0 ? header : header.substring(0, comma)).strip());
                if (key != null) {
                    return key;
                }
            }
        }
        var key = normalise(request.getRemoteAddr());
        return key != null ? key : UNKNOWN;
    }

    /** The key for a literal IP, or {@code null} when the value is not one. Never performs a DNS lookup. */
    private static String normalise(String value) {
        if (value == null || value.length() > MAX_LENGTH) {
            return null;
        }
        var v4 = IPV4.matcher(value);
        if (v4.matches()) {
            return ipv4(v4);
        }
        if (value.indexOf(':') < 0 || !IPV6.matcher(value).matches()) {
            return null;
        }
        try {
            // A value containing ':' is parsed as an IPv6 literal only, so this cannot resolve a name.
            var address = InetAddress.getByName(value);
            if (!(address instanceof Inet6Address)) {
                // Java hands back an Inet4Address for ::ffff:a.b.c.d
                return address.getHostAddress();
            }
            var bytes = address.getAddress();
            var key = new StringBuilder();
            for (int group = 0; group < 4; group++) {
                var word = ((bytes[group * 2] & 0xff) << 8) | (bytes[group * 2 + 1] & 0xff);
                key.append(group == 0 ? "" : ":").append(Integer.toHexString(word));
            }
            return key.append("::/64").toString();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static String ipv4(Matcher match) {
        var parts = new int[4];
        for (int i = 0; i < 4; i++) {
            parts[i] = Integer.parseInt(match.group(i + 1));
            if (parts[i] > 255) {
                return null;
            }
        }
        return parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
    }
}
