package com.bowe.meetstudent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

//    private final JwtAuthenticationFilter filter;
//    private final CustomUserDetailsService customUserDetailsService;

    /**
     * Configures the security filter chain for the application.
     *
     * @param httpSecurity the {@link HttpSecurity} object used to configure the security filter chain
     * @return the configured {@link SecurityFilterChain} instance
     * @throws Exception if an error occurs while configuring the security filter chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        // do the customer filter before using the UsernamePasswordAuthFilter class
//        httpSecurity.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

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
                                .requestMatchers("/api/users").permitAll()
                                .anyRequest().authenticated()
                );
        return httpSecurity.build();
    }

    /**
     * Creates a bean for password encoding.
     *
     * @return a `PasswordEncoder` instance using `BCryptPasswordEncoder`
     */
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the `AuthenticationManager` bean.
     *
     * @param httpSecurity the `HttpSecurity` object used to configure the `AuthenticationManagerBuilder`
     * @return the configured `AuthenticationManager` instance
     * @throws Exception if an error occurs while building the `AuthenticationManager`
     */
//    @Bean
//    public AuthenticationManager authenticationManager(HttpSecurity httpSecurity) throws Exception {
//        AuthenticationManagerBuilder authenticationManagerBuilder = httpSecurity.getSharedObject(
//                AuthenticationManagerBuilder.class
//        );
//
//        authenticationManagerBuilder.userDetailsService(customUserDetailsService).passwordEncoder(passwordEncoder());
//
//        return authenticationManagerBuilder.build();
//    }
}
