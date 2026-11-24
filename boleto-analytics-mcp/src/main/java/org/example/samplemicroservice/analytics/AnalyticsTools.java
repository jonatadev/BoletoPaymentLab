package org.example.samplemicroservice.analytics;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
class AnalyticsTools {
    private final AnalyticsService analytics;
    private final AnalyticsTelemetry telemetry;

    AnalyticsTools(AnalyticsService analytics, AnalyticsTelemetry telemetry) {
        this.analytics = analytics;
        this.telemetry = telemetry;
    }

    @Tool(
            name = "get_boleto_summary",
            description = "Returns boleto totals and status aggregates for a bounded period.")
    AnalyticsModels.SummaryResponse getBoletoSummary(AnalyticsModels.DateRangeInput input) {
        return telemetry.observe("get_boleto_summary", () -> analytics.summary(input));
    }

    @Tool(
            name = "get_boletos_by_day",
            description = "Returns chronological boleto count and amount totals by issue date.")
    List<AnalyticsModels.DailyBoleto> getBoletosByDay(AnalyticsModels.DateRangeInput input) {
        return telemetry.observe("get_boletos_by_day", () -> analytics.daily(input));
    }

    @Tool(name = "get_boletos_by_status", description = "Returns boleto count, percentage and amount totals by status.")
    List<AnalyticsModels.StatusSummary> getBoletosByStatus(AnalyticsModels.DateRangeInput input) {
        return telemetry.observe("get_boletos_by_status", () -> analytics.statuses(input));
    }

    @Tool(
            name = "get_processing_metrics",
            description = "Returns processing receipts and available Outbox processing metrics.")
    AnalyticsModels.ProcessingMetrics getProcessingMetrics() {
        return telemetry.observe("get_processing_metrics", analytics::processingMetrics);
    }

    @Tool(
            name = "get_outbox_metrics",
            description = "Returns aggregate pending, published and retry-attempt Outbox metrics.")
    AnalyticsModels.OutboxMetrics getOutboxMetrics() {
        return telemetry.observe("get_outbox_metrics", analytics::outboxMetrics);
    }

    @Tool(
            name = "get_database_health",
            description = "Returns limited database availability, latency and PostgreSQL major version.")
    AnalyticsModels.DatabaseHealth getDatabaseHealth() {
        return telemetry.observe("get_database_health", analytics::databaseHealth);
    }
}
