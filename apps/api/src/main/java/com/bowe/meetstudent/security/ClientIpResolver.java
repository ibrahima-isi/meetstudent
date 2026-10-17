package com.bowe.meetstudent.security;

import com.bowe.meetstudent.config.ContactProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Finds the address a request really comes from. The socket address is used unless
 * {@code app.contact.trust-forwarded-for} is on (prod: every request goes through Traefik, which replaces any
 * client-supplied {@code X-Forwarded-For} with the real peer address). Then the first hop is used, but only
 * when it is a literal IPv4/IPv6 address: anything else falls back to the socket address, so the value is
 * short, free of line breaks and safe as a map key.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private final ContactProperties properties;

    public String resolve(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            var header = request.getHeader("X-Forwarded-For");
            if (header != null) {
                var comma = header.indexOf(',');
                var first = (comma < 0 ? header : header.substring(0, comma)).strip();
                if (isLiteralIp(first)) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isLiteralIp(String value) {
        if (value.length() > 45) {
            return false;
        }
        return IPV4.matcher(value).matches() || (value.indexOf(':') >= 0 && IPV6.matcher(value).matches());
    }
}
