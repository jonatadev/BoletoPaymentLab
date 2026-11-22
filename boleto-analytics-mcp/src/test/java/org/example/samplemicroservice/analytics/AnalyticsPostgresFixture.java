package org.example.samplemicroservice.analytics;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class AnalyticsPostgresFixture {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.12-bookworm");
    static final String READER = "mcp_reader";
    static final String READER_PASSWORD = "mcp-reader-test";

    static {
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:migration", "classpath:vendor/postgresql").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute("create role " + READER + " login password '" + READER_PASSWORD + "'");
            statement.execute("grant connect on database " + POSTGRES.getDatabaseName() + " to " + READER);
            statement.execute("grant usage on schema analytics to " + READER);
            statement.execute("grant select on all tables in schema analytics to " + READER);
            statement.execute("alter role " + READER + " set default_transaction_read_only = on");
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot provision the MCP reader test role", exception);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "analytics-test-postgres-stop"));
    }

    private AnalyticsPostgresFixture() {
    }
}
