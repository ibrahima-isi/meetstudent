package com.bowe.meetstudent.exceptions;

import lombok.Getter;

/** A business-rule violation on one request field, answered as 400 {"field": "message"} like bean validation. */
@Getter
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
