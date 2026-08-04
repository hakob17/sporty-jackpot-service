package com.sporty.jackpot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected wherever a timestamp is stamped, so tests can pin the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
