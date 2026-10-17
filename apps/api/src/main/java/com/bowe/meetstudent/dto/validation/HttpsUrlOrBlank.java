package com.bowe.meetstudent.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Null or empty (the empty string is how a client erases a link), otherwise an absolute
 * {@code https://} URL: lowercase scheme, a host, no user info, and no whitespace, control or
 * invisible format character anywhere. The value is expected to be stripped already.
 */
@Documented
@Constraint(validatedBy = HttpsUrlOrBlank.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpsUrlOrBlank {

    String message() default "must be a full https URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HttpsUrlOrBlank, String> {
        private static final String PREFIX = "https://";

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isEmpty() || isHttpsUrl(value);
        }

        static boolean isHttpsUrl(String value) {
            if (!value.startsWith(PREFIX) || value.codePoints().anyMatch(Validator::isForbidden)) {
                return false;
            }
            try {
                var uri = new URI(value);
                return uri.getHost() != null && !uri.getHost().isEmpty() && uri.getUserInfo() == null;
            } catch (URISyntaxException e) {
                return false;
            }
        }

        private static boolean isForbidden(int cp) {
            return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)
                    || Character.getType(cp) == Character.FORMAT;
        }
    }
}
