package com.bowe.meetstudent.controllers.rates;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueRateViolationTest {

    @Test
    void matchesTheRateUniqueConstraintEvenWhenNested() {
        var cause = new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_school_rates_user_school\"");
        assertTrue(UniqueRateViolation.matches(new DataIntegrityViolationException("could not execute statement", cause)));
    }

    @Test
    void matchesH2UpperCaseConstraintNames() {
        var cause = new RuntimeException("Unique index or primary key violation: \"UK_COURSE_RATES_USER_COURSE ON PUBLIC.COURSE_RATES(USER_ID, COURSE_ID)\"");
        assertTrue(UniqueRateViolation.matches(new DataIntegrityViolationException("x", cause)));
    }

    @Test
    void doesNotRelabelOtherIntegrityErrors() {
        var cause = new RuntimeException("ERROR: null value in column \"note\" violates not-null constraint");
        assertFalse(UniqueRateViolation.matches(new DataIntegrityViolationException("x", cause)));
        assertFalse(UniqueRateViolation.matches(new DataIntegrityViolationException("insert or update violates foreign key constraint fk_rates_user")));
    }
}
