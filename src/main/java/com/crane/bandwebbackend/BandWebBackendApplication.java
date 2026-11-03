package com.crane.bandwebbackend;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BandWebBackendApplication {

    // 저장은 UTC(LocalDateTime). @CreationTimestamp·@UpdateTimestamp와 now()가 JVM 기본 시간대를 쓰므로 UTC로 고정.
    // 클래스 로딩 시점에 실행되어 테스트(@SpringBootTest)에도 적용됨
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BandWebBackendApplication.class, args);
    }
}
