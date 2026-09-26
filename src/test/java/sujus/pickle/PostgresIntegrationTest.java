package sujus.pickle;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A disposable real PostgreSQL instance; tests never connect to the developer database. */
public abstract class PostgresIntegrationTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");
    static { DATABASE.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
        properties.add("spring.flyway.ignore-migration-patterns", () -> "");
        properties.add("logging.level.org.springframework", () -> "WARN");
        properties.add("logging.level.org.hibernate", () -> "WARN");
        properties.add("logging.level.org.flywaydb", () -> "WARN");
    }
}
