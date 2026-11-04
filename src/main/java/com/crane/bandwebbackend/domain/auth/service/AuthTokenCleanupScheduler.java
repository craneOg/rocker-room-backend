package com.crane.bandwebbackend.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 실행 시각만 정한다. 실패하면 Spring이 예외를 기록하고 다음 주기에 다시 실행한다.
// 서버 한 대 전제(0.5)라 분산 락은 없다. 여러 대가 되면 조건부 DELETE라 중복 실행은 안전하지만 부하가 늘어난다
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthTokenCleanupScheduler {

    private final AuthTokenCleanupService cleanup;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void clearExpiredProfiles() {
        int cleared = cleanup.clearExpiredProfiles();
        if (cleared > 0) {
            log.info("만료된 1회용 토큰의 카카오 프로필 값 삭제 count={}", cleared);
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void deleteExpired() {
        AuthTokenCleanupService.Deleted deleted = cleanup.deleteExpired();
        log.info(
                "만료 토큰 정리 oneTimeTokens={} refreshTokens={} blacklistEntries={}",
                deleted.oneTimeTokens(),
                deleted.refreshTokens(),
                deleted.blacklistEntries());
    }
}
