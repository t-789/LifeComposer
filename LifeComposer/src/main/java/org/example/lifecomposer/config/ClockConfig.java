package org.example.lifecomposer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Injects the configured chat-usage zone so day boundaries are testable. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock appClock(AppSecurityProperties properties) {
        return Clock.system(ZoneId.of(properties.getChatUsageZone()));
    }
}
