package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.ContactProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ContactPropertiesTest {

    private final ContactProperties.Retry retry = new ContactProperties().getMail().getRetry();

    @Test
    void defaultsAreThreeAttemptsWithTwoThenTenSeconds() {
        assertThat(retry.effectiveMaxAttempts()).isEqualTo(3);
        assertThat(retry.delayAfter(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(retry.delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void attemptsAreClampedBetweenOneAndTheHardLimit() {
        retry.setMaxAttempts(0);
        assertThat(retry.effectiveMaxAttempts()).isEqualTo(1);
        retry.setMaxAttempts(-4);
        assertThat(retry.effectiveMaxAttempts()).isEqualTo(1);
        retry.setMaxAttempts(99);
        assertThat(retry.effectiveMaxAttempts()).isEqualTo(ContactProperties.Mail.MAX_ATTEMPTS_LIMIT);
    }

    @Test
    void delaysAreNeverNegativeNorAboveTheCap() {
        retry.setInitialDelay(Duration.ofSeconds(-5));
        assertThat(retry.delayAfter(1)).isEqualTo(Duration.ZERO);
        retry.setInitialDelay(Duration.ofSeconds(30));
        retry.setBackoffMultiplier(10);
        assertThat(retry.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(retry.delayAfter(2)).isEqualTo(ContactProperties.Mail.MAX_DELAY);
        assertThat(retry.delayAfter(50)).isEqualTo(ContactProperties.Mail.MAX_DELAY);
        retry.setInitialDelay(Duration.ofHours(5));
        assertThat(retry.delayAfter(1)).isEqualTo(ContactProperties.Mail.MAX_DELAY);
    }

    @Test
    void aMultiplierBelowOneMeansConstantDelay() {
        retry.setBackoffMultiplier(0);
        assertThat(retry.delayAfter(3)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void anUnsetDelayFallsBackToTheDefaultNeverToABusyRetryLoop() {
        retry.setInitialDelay(null);
        assertThat(retry.delayAfter(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(retry.delayAfter(2)).isEqualTo(Duration.ofSeconds(10));
    }
}
