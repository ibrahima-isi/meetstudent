package com.bowe.meetstudent.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(h -> h.authenticationEntryPoint(customAuthenticationEntryPoint))
                .securityMatcher("/**")
                .authorizeHttpRequests(registry ->
                        registry
                                // Public endpoints
                                .requestMatchers("/").permitAll()
                                .requestMatchers("/api/v1/auth/**").permitAll()
                                .requestMatchers("/uploads/**").permitAll()
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                                
                                // Public Read access for content
                                .requestMatchers(HttpMethod.GET, "/api/v1/schools/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/programs/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/courses/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/accreditations/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/tags/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/course-rates/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/program-rates/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/school-rates/**").permitAll()

                                // Admin CRUD and Moderation
                                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/schools/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/schools/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/schools/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/programs/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/programs/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/programs/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.POST, "/api/v1/courses/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/courses/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/courses/**").hasRole("ADMIN")
                                .requestMatchers("/api/v1/accreditations/**").hasRole("ADMIN")
                                .requestMatchers("/api/v1/tags/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/course-rates/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/program-rates/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/school-rates/**").hasRole("ADMIN")

                                // Wishlist restricted to Students
                                .requestMatchers("/api/v1/users/*/wishlist/**").hasRole("STUDENT")

                                // Rating Permissions (More granular check in Controller)
                                .requestMatchers("/api/v1/school-rates/**").hasAnyRole("STUDENT", "EXPERT")
                                .requestMatchers("/api/v1/program-rates/**").hasRole("EXPERT")
                                .requestMatchers("/api/v1/course-rates/**").hasRole("EXPERT")

                                // User Profile (Ownership handled in controller)
                                .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll() // Registration
                                .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        var authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(passwordEncoder());

        return authBuilder.build();
    }
}
