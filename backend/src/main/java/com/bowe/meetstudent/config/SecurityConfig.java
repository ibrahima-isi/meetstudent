package com.bowe.meetstudent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Configures the security filter chain for the application.
     *
     * @param httpSecurity the {@link HttpSecurity} object used to configure the security filter chain
     * @return the configured {@link SecurityFilterChain} instance
     * @throws Exception if an error occurs while configuring the security filter chain
     */
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
//
//        httpSecurity
//                .cors(AbstractHttpConfigurer::disable)
//                .csrf(AbstractHttpConfigurer::disable)
//                .sessionManagement(session ->
//                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                )
//                .formLogin(AbstractHttpConfigurer::disable)
//                .securityMatcher("/**") // make this config for all my app. I could set it to work on the API only by doing this /api/**
//                .authorizeHttpRequests( registry ->
//                        registry
//                                .requestMatchers("/").permitAll() // permit everyone to access the root path which means the welcome page here
//                                .requestMatchers("/api/auth/**").permitAll() // the authentication (login and register) path available to enable authentication for everyone
//                                .requestMatchers("/api/users").permitAll()
//                                .anyRequest().authenticated()
//                );
//        return httpSecurity.build();
//    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {


        return http
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers("/").permitAll();
                    registry.requestMatchers("/login").permitAll();
                    registry.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    registry.anyRequest().authenticated();
                })
                .formLogin(formLogin -> formLogin.permitAll())
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(){

        UserDetails normalUser = User.builder().username("ibra").password("$2a$12$vXmkPpdO.xfeQF8LyKZwfe6QgrTzu1SdjlNQtt5dIE5AlHPinai82").roles("USER").build();
        UserDetails adminUser = User.builder().username("admin").password("$2a$12$mYPXyP/tq4d9GG2yc2Xu9eC5cXoF9HMt.98kOCqvKEOUOQJnrIy02").roles("ADMIN", "USER").build();

        return new InMemoryUserDetailsManager(normalUser, adminUser);

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
