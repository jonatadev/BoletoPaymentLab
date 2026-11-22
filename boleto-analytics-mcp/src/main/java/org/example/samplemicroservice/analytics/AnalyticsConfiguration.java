package org.example.samplemicroservice.analytics;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AnalyticsConfiguration {
    @Bean
    Clock analyticsClock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }

    @Bean
    AnalyticsDateRanges analyticsDateRanges(Clock analyticsClock, AnalyticsProperties properties) {
        return new AnalyticsDateRanges(analyticsClock, properties);
    }
}
