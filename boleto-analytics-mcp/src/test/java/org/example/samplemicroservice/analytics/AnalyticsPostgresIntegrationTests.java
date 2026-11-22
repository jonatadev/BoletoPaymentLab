package org.example.samplemicroservice.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

class AnalyticsPostgresIntegrationTests {
    private final SimpleDriverDataSource writer = new SimpleDriverDataSource();
    private final SimpleDriverDataSource reader = new SimpleDriverDataSource();

    AnalyticsPostgresIntegrationTests() {
        writer.setDriverClass(org.postgresql.Driver.class);
        writer.setUrl(AnalyticsPostgresFixture.POSTGRES.getJdbcUrl());
        writer.setUsername(AnalyticsPostgresFixture.POSTGRES.getUsername());
        writer.setPassword(AnalyticsPostgresFixture.POSTGRES.getPassword());
        reader.setDriverClass(org.postgresql.Driver.class);
        reader.setUrl(AnalyticsPostgresFixture.POSTGRES.getJdbcUrl());
        reader.setUsername(AnalyticsPostgresFixture.READER);
        reader.setPassword(AnalyticsPostgresFixture.READER_PASSWORD);
    }

    @BeforeEach
    void clearAnalyticsData() {
        JdbcClient.create(writer).sql("delete from processed_boleto_event").update();
        JdbcClient.create(writer).sql("delete from boleto_outbox").update();
        JdbcClient.create(writer).sql("delete from boleto_audit").update();
        JdbcClient.create(writer).sql("delete from idempotent_issuance").update();
        JdbcClient.create(writer).sql("delete from boleto").update();
    }

    @Test
    void shouldReturnReadOnlyDailyAndStatusAggregatesFromViews() {
        seedBoleto("EMITIDO", new BigDecimal("10.00"), Instant.parse("2026-02-01T11:00:00Z"));
        seedBoleto("PAGO", new BigDecimal("30.00"), Instant.parse("2026-02-01T15:00:00Z"));
        seedBoleto("PAGO", new BigDecimal("20.00"), Instant.parse("2026-02-02T12:00:00Z"));

        var service = service();
        var summary = service.summary(new AnalyticsModels.DateRangeInput(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2)));

        assertThat(summary.totalBoletos()).isEqualTo(3);
        assertThat(summary.totalAmount()).isEqualByComparingTo("60.00");
        assertThat(summary.averageAmount()).isEqualByComparingTo("20.00");
        assertThat(service.daily(new AnalyticsModels.DateRangeInput(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2))))
                .extracting(AnalyticsModels.DailyBoleto::boletoCount).containsExactly(2L, 1L);
        assertThat(summary.statuses()).extracting(AnalyticsModels.StatusSummary::status)
                .containsExactly("EMITIDO", "PAGO");
    }

    @Test
    void shouldRejectWritesForTheAnalyticsReaderRole() {
        assertThatThrownBy(() -> JdbcClient.create(reader)
                .sql("insert into boleto (id, customer_id, amount, due_date) values (:id, :customer, :amount, :dueDate)")
                .param("id", UUID.randomUUID()).param("customer", "synthetic-customer")
                .param("amount", BigDecimal.ONE).param("dueDate", LocalDate.of(2026, 12, 1)).update())
                .hasMessageContaining("cannot execute INSERT in a read-only transaction");
    }

    private AnalyticsService service() {
        var properties = new AnalyticsProperties("analytics", "issuer", "jwks", 30, 366, 5);
        var ranges = new AnalyticsDateRanges(Clock.fixed(Instant.parse("2026-02-10T12:00:00Z"), ZoneOffset.UTC),
                properties);
        return new AnalyticsService(new AnalyticsRepository(JdbcClient.create(reader)), ranges);
    }

    private void seedBoleto(String status, BigDecimal amount, Instant issuedAt) {
        JdbcClient.create(writer).sql("""
                insert into boleto (id, customer_id, amount, due_date, status, issued_at, version)
                values (:id, :customer, :amount, :dueDate, :status, :issuedAt, 0)
                """).param("id", UUID.randomUUID()).param("customer", "synthetic-customer")
                .param("amount", amount).param("dueDate", LocalDate.of(2026, 12, 1))
                .param("status", status).param("issuedAt", issuedAt.atOffset(ZoneOffset.UTC)).update();
    }
}
