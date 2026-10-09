package com.bowe.meetstudent.unit.security;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.bowe.meetstudent.security.JwtAuthenticationFilter;
import com.bowe.meetstudent.security.JwtDecoder;
import com.bowe.meetstudent.security.JwtToPrincipalConverter;
import jakarta.servlet.FilterChain;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidTokenIsLoggedWithoutErrorLevelOrStackTrace() throws Exception {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decodedJWT(anyString())).thenThrow(new JWTDecodeException("bad token"));
        var filter = new JwtAuthenticationFilter(decoder, mock(JwtToPrincipalConverter.class));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer garbage");
        FilterChain chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(appender.list.isEmpty(), "the failure should still be logged");
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.ERROR)),
                "a bad token must not be logged at ERROR");
        assertTrue(appender.list.stream().allMatch(e -> e.getThrowableProxy() == null),
                "a bad token must not log a stack trace");
    }
}
