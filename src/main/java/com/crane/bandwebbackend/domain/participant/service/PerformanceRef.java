package com.crane.bandwebbackend.domain.participant.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 경로(/clubs/{clubId}/performances/{performanceId}) + 인증 주체. 컨트롤러가 만들어 서비스로 넘김
@Getter
@RequiredArgsConstructor
public class PerformanceRef {

    private final Long clubId;
    private final Long performanceId;
    private final Long userId;
}
