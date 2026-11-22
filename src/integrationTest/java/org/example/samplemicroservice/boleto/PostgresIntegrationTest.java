package org.example.samplemicroservice.boleto;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared PostgreSQL lifecycle for the integration-test JVM. The container starts before Spring
 * evaluates dynamic datasource properties. Each scenario gets its own database because Flyway
 * also creates a fixed analytics schema outside the scenario's business schema.
 */
public abstract class PostgresIntegrationTest {
    private static final Set<String> DATABASES = new HashSet<>();
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16.11-alpine");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop, "postgres-integration-test-stop"));
    }

    protected static synchronized String postgresUrl(String schema) {
        if (!schema.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid test database name");
        }
        if (!DATABASES.contains(schema)) {
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), postgresUser(),
                    postgresPassword()); var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + schema);
                DATABASES.add(schema);
            } catch (SQLException failure) {
                throw new IllegalStateException("Cannot create isolated PostgreSQL test database", failure);
            }
        }
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/" + schema + "?currentSchema=" + schema;
    }

    protected static String postgresUser() {
        return POSTGRES.getUsername();
    }

    protected static String postgresPassword() {
        return POSTGRES.getPassword();
    }
}
