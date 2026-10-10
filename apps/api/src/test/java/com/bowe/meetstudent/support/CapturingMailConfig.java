package com.bowe.meetstudent.support;

import com.bowe.meetstudent.mail.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Import with {@code @Import(CapturingMailConfig.class)} to capture outgoing emails in an integration test. */
@TestConfiguration
public class CapturingMailConfig {

    @Bean
    @Primary
    public CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }
}
