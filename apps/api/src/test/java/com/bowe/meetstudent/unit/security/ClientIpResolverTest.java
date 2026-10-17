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
    void acceptsIpv6AndKeysItByItsPrefix() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", "2001:db8::1"))).isEqualTo("2001:db8:0:0::/64");
    }

    @Test
    void fallsBackToTheSocketAddressWhenTheHeaderIsAbsentOrNotAnIp() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", null))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", "evil\r\nhost, 1.2.3.4"))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", "a".repeat(5000)))).isEqualTo("172.18.0.5");
        assertThat(resolver.resolve(request("172.18.0.5", ""))).isEqualTo("172.18.0.5");
    }

    @Test
    void aSocketAddressThatIsNotALiteralIpBecomesTheFixedKey() {
        // Behind the framework forwarded-header strategy getRemoteAddr() can carry a client-chosen string.
        for (boolean trust : new boolean[]{false, true}) {
            properties.setTrustForwardedFor(trust);
            for (String remote : new String[]{"hello", "[2001:db8::1]", "1.2.3.4\r\nx", "999.1.1.1", "1.2.3", "", "a".repeat(8000), "::zz", "1.2.3.4:80"}) {
                assertThat(resolver.resolve(request(remote, null))).as("remote=%s trust=%s", remote.length() > 20 ? "long" : remote, trust)
                        .isEqualTo("unknown");
            }
        }
    }

    @Test
    void aNullSocketAddressBecomesTheFixedKey() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertThat(resolver.resolve(request)).isEqualTo("unknown");
    }

    @Test
    void anInvalidForwardedHopFallsBackToAValidSocketAddressOrTheFixedKey() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("garbage", "also garbage"))).isEqualTo("unknown");
    }

    @Test
    void theKeyIsAlwaysShortAndLiteral() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("203.0.113.7", null))).isEqualTo("203.0.113.7");
        assertThat(resolver.resolve(request("172.18.0.5", "203.000.113.009"))).isEqualTo("203.0.113.9");
    }

    @Test
    void ipv4MappedIpv6IsTheSameClientAsThePlainIpv4() {
        assertThat(resolver.resolve(request("::ffff:203.0.113.9", null))).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(request("0:0:0:0:0:ffff:cb00:7109", null))).isEqualTo(resolver.resolve(request("203.0.113.9", null)));
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", "::ffff:203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void ipv6AddressesOfTheSame64PrefixShareOneKey() {
        var a = resolver.resolve(request("2001:db8:1:2:aaaa:bbbb:cccc:dddd", null));
        var b = resolver.resolve(request("2001:db8:1:2::1", null));
        var c = resolver.resolve(request("2001:0DB8:0001:0002:ffff:ffff:ffff:ffff", null));
        var other = resolver.resolve(request("2001:db8:1:3::1", null));
        assertThat(a).isEqualTo(b).isEqualTo(c);
        assertThat(other).isNotEqualTo(a);
        assertThat(a).hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void ipv6ForwardedHopsAreGroupedPer64Too() {
        properties.setTrustForwardedFor(true);
        assertThat(resolver.resolve(request("172.18.0.5", "2001:db8:1:2::5")))
                .isEqualTo(resolver.resolve(request("172.18.0.5", "2001:db8:1:2:1:2:3:4")))
                .isNotEqualTo(resolver.resolve(request("172.18.0.5", "2001:db8:1:9::5")));
    }
}
