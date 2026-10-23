package com.amiva.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseConfig {

    // Base core: la usa JPA, por eso es @Primary
    @Bean
    @CoreDb
    @ConfigurationProperties("amiva.datasource.core")
    public DataSourceProperties coreDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @CoreDb
    public DataSource coreDataSource(@CoreDb DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    @CoreDb
    public JdbcTemplate coreJdbcTemplate(@CoreDb DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // Base data: solo JDBC
    @Bean
    @DataDb
    @ConfigurationProperties("amiva.datasource.data")
    public DataSourceProperties dataDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @DataDb
    public DataSource dataDataSource(@DataDb DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @DataDb
    public JdbcTemplate dataJdbcTemplate(@DataDb DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
