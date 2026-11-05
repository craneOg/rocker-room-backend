package com.crane.bandwebbackend.global.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    ImageStorage imageStorage(StorageProperties properties) {
        return new R2ImageStorage(properties);
    }
}
