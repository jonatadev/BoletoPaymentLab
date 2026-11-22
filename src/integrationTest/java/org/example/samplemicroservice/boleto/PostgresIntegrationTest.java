package org.example.samplemicroservice.boleto;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared PostgreSQL lifecycle for the integration-test JVM. The container starts before Spring
 * evaluates dynamic datasource properties. Each test class creates its own schema, so sharing
 * the container does not share business data between scenarios.
 */
public abstract class PostgresIntegrationTest {
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16.11-alpine");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "postgres-integration-test-stop"));
    }

    protected static String postgresUrl(String schema) {
        return POSTGRES.getJdbcUrl() + "&currentSchema=" + schema;
    }

    protected static String postgresUser() {
        return POSTGRES.getUsername();
    }

    protected static String postgresPassword() {
        return POSTGRES.getPassword();
    }
}
