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
}
