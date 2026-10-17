package com.bowe.meetstudent.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;

/**
 * With {@code server.forward-headers-strategy=framework} (prod) Spring's {@link ForwardedHeaderFilter} prefers the
 * RFC 7239 {@code Forwarded} header over {@code X-Forwarded-For} and copies its {@code for=} value, unvalidated,
 * into {@code getRemoteAddr()}. Traefik sets {@code X-Forwarded-*} but passes a client-sent {@code Forwarded}
 * through, so any visitor could pick the address the API sees. This registration replaces Spring Boot's own
 * (which backs off) with the same filter minus that header: {@code Forwarded} is hidden before the filter reads it,
 * so only {@code X-Forwarded-*} counts. Traefik also strips it (compose.prod.yml); this is the second lock.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "server.forward-headers-strategy", havingValue = "framework")
public class ForwardedHeaderConfig {

    private static final String FORWARDED = "Forwarded";

    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        var registration = new FilterRegistrationBean<ForwardedHeaderFilter>(new IgnoreForwardedHeaderFilter());
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static final class IgnoreForwardedHeaderFilter extends ForwardedHeaderFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            super.doFilterInternal(new WithoutForwarded(request), response, chain);
        }
    }

    private static final class WithoutForwarded extends HttpServletRequestWrapper {

        WithoutForwarded(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return FORWARDED.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return FORWARDED.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(header -> !FORWARDED.equalsIgnoreCase(header)).collect(Collectors.toList()));
        }
    }
}
