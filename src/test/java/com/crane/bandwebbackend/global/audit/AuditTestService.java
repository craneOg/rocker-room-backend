package com.crane.bandwebbackend.global.audit;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 테스트 전용 최소 서비스: 감사 기록을 서비스 트랜잭션 안에서 호출
@Service
@RequiredArgsConstructor
public class AuditTestService {

    private final AuditRecorder auditRecorder;

    @Transactional
    public void recordInTx(String action, String reason, Map<String, Object> detail) {
        auditRecorder.record(1L, 2L, action, "CLUB", 3L, reason, detail);
    }

    @Transactional
    public void recordThenFail() {
        auditRecorder.record(1L, 2L, "TEST_ACTION", "CLUB", 3L, null, null);
        throw new IllegalStateException("본 작업 실패");
    }
}
