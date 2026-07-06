package com.nexusbank.notifications.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Gerencia as migrations do schema notifications.
 * baselineOnMigrate=true garante que o Flyway não tente recriar o schema
 * quando o banco já tem as tabelas provenientes do monólito core.
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway notificationsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("notifications")
                .locations("classpath:db/migration/notifications")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }
}
