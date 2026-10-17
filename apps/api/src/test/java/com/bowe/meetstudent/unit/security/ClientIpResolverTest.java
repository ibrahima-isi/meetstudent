package com.bowe.meetstudent.unit.security;

import com.bowe.meetstudent.config.ContactProperties;
import com.bowe.meetstudent.security.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ContactProperties properties = new ContactProperties();
    private final ClientIpResolver resolver = new ClientIpResolver(properties);

    private static MockHttpServletRequest request(String remote, String forwardedFor) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void ignoresForwardedForByDefault() {
        assertThat(resolver.resolve(request("198.51.100.7", "203.0.113.9"))).isEqualTo("198.51.100.7");
    }

    @Test
    void usesTheFirstForwardedHopWhenTheProxyIsTrusted() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", "203.0.113.9, 172.18.0.2"))).isEqualTo("203.0.113.9");
    }

    @Test
    void acceptsIpv6() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", "2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    @Test
    void fallsBackToTheSocketAddressWhenTheHeaderIsAbsentOrNotAnIp() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", null))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", "evil\r\nhost, 1.2.3.4"))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", "a".repeat(5000)))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", ""))).isEqualTo("172.18.0.5");
    }
}
