package com.hrms.employee;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * Base class for tests that need a real Oracle database. One throwaway container is started for the
 * whole test run, the schema is built from db/migration (so those scripts are tested too) and the
 * application starts against it exactly as it does in production, including Hibernate's schema check.
 * Skipped, not failed, when Docker is not running.
 *
 * <p>Extend it, and mark the test class {@code @Transactional} so its rows are rolled back.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(DatabaseTest.Db.class)
public abstract class DatabaseTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Db {

        // The schema owner is HRMS, which is what the scripts and the entities expect.
        @Bean
        @ServiceConnection
        OracleContainer oracle() {
            return new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
                .withUsername("HRMS")
                .withPassword("hrms_test_pw");
        }

        // The application itself only validates; the test run builds the schema from the scripts.
        @Bean
        @Primary
        FlywayMigrationStrategy migrateForTests() {
            return flyway -> flyway.migrate();
        }
    }
}
