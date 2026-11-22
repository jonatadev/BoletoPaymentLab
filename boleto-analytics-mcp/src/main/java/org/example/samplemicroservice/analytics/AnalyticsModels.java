package org.example.samplemicroservice.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsModels {
    private AnalyticsModels() {
    }

    public record DateRangeInput(LocalDate startDate, LocalDate endDate) {
    }

    public record SummaryResponse(LocalDate startDate, LocalDate endDate, long totalBoletos,
            BigDecimal totalAmount, BigDecimal averageAmount, List<StatusSummary> statuses) {
    }

    public record DailyBoleto(LocalDate date, long boletoCount, BigDecimal totalAmount) {
    }

    public record StatusSummary(String status, long boletoCount, BigDecimal percentage, BigDecimal totalAmount) {
    }

    public record ProcessingMetrics(long processedEventReceipts, long pendingOutboxEvents,
            long publishedOutboxEvents, List<String> unavailableMetrics) {
    }

    public record OutboxMetrics(long totalEvents, long pendingEvents, long publishedEvents,
            long eventsWithRetryAttempts, Long oldestPendingAgeSeconds) {
    }

    public record DatabaseHealth(boolean available, long queryLatencyMilliseconds, Integer postgresqlMajorVersion) {
    }
}
