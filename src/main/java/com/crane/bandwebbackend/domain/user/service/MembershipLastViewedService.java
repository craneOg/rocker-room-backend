package com.crane.bandwebbackend.domain.user.service;

import com.crane.bandwebbackend.domain.user.dto.LastViewedResponse;
import com.crane.bandwebbackend.domain.user.repository.MembershipLastViewedRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MembershipLastViewedService {

    private final MembershipLastViewedRepository repository;

    // 타인·없는 소속은 모두 404로 같게 보여 존재를 숨긴다(G-04)
    @Transactional
    public LastViewedResponse touch(long userId, long id) {
        // DATETIME(6)에 맞춰 마이크로초로 자른 값을 저장하고 그대로 응답한다
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (repository.touch(id, userId, now) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");
        }
        return LastViewedResponse.builder()
                .membershipId(id)
                .lastViewedAt(KstTime.toKst(now))
                .build();
    }
}
