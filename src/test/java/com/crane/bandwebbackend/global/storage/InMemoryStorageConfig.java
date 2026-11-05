package com.crane.bandwebbackend.global.storage;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// 사진 저장소를 메모리 구현으로 바꾼다. 사진을 다루는 테스트가 @Import 한다
@TestConfiguration
public class InMemoryStorageConfig {

    @Bean
    @Primary
    InMemoryImageStorage inMemoryImageStorage() {
        return new InMemoryImageStorage();
    }
}
