package org.example.samplemicroservice.boleto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class LegacyDatabaseUpgradeTests extends PostgresIntegrationTest {
    @Test
    void shouldUpgradeExistingV1RowsWithoutChangingIdentityOrDueDate() throws Exception {
        String schema = "bpl_it_" + UUID.randomUUID().toString().replace("-", "");
        String url = postgresUrl(schema);
        String user = postgresUser();
        String password = postgresPassword();
        Flyway.configure().dataSource(url, user, password).schemas(schema).target("1").load().migrate();

        UUID id = UUID.randomUUID();
        LocalDate due = LocalDate.of(2035, 8, 30);
        try (var connection = DriverManager.getConnection(url, user, password);
                var insert = connection.prepareStatement(
                        "insert into boleto (id, customer_id, amount, due_date) values (?, ?, ?, ?)")) {
            insert.setObject(1, id);
            insert.setString(2, "legacy-synthetic");
            insert.setBigDecimal(3, new BigDecimal("17.32"));
            insert.setObject(4, due);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }

        var migration = Flyway.configure().dataSource(url, user, password).schemas(schema)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql").load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(6);
        migration.validate();
        try (var connection = DriverManager.getConnection(url, user, password);
                var query = connection.prepareStatement(
                        "select id, amount, due_date, status, issued_at, version, created_at, updated_at, created_by, updated_by from boleto where id = ?")) {
            query.setObject(1, id);
            try (var row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("id", UUID.class)).isEqualTo(id);
                assertThat(row.getObject("due_date", LocalDate.class)).isEqualTo(due);
                assertThat(row.getBigDecimal("amount")).isEqualByComparingTo("17.32");
                assertThat(row.getString("status")).isEqualTo("EMITIDO");
                assertThat(row.getObject("issued_at")).isNotNull();
                assertThat(row.getLong("version")).isZero();
                assertThat(row.getObject("created_at")).isEqualTo(row.getObject("issued_at"));
                assertThat(row.getObject("updated_at")).isNull();
                assertThat(row.getString("created_by")).isNull();
                assertThat(row.getString("updated_by")).isNull();
            }
        }
    }
}
