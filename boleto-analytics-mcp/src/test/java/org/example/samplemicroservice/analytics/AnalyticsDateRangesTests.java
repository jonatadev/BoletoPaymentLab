package org.example.samplemicroservice.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AnalyticsDateRangesTests {
    private final AnalyticsDateRanges ranges =
            new AnalyticsDateRanges(
                    Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC),
                    new AnalyticsProperties("analytics", "issuer", "jwks", 30, 366, 5));

    @Test
    void shouldApplyTheApprovedThirtyDayDefaultRange() {
        var range = ranges.optional(null);

        assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 1, 12));
        assertThat(range.endDate()).isEqualTo(LocalDate.of(2026, 2, 10));
    }

    @Test
    void shouldRejectInvertedAndExcessiveRanges() {
        assertThatThrownBy(() ->
                        ranges.required(
                                new AnalyticsModels.DateRangeInput(
                                        LocalDate.of(2026, 2, 11), LocalDate.of(2026, 2, 10))))
                .isInstanceOf(AnalyticsValidationException.class)
                .hasMessageContaining("must not be before");
        assertThatThrownBy(() ->
                        ranges.required(
                                new AnalyticsModels.DateRangeInput(
                                        LocalDate.of(2025, 1, 1), LocalDate.of(2026, 2, 10))))
                .isInstanceOf(AnalyticsValidationException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void shouldRequireBothDatesForTheDailyTool() {
        assertThatThrownBy(() -> ranges.required(new AnalyticsModels.DateRangeInput(null, LocalDate.now())))
                .isInstanceOf(AnalyticsValidationException.class)
                .hasMessageContaining("required");
    }
}
