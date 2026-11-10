package com.aidbridge.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTest {
    private String url;
    private String username;
    private String password;

    @BeforeEach
    void requireDedicatedTestDatabase() throws Exception {
        url = requiredEnvironment("AIDBRIDGE_TEST_DATABASE_URL");
        username = requiredEnvironment("AIDBRIDGE_TEST_DATABASE_USERNAME");
        password = requiredEnvironment("AIDBRIDGE_TEST_DATABASE_PASSWORD");
        boolean dedicatedTestUrl =
                url.equals("jdbc:postgresql://localhost:5432/aidbridge_test")
                        || url.equals("jdbc:postgresql://127.0.0.1:5432/aidbridge_test");
        assertThat(dedicatedTestUrl)
                .as("Tests must use localhost:5432/aidbridge_test")
                .isTrue();
        try (Connection connection = connect()) {
            assertThat(connection.getCatalog())
                    .isEqualTo("aidbridge_test");
        }
    }

    @Test
    void migratesEmptySchemaThroughV5() throws Exception {
        String schema = newSchemaName("b02_fresh_");
        Flyway flyway = configuration(schema).load();
        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isEqualTo(5);
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion())
                .isEqualTo("5");

        try (Connection connection = connect();
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM information_schema.tables
                     WHERE table_schema = ?
                       AND table_type = 'BASE TABLE'
                       AND table_name <> 'flyway_schema_history'
                     """)) {
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(26);
            }
        }
    }

    @Test
    void upgradesV1ToV5WithoutLosingExistingUser() throws Exception {
        String schema = newSchemaName("b02_upgrade_");
        UUID userId = UUID.randomUUID();
        Flyway initial = configuration(schema)
                .target(MigrationVersion.fromVersion("1"))
                .load();
        assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = connect()) {
            connection.setSchema(schema);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO users (
                        id, full_name, email, password_hash
                    )
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setObject(1, userId);
                statement.setString(2, "Migration Test User");
                statement.setString(3, "migration-test@example.com");
                statement.setString(4, "migration-fixture-not-for-login");
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }
        Flyway upgraded = configuration(schema).load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
        upgraded.validate();
        assertThat(upgraded.info().current().getVersion().getVersion())
                .isEqualTo("5");

        try (Connection connection = connect()) {
            connection.setSchema(schema);
            try (var statement = connection.prepareStatement("""
                    SELECT email, status
                    FROM users
                    WHERE id = ?
                    """)) {
                statement.setObject(1, userId);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("email"))
                            .isEqualTo("migration-test@example.com");
                    assertThat(rows.getString("status"))
                            .isEqualTo("ACTIVE");
                }
            }
        }
    }

    private FluentConfiguration configuration(String schema) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                // Keep the original B02 V1-V5 regression stable; B04MigrationIT verifies V6.
                .target(MigrationVersion.fromVersion("5"))
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .cleanDisabled(true);
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(url, username, password);
    }

    private String newSchemaName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String requiredEnvironment(String name) {String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }
        return value;
    }
}
