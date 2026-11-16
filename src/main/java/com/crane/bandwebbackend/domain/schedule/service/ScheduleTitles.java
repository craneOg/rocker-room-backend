package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import java.util.List;

/** API-106·107·109·110: 입력 또는 자동 생성이 끝난 최종 제목을 같은 기준으로 검증한다. */
final class ScheduleTitles {
    private ScheduleTitles() {}

    static String resolve(
            String title, Performance performance, ScheduleType type, String firstTeamName, int teamCount) {
        if (title == null || title.isBlank()) {
            String prefix = performance.getPerformDate() == null
                    ? ""
                    : performance.getPerformDate().getYear() + "년 "
                            + performance.getPerformDate().getMonthValue() + "월 공연 ";
            String team = firstTeamName == null
                    ? ""
                    : firstTeamName + (teamCount > 1 ? " 외 " + (teamCount - 1) + "팀" : "") + " ";
            String suffix = switch (type) {
                case PRACTICE -> "합주";
                case REHEARSAL -> "리허설";
                case AUDITION -> "오디션";
                case ETC -> "일정";
            };
            title = prefix + team + suffix;
        }
        if (title.length() > 100) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "일정 제목은 100자 이하여야 합니다.",
                    List.of(ErrorDetail.of("title", "INVALID_VALUE")));
        }
        return title;
    }
}
