package com.bowe.meetstudent.controllers.rates;

import org.hibernate.exception.ConstraintViolationException;
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

    private static DataIntegrityViolationException withConstraint(String name, String message) {
        var hibernate = new ConstraintViolationException(message, new java.sql.SQLException(message, "23505"), name);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }

    @Test
    void matchesOnHibernateConstraintNameEvenWhenTheMessageDoesNotMentionIt() {
        assertTrue(UniqueRateViolation.matches(withConstraint("uk_program_rates_user_program", "duplicate key")));
        assertTrue(UniqueRateViolation.matches(withConstraint("UK_COURSE_RATES_USER_COURSE", "duplicate key")));
    }

    @Test
    void anotherNamedConstraintIsNotARateRace() {
        assertFalse(UniqueRateViolation.matches(withConstraint("uk_users_email", "duplicate key")));
        assertFalse(UniqueRateViolation.matches(withConstraint(null, "duplicate key")));
    }
}
