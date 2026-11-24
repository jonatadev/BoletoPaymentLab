package org.example.samplemicroservice.analytics;

import java.time.Clock;
import java.time.LocalDate;

final class AnalyticsDateRanges {
    private final Clock clock;
    private final AnalyticsProperties properties;

    AnalyticsDateRanges(Clock clock, AnalyticsProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    ResolvedDateRange optional(AnalyticsModels.DateRangeInput input) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start =
                input == null || input.startDate() == null
                        ? today.minusDays(properties.defaultPeriodDays() - 1L)
                        : input.startDate();
        LocalDate end = input == null || input.endDate() == null ? today : input.endDate();
        return validate(start, end);
    }

    ResolvedDateRange required(AnalyticsModels.DateRangeInput input) {
        if (input == null || input.startDate() == null || input.endDate() == null) {
            throw new AnalyticsValidationException("startDate and endDate are required");
        }
        return validate(input.startDate(), input.endDate());
    }

    private ResolvedDateRange validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new AnalyticsValidationException("endDate must not be before startDate");
        }
        if (start.plusDays(properties.maximumPeriodDays() - 1L).isBefore(end)) {
            throw new AnalyticsValidationException("date range exceeds the configured maximum");
        }
        return new ResolvedDateRange(start, end);
    }

    record ResolvedDateRange(LocalDate startDate, LocalDate endDate) {}
}
