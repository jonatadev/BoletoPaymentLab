package org.example.samplemicroservice.analytics;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AnalyticsRepository {
    private final JdbcClient jdbc;

    AnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Totals totals(LocalDate startDate, LocalDate endDate) {
        return jdbc.sql("""
                select coalesce(sum(boleto_count), 0) as boleto_count,
                       coalesce(sum(total_amount), 0) as total_amount
                from analytics.boleto_daily
                where issued_date between :startDate and :endDate
                """).param("startDate", startDate).param("endDate", endDate)
                .query((resultSet, rowNumber) -> new Totals(
                        resultSet.getLong("boleto_count"), resultSet.getBigDecimal("total_amount")))
                .single();
    }

    List<AnalyticsModels.DailyBoleto> daily(LocalDate startDate, LocalDate endDate) {
        return jdbc.sql("""
                select issued_date, boleto_count, total_amount
                from analytics.boleto_daily
                where issued_date between :startDate and :endDate
                order by issued_date
                """).param("startDate", startDate).param("endDate", endDate)
                .query((resultSet, rowNumber) -> new AnalyticsModels.DailyBoleto(
                        resultSet.getObject("issued_date", LocalDate.class),
                        resultSet.getLong("boleto_count"), resultSet.getBigDecimal("total_amount")))
                .list();
    }

    List<StatusAggregate> statuses(LocalDate startDate, LocalDate endDate) {
        return jdbc.sql("""
                select status, coalesce(sum(boleto_count), 0) as boleto_count,
                       coalesce(sum(total_amount), 0) as total_amount
                from analytics.boleto_status
                where issued_date between :startDate and :endDate
                group by status
                order by status
                """).param("startDate", startDate).param("endDate", endDate)
                .query((resultSet, rowNumber) -> new StatusAggregate(
                        resultSet.getString("status"), resultSet.getLong("boleto_count"),
                        resultSet.getBigDecimal("total_amount")))
                .list();
    }

    OutboxAggregate outbox() {
        return jdbc.sql("""
                select coalesce(sum(total_events), 0) as total_events,
                       coalesce(sum(pending_events), 0) as pending_events,
                       coalesce(sum(published_events), 0) as published_events,
                       coalesce(sum(events_with_retry_attempts), 0) as events_with_retry_attempts,
                       min(oldest_pending_occurred_at) as oldest_pending_occurred_at
                from analytics.outbox_summary
                """).query((resultSet, rowNumber) -> new OutboxAggregate(
                        resultSet.getLong("total_events"), resultSet.getLong("pending_events"),
                        resultSet.getLong("published_events"), resultSet.getLong("events_with_retry_attempts"),
                        resultSet.getTimestamp("oldest_pending_occurred_at") == null ? null
                                : resultSet.getTimestamp("oldest_pending_occurred_at").toInstant()))
                .single();
    }

    long processedReceipts() {
        return jdbc.sql("select coalesce(sum(processed_event_receipts), 0) from analytics.processing_summary")
                .query(Long.class).single();
    }

    int postgresqlMajorVersion() {
        String version = jdbc.sql("show server_version_num").query(String.class).single();
        return Integer.parseInt(version) / 10_000;
    }

    boolean connectionAvailable() {
        return jdbc.sql("select 1").query(Integer.class).single() == 1;
    }

    record Totals(long boletoCount, BigDecimal totalAmount) {
    }

    record StatusAggregate(String status, long boletoCount, BigDecimal totalAmount) {
    }

    record OutboxAggregate(long totalEvents, long pendingEvents, long publishedEvents,
            long eventsWithRetryAttempts, java.time.Instant oldestPendingOccurredAt) {
    }
}
