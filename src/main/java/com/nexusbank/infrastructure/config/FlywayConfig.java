package com.nexusbank.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuração manual do Flyway com um bean por schema de módulo.
 *
 * Cada bounded context possui schema e historico de migrations isolados.
 * O auto-config do Spring (spring.flyway.enabled=false) é desabilitado para
 * evitar conflito com esses beans.
 *
 * Schemas gerenciados: identity, corebanking, payments, fraud.
 * O schema notifications é gerenciado pelo serviço extraído (notifications-service).
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway identityFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("identity")
                .locations("classpath:db/migration/identity")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway corebankingFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("corebanking")
                .locations("classpath:db/migration/corebanking")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway paymentsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("payments")
                .locations("classpath:db/migration/payments")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway fraudFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("fraud")
                .locations("classpath:db/migration/fraud")
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }
}
