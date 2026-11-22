package org.example.samplemicroservice.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class AnalyticsService {
    private final AnalyticsRepository repository;
    private final AnalyticsDateRanges dateRanges;

    AnalyticsService(AnalyticsRepository repository, AnalyticsDateRanges dateRanges) {
        this.repository = repository;
        this.dateRanges = dateRanges;
    }

    AnalyticsModels.SummaryResponse summary(AnalyticsModels.DateRangeInput input) {
        var range = dateRanges.optional(input);
        var totals = repository.totals(range.startDate(), range.endDate());
        var statuses = statuses(range, totals.boletoCount());
        BigDecimal average = totals.boletoCount() == 0 ? BigDecimal.ZERO
                : totals.totalAmount().divide(BigDecimal.valueOf(totals.boletoCount()), 2, RoundingMode.HALF_UP);
        return new AnalyticsModels.SummaryResponse(range.startDate(), range.endDate(), totals.boletoCount(),
                totals.totalAmount(), average, statuses);
    }

    List<AnalyticsModels.DailyBoleto> daily(AnalyticsModels.DateRangeInput input) {
        var range = dateRanges.required(input);
        return repository.daily(range.startDate(), range.endDate());
    }

    List<AnalyticsModels.StatusSummary> statuses(AnalyticsModels.DateRangeInput input) {
        var range = dateRanges.optional(input);
        return statuses(range, repository.totals(range.startDate(), range.endDate()).boletoCount());
    }

    AnalyticsModels.ProcessingMetrics processingMetrics() {
        var outbox = repository.outbox();
        return new AnalyticsModels.ProcessingMetrics(repository.processedReceipts(), outbox.pendingEvents(),
                outbox.publishedEvents(), List.of("failedOperations", "averageProcessingDuration"));
    }

    AnalyticsModels.OutboxMetrics outboxMetrics() {
        var outbox = repository.outbox();
        Long oldestAge = outbox.oldestPendingOccurredAt() == null ? null
                : Math.max(0, Duration.between(outbox.oldestPendingOccurredAt(), Instant.now()).toSeconds());
        return new AnalyticsModels.OutboxMetrics(outbox.totalEvents(), outbox.pendingEvents(),
                outbox.publishedEvents(), outbox.eventsWithRetryAttempts(), oldestAge);
    }

    AnalyticsModels.DatabaseHealth databaseHealth() {
        long start = System.nanoTime();
        boolean available = repository.connectionAvailable();
        long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return new AnalyticsModels.DatabaseHealth(available, elapsed, repository.postgresqlMajorVersion());
    }

    private List<AnalyticsModels.StatusSummary> statuses(AnalyticsDateRanges.ResolvedDateRange range, long total) {
        return repository.statuses(range.startDate(), range.endDate()).stream()
                .map(status -> new AnalyticsModels.StatusSummary(status.status(), status.boletoCount(),
                        total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(status.boletoCount())
                                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2,
                                        RoundingMode.HALF_UP),
                        status.totalAmount()))
                .toList();
    }
}
