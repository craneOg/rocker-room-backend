package com.crane.bandwebbackend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

class AuthTokenCleanupSchedulerTest {

    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("test")
    @SpringBootTest
    @Nested
    class DisabledInTests {

        @Autowired
        ApplicationContext context;

        @Test
        @DisplayName("테스트 프로필에서는 스케줄러가 꺼져 있어 정리 작업이 테스트 데이터를 지우지 않는다")
        void notScheduled() {
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class))
                    .isEmpty();
        }
    }

    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("test")
    @SpringBootTest(properties = "app.scheduling.enabled=true")
    @Nested
    class Enabled {

        @Autowired
        ScheduledAnnotationBeanPostProcessor processor;

        @Test
        @DisplayName("켜면 프로필 삭제(5분 주기)와 만료 행 삭제(매일 03:30 KST) 두 작업이 등록된다")
        void registersBothTasks() {
            List<String> tasks =
                    processor.getScheduledTasks().stream().map(Object::toString).toList();

            assertThat(tasks).hasSize(2);
            assertThat(tasks).anyMatch(t -> t.contains("clearExpiredProfiles"));
            assertThat(tasks).anyMatch(t -> t.contains("deleteExpired"));
        }
    }
}
