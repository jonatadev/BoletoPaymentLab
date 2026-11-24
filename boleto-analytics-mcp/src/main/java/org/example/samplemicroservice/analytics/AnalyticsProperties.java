package org.example.samplemicroservice.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.analytics")
public record AnalyticsProperties(
        String authAudience,
        String authIssuer,
        String authJwks,
        int defaultPeriodDays,
        int maximumPeriodDays,
        int databaseQueryTimeoutSeconds) {}
