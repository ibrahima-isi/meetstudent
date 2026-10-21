package com.bowe.meetstudent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Settings of the forum, bound from {@code app.forum.*}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.forum")
public class ForumProperties {

    private final RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {
        /** Messages (threads and replies together) accepted per user and window. */
        private int maxPerWindow = 5;
        private int windowSeconds = 60;
        /** Upper bound on the users remembered at once; the least recently seen is dropped first. */
        private int maxTrackedUsers = 10_000;
    }
}
