package com.amiva.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    @CoreDb
    public Flyway coreFlyway(@CoreDb DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-core")
                .load();
    }

    @Bean(initMethod = "migrate")
    @DataDb
    public Flyway dataFlyway(@DataDb DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-data")
                .load();
    }

    // Las dos bases migran antes de que se cree el EntityManagerFactory
    @Bean
    static EntityManagerFactoryDependsOnPostProcessor flywayBeforeJpa() {
        return new EntityManagerFactoryDependsOnPostProcessor(Flyway.class);
    }
}
