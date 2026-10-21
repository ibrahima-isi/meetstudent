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

/**
 * A single-line text with no control, invisible or bidi characters (see {@link SafeText}) and no tab or line break:
 * for names that other users see (the forum shows the first name). Null passes; combine with {@code @Size}/{@code @NotEmpty}.
 */
@Documented
@Constraint(validatedBy = SafeName.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeName {

    String message() default "Caractères non autorisés";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SafeName, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || (!SafeText.hasForbiddenCharacters(value)
                    && value.chars().noneMatch(c -> c == '\t' || c == '\r' || c == '\n'));
        }
    }
}
