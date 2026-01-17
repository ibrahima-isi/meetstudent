package com.bowe.meetstudent.config;

import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.utils.Utils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Integer> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null ||
                    !authentication.isAuthenticated() ||
                    authentication instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            return Optional.ofNullable(userPrincipal.getId());
        };
    }

    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(Utils.dakarTimeZone());
    }
}
