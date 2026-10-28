package com.bowe.meetstudent.unit.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the production-safety settings of application.yml + application-prod.yml, resolved the way
 * Spring Boot layers them (base first, prod on top) with a controlled set of environment variables.
 */
class ProdProfileConfigTest {

    private static final Map<String, Object> FULL_ENV = Map.of(
            "JWT_SECRET_KEY", "k".repeat(40),
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://db/meetstudent",
            "SPRING_DATASOURCE_USERNAME", "u",
            "SPRING_DATASOURCE_PASSWORD", "p",
            "CORS_ALLOWED_ORIGINS", "https://app.example.com");

    private List<PropertySource<?>> load(String file) throws IOException {
        return new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
    }

    private PropertySourcesPropertyResolver resolver(Map<String, Object> env) throws IOException {
        var sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("env", env));
        // Highest precedence first: prod overrides the base file.
        load("application-prod.yml").forEach(sources::addLast);
        load("application.yml").forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources);
    }

    private PropertySourcesPropertyResolver prod;

    @BeforeEach
    void setUp() throws IOException {
        prod = resolver(FULL_ENV);
    }

    @Test
    void trustsForwardedHeadersFromTheTlsTerminatingProxy() {
        // native (Tomcat's RemoteIpValve), not framework: only the valve honours internal-proxies.
        assertEquals("native", prod.getProperty("server.forward-headers-strategy"));
    }

    @Test
    void theContactResolverDoesNotReadXForwardedForItself() {
        // The valve has already put the client address in getRemoteAddr() when the peer is a trusted proxy;
        // reading the raw header too would let any other peer choose its key.
        assertEquals("false", prod.getProperty("app.contact.trust-forwarded-for"));
    }

    @Test
    void trustedProxiesDefaultToPrivateAndLoopbackRanges() {
        var trusted = Pattern.compile(prod.getProperty("server.tomcat.remoteip.internal-proxies"));

        for (var proxy : List.of("10.1.2.3", "172.16.0.9", "172.31.255.1", "192.168.1.1", "127.0.0.1", "::1")) {
            assertTrue(trusted.matcher(proxy).matches(), proxy);
        }
        for (var other : List.of("8.8.8.8", "172.32.0.1", "172.15.0.1", "203.0.113.7", "11.0.0.1")) {
            assertFalse(trusted.matcher(other).matches(), other);
        }
    }

    @Test
    void trustedProxiesCanBeNarrowedToTheProxyNetworkWithAnEnvVariable() throws IOException {
        var env = new java.util.HashMap<>(FULL_ENV);
        env.put("TRUSTED_PROXIES", "10\\.250\\.77\\.\\d{1,3}");

        var trusted = Pattern.compile(resolver(env).getProperty("server.tomcat.remoteip.internal-proxies"));

        assertTrue(trusted.matcher("10.250.77.2").matches());
        assertFalse(trusted.matcher("10.250.78.2").matches());
        assertFalse(trusted.matcher("172.20.0.2").matches());
    }

    @Test
    void swaggerAndApiDocsAreDisabled() {
        assertEquals("false", prod.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", prod.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    void springdocDoesNotDocumentActuatorEndpoints() {
        assertEquals("false", prod.getProperty("springdoc.show-actuator"));
    }

    @Test
    void onlyTheHealthEndpointIsExposedAndWithoutDetails() {
        assertEquals("health", prod.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("never", prod.getProperty("management.endpoint.health.show-details"));
    }

    @Test
    void jwtSecretHasNoDefaultAndIsRequired() throws IOException {
        var withoutSecret = new java.util.HashMap<>(FULL_ENV);
        withoutSecret.remove("JWT_SECRET_KEY");

        assertThrows(IllegalArgumentException.class,
                () -> resolver(withoutSecret).getProperty("security.jwt.secret-key"));
    }

    @Test
    void datasourceAndCorsHaveNoDefaults() throws IOException {
        for (var key : List.of("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME",
                "SPRING_DATASOURCE_PASSWORD", "CORS_ALLOWED_ORIGINS")) {
            var env = new java.util.HashMap<>(FULL_ENV);
            env.remove(key);
            var resolver = resolver(env);
            assertThrows(IllegalArgumentException.class, () -> {
                resolver.getProperty("spring.datasource.url");
                resolver.getProperty("spring.datasource.username");
                resolver.getProperty("spring.datasource.password");
                resolver.getProperty("app.cors.allowed-origins");
            }, key + " must be required in prod");
        }
    }

    @Test
    void adminCredentialsAreNotDefaultedButResolveToEmptyForTheRunnerToReject() throws IOException {
        assertEquals("", prod.getProperty("app.admin.email"));
        assertEquals("", prod.getProperty("app.admin.password"));
    }

    @Test
    void accessTokenLifetimeDefaultsToSixtyMinutesAndIsEnvOverridable() throws IOException {
        assertEquals("60", prod.getProperty("security.jwt.access-ttl-minutes"));

        var env = new java.util.HashMap<>(FULL_ENV);
        env.put("JWT_ACCESS_TTL_MINUTES", "15");
        assertEquals("15", resolver(env).getProperty("security.jwt.access-ttl-minutes"));
    }
}
