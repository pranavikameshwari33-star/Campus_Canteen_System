package com.campuscanteen.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides one Clock for the whole application, set to the canteen's time zone.
 * All "what time is it now / what is today's date" questions go through this clock,
 * which also lets tests use a fixed time.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.timezone()));
    }
}
