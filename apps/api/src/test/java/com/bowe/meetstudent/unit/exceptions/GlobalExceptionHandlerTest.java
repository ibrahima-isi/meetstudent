package com.bowe.meetstudent.unit.exceptions;

import com.bowe.meetstudent.exceptions.ErrorResponse;
import com.bowe.meetstudent.exceptions.GlobalExceptionHandler;
import com.bowe.meetstudent.exceptions.MediaValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpInputMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ServletWebRequest request() {
        return new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/media"));
    }

    private void assertShape(ResponseEntity<ErrorResponse> response, HttpStatus expected) {
        assertEquals(expected, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertEquals(expected.value(), body.getStatus());
        assertEquals(expected.getReasonPhrase(), body.getError());
        assertEquals("/api/v1/media", body.getPath());
        assertTrue(body.getMessage() != null && !body.getMessage().isBlank());
    }

    @Test
    void mediaValidationExceptionKeepsItsStatusAndMessage() {
        var response = handler.handleMediaValidationException(
                new MediaValidationException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File extension is not allowed"), request());
        assertShape(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertEquals("File extension is not allowed", response.getBody().getMessage());
    }

    @Test
    void maxUploadSizeExceededIs413() {
        var response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(10_485_760L), request());
        assertShape(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void noResourceFoundIs404() {
        var response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "api/v1/nope"), request());
        assertShape(response, HttpStatus.NOT_FOUND);
    }

    @Test
    void genuineBugsStayInternalServerErrors() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.handleGlobalException(new IllegalArgumentException("a real bug"), request()).getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.handleGlobalException(new IllegalStateException("a real bug"), request()).getStatusCode());
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class)).detachAppender(appender);
    }

    @Test
    void unexpectedExceptionIsLoggedAtErrorWithStackTrace() {
        var appender = captureLogs();
        try {
            var boom = new IllegalStateException("boom");
            handler.handleGlobalException(boom, request());
            assertEquals(1, appender.list.size());
            assertEquals(Level.ERROR, appender.list.get(0).getLevel());
            assertEquals(boom, ((ch.qos.logback.classic.spi.ThrowableProxy) appender.list.get(0).getThrowableProxy()).getThrowable());
        } finally {
            detach(appender);
        }
    }

    @Test
    void mappedClientErrorsAreNotLoggedAtError() {
        var appender = captureLogs();
        try {
            handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"), request());
            handler.handleMissingParameter(new MissingServletRequestParameterException("category", "String"), request());
            assertEquals(0, appender.list.stream().filter(e -> e.getLevel().isGreaterOrEqual(Level.ERROR)).count());
        } finally {
            detach(appender);
        }
    }

    @Test
    void methodNotSupportedIs405() {
        assertShape(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"), request()),
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void mediaTypeNotSupportedIs415() {
        assertShape(handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of(MediaType.APPLICATION_JSON)), request()),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void missingParameterIs400() {
        var response = handler.handleMissingParameter(new MissingServletRequestParameterException("category", "String"), request());
        assertShape(response, HttpStatus.BAD_REQUEST);
    }

    @Test
    void unreadableBodyIs400() {
        var response = handler.handleNotReadable(
                new HttpMessageNotReadableException("bad", (HttpInputMessage) null), request());
        assertShape(response, HttpStatus.BAD_REQUEST);
    }
}
