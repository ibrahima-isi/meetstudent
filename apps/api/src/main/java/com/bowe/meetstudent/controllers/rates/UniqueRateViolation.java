package com.bowe.meetstudent.controllers.rates;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/** Recognises the one-rating-per-user-per-target unique constraints (uk_*_rates_*) among integrity errors. */
final class UniqueRateViolation {

    private static final String[] CONSTRAINTS = {
            "uk_school_rates_user_school", "uk_program_rates_user_program", "uk_course_rates_user_course"
    };

    private UniqueRateViolation() {
    }

    static boolean matches(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            var message = cause.getMessage();
            if (message == null) continue;
            var lower = message.toLowerCase(Locale.ROOT);
            for (var name : CONSTRAINTS) {
                if (lower.contains(name)) return true;
            }
        }
        return false;
    }
}
