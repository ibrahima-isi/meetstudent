package com.bowe.meetstudent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .securityMatcher("/**") // make this config for all my app. I could set it to work on the API only by doing this /api/**
                .authorizeHttpRequests( registry ->
                        registry
                                .requestMatchers("/").permitAll() // permit everyone to access the root path which means the welcome page here
                                .requestMatchers("/api/auth/**").permitAll() // the authentication (login and register) path available to enable authentication for everyone
                                .requestMatchers("/api/users").hasRole("ADMIN")
                                .anyRequest().authenticated()
                );
        return httpSecurity.build();
    }
}
