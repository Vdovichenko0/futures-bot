package io.cryptobot.configs.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "dateTimeProvider")
public class TimeConfig {
    /**
     * Бин, дающий текущее время в системной зоне по UTC (или вашей локальной).
     * В тестах вы сможете заменить его на фиксированный Clock.fixed(...)
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();  // или systemUTC()
    }

    @Bean
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }

    /*
    @EnableMongoAuditing включает слушатели, которые будут заполнять поля.
    dateTimeProviderRef указывает на наш провайдер времени.
     */
}
