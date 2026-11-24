package org.example.samplemicroservice.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTests {
    @Mock private AnalyticsRepository repository;

    @Test
    void shouldCalculateSummaryAverageAndStatusPercentages() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 10);
        when(repository.totals(start, end)).thenReturn(new AnalyticsRepository.Totals(4, new BigDecimal("100.00")));
        when(repository.statuses(start, end))
                .thenReturn(
                        List.of(
                                new AnalyticsRepository.StatusAggregate("EMITIDO", 3, new BigDecimal("70.00")),
                                new AnalyticsRepository.StatusAggregate("PAGO", 1, new BigDecimal("30.00"))));

        var service = service();
        var summary = service.summary(new AnalyticsModels.DateRangeInput(start, end));

        assertThat(summary.averageAmount()).isEqualByComparingTo("25.00");
        assertThat(summary.statuses())
                .extracting(AnalyticsModels.StatusSummary::percentage)
                .extracting(BigDecimal::stripTrailingZeros)
                .containsExactly(new BigDecimal("75"), new BigDecimal("25"));
    }

    @Test
    void shouldDeclareMetricsUnavailableWhenTheSchemaCannotRepresentThem() {
        when(repository.outbox()).thenReturn(new AnalyticsRepository.OutboxAggregate(5, 2, 3, 1, null));
        when(repository.processedReceipts()).thenReturn(3L);

        var metrics = service().processingMetrics();

        assertThat(metrics.processedEventReceipts()).isEqualTo(3);
        assertThat(metrics.unavailableMetrics()).containsExactly("failedOperations", "averageProcessingDuration");
    }

    private AnalyticsService service() {
        var properties = new AnalyticsProperties("analytics", "issuer", "jwks", 30, 366, 5);
        var ranges =
                new AnalyticsDateRanges(Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC), properties);
        return new AnalyticsService(repository, ranges);
    }
}
