package org.example.samplemicroservice.boleto.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.example.samplemicroservice.boleto.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class OutboxAuditMigrationTests extends PostgresIntegrationTest {
    private final String schema = "bpl_it_" + UUID.randomUUID().toString().replace("-", "");
    private final String url = postgresUrl(schema);
    private final String user = postgresUser();
    private final String password = postgresPassword();

    @Test
    void shouldUpgradeMatchingHistoryAndRejectMismatchedWrites() throws SQLException {
        migrateToV4();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            seedAudits(connection);
            insertOutbox(connection, 0, 0);
            assertThat(flyway().migrate().migrationsExecuted).isEqualTo(4);
            flyway().validate();
            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() ->
                                statement.executeUpdate(
                                        """
                                        update boleto_outbox set aggregate_id =
                                            (select id from boleto where customer_id = 'other-synthetic')
                                        """))
                        .isInstanceOfSatisfying(
                                SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("23503"));
            }
            assertThatThrownBy(() -> insertOutbox(connection, 1, 2))
                    .isInstanceOfSatisfying(
                            SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("23503"));
            assertThat(insertOutbox(connection, 1, 1)).isEqualTo(1);
        }
    }

    @Test
    void shouldRollBackV5WhenLegacyHistoryDiverges() throws SQLException {
        migrateToV4();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            seedAudits(connection);
            insertOutbox(connection, 1, 2);

            assertThatThrownBy(() -> flyway().migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasStackTraceContaining("fk_boleto_outbox_audit_identity");

            try (var statement = connection.createStatement();
                    var row =
                            statement.executeQuery(
                                    """
                                    select
                                        (select count(*) from pg_constraint
                                         where conrelid in ('boleto_audit'::regclass, 'boleto_outbox'::regclass)
                                           and conname in ('uq_boleto_audit_event_identity',
                                                           'fk_boleto_outbox_audit_identity')) as constraints,
                                        (select aggregate_version from boleto_outbox) as legacy_version
                                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt("constraints")).isZero();
                assertThat(row.getLong("legacy_version")).isEqualTo(2);
            }
        }
    }

    private void migrateToV4() {
        Flyway.configure().dataSource(url, user, password).schemas(schema).target("4").load().migrate();
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(url, user, password)
                .schemas(schema)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .load();
    }

    private static void seedAudits(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
                    insert into boleto (id, customer_id, amount, due_date)
                    values (gen_random_uuid(), 'audit-synthetic', 10.00, '2035-08-30'),
                           (gen_random_uuid(), 'other-synthetic', 10.00, '2035-08-30')
                    """);
            statement.executeUpdate(
                    """
                    insert into boleto_audit
                        (event_id, boleto_id, event_type, occurred_at, source, previous_status, new_status,
                         aggregate_version, correlation_id)
                    select gen_random_uuid(), b.id, event.type, current_timestamp, 'API',
                           event.previous_status, event.status, event.version, gen_random_uuid()::text
                    from boleto b
                    cross join (values (0, 'BOLETO_ISSUED', null, 'EMITIDO'),
                                       (1, 'BOLETO_PAID', 'EMITIDO', 'PAGO'))
                        as event(version, type, previous_status, status)
                    where b.customer_id = 'audit-synthetic'
                    """);
        }
    }

    private static int insertOutbox(Connection connection, long auditVersion, long outboxVersion) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        insert into boleto_outbox
                            (event_id, aggregate_id, event_type, occurred_at, aggregate_version, correlation_id,
                             next_attempt_at)
                        select event_id, boleto_id, event_type, occurred_at, ?, correlation_id, occurred_at
                        from boleto_audit where aggregate_version = ?
                        """)) {
            statement.setLong(1, outboxVersion);
            statement.setLong(2, auditVersion);
            return statement.executeUpdate();
        }
    }
}
