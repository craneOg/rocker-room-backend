package com.crane.bandwebbackend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 정리 배치용 스케줄러. 테스트는 app.scheduling.enabled=false로 꺼서 정리 작업이 테스트 데이터를 지우지 않게 한다
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
