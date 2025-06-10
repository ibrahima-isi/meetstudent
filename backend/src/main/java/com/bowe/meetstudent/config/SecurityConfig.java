package com.bowe.meetstudent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Configures the security filter chain for the application.
     *
     * @param http the {@link HttpSecurity} object used to configure the security filter chain
     * @return the configured {@link SecurityFilterChain} instance
     * @throws Exception if an error occurs while configuring the security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers("/", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll();
                    registry.requestMatchers("/logout").permitAll();
                    registry.requestMatchers("/api/auth/**").permitAll();
                    registry.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    registry.anyRequest().authenticated();
                })
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout (form ->form.logoutSuccessUrl("/"))
                .build();
    }

    /**
     * Creates a bean for password encoding.
     *
     * @return a `PasswordEncoder` instance using `BCryptPasswordEncoder`
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SpringSecurityDialect securityDialect() {
        return new SpringSecurityDialect();
    }
}
