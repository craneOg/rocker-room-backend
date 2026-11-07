package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.dto.SuccessionNoticeListResponse;
import com.crane.bandwebbackend.domain.user.dto.SuccessionNoticeResponse;
import com.crane.bandwebbackend.domain.user.repository.SuccessionNoticeQueryRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 135·136: 본인 승계 안내 조회·확인(U-06). 안내 생성은 탈퇴(9)·긴급 교체(121) 쪽 책임
@Service
@RequiredArgsConstructor
public class SuccessionNoticeService {

    private final SuccessionNoticeQueryRepository notices;

    @Transactional(readOnly = true)
    public SuccessionNoticeListResponse list(long userId) {
        return new SuccessionNoticeListResponse(notices.findUnconfirmed(userId).stream()
                .map(SuccessionNoticeResponse::of)
                .toList());
    }

    // 확인은 되돌릴 수 없고 멱등이다. 타인의 안내·없는 안내는 같은 404(G-04)
    @Transactional
    public SuccessionNoticeResponse confirm(long userId, long noticeId) {
        // UPDATE(현재 읽기)를 먼저 실행해야 동시 확인에서 낡은 스냅샷을 읽지 않는다
        notices.confirm(noticeId, userId);
        return SuccessionNoticeResponse.of(
                notices.findOwned(noticeId, userId).orElseThrow(SuccessionNoticeService::notFound));
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
    }
}
