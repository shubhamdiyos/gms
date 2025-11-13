package com.gms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${DB_HOST:localhost}")
    private String dbHost;

    @Value("${DB_PORT:5432}")
    private String dbPort;

    @Value("${DB_NAME:gms}")
    private String dbName;

    @Value("${DB_USERNAME:gms_user}")
    private String dbUsername;

    @Value("${DB_PASSWORD:gms_password}")
    private String dbPassword;

    @Value("${DB_SSL_PARAMS:}")
    private String dbSslParams;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Construct URL with optional SSL parameters
        String url = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbName);
        if (dbSslParams != null && !dbSslParams.trim().isEmpty()) {
            url += "?" + dbSslParams;
        }

        return DataSourceBuilder.create()
                .url(url)
                .username(dbUsername)
                .password(dbPassword)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}

