package com.crane.bandwebbackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 실행 시각만 정한다. 서버 한 대 전제(0.5)라 분산 락은 없다. 여러 대가 되면 같은 객체를 동시에 지우려 해도
// 삭제가 멱등이라 안전하지만 목록 조회 비용이 늘어난다
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileImageCleanupScheduler {

    private final ProfileImageCleanupService cleanup;

    // 매시 17분. 실패하면 Spring이 예외를 기록하고 다음 주기에 다시 실행한다
    @Scheduled(cron = "0 17 * * * *", zone = "Asia/Seoul")
    public void cleanOrphans() {
        ProfileImageCleanupService.Result result = cleanup.cleanup();
        log.info(
                "사진 고아 객체 정리 expiredTmp={} orphans={} aborted={}",
                result.getExpiredTmpDeleted(),
                result.getOrphansDeleted(),
                result.isOrphanScanAborted());
    }
}
