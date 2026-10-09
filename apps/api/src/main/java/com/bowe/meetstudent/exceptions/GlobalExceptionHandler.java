package com.bowe.meetstudent.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions (e.g., @Valid annotation).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles resource not found exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles authentication failures (Bad credentials).
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Invalid username or password",
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles unknown or expired refresh tokens (401 so the client falls back to login).
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshTokenException(
            InvalidRefreshTokenException ex, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Upload validation failures: 400 (invalid), 413 (too large) or 415 (unsupported type).
     */
    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<ErrorResponse> handleMediaValidationException(
            MediaValidationException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    /**
     * Multipart request larger than the configured limit (rejected before the controller runs).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed size", request);
    }

    /**
     * Unknown route / static resource.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", request);
    }

    /**
     * Unauthenticated access to a resource that requires authentication (401).
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            org.springframework.security.core.AuthenticationException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        Class<?> required = ex.getRequiredType();
        String message;
        if (required != null && required.isEnum()) {
            String allowed = Arrays.stream(required.getEnumConstants())
                    .map(c -> ((Enum<?>) c).name()).collect(Collectors.joining(", "));
            message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName()
                    + "'. Allowed values: " + allowed;
        } else {
            message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'"
                    + (required != null ? ": expected " + required.getSimpleName() : "");
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Required part '" + ex.getRequestPartName() + "' is missing", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, WebRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type '" + ex.getContentType()
                + "' is not supported. Supported: " + ex.getSupportedMediaTypes(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method '" + ex.getMethod() + "' is not supported for this endpoint", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Handles all other general exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error on {}", request.getDescription(false), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handle Access Denied (403 Forbidden) when @PreAuthorize fails
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
        AccessDeniedException exception, WebRequest request
    ){
        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            "You do not have permission to perform this action.",
            request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
}
