package com.hrms.employee.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The app never changes the schema: the prod database user has no DDL rights, and a person runs each
 * script in {@code db/migration} by hand. At startup Flyway only checks that every script in the code
 * has been applied (and none was edited), so a missing script stops the app with a clear message
 * instead of failing later on a missing column.
 */
@Configuration
public class FlywayValidateOnly {

    @Bean
    FlywayMigrationStrategy validateOnly() {
        return flyway -> flyway.validate();
    }
}
