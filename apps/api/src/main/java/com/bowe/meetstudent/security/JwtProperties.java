package com.bowe.meetstudent.security;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties("security.jwt")
public class JwtProperties {
    private String secretKey;

    /** Access token lifetime in minutes (env: JWT_ACCESS_TTL_MINUTES). The web client refreshes on 401. */
    @Min(1)
    private long accessTtlMinutes = 60;
}
