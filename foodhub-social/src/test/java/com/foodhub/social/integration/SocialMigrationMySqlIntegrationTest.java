package com.foodhub.social.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class SocialMigrationMySqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("foodhub_social")
            .withUsername("foodhub")
            .withPassword("foodhub");

    @Test
    void allSocialMigrationsRunOnEmptyMySqlDatabase() throws Exception {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             ResultSet result = connection.getMetaData()
                     .getTables(null, null, "post_comment", null)) {
            assertEquals(true, result.next());
        }
    }
}
