package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceDetailResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformancePolicyResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleLookup;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceDetailReader {
    private final PerformanceService performances;
    private final MembershipService memberships;
    private final EntityManager entityManager;
    private final ScheduleLookup schedules;

    /** 공연 정보를 조회한다 */
    @Transactional(readOnly = true)
    public PerformanceDetailResponse read(Long clubId, Long performanceId, Long actorUserId) {
        // 공연과 정책 접근 권한을 확인하고 두 엔티티를 조회한다.
        var performance = performances.detail(clubId, performanceId, actorUserId);
        var policy = performances.policy(clubId, performanceId, actorUserId);
        var actor = memberships.requireMember(clubId, actorUserId);
        // API-039: GUEST에는 counts·nextSchedule을 생략한다.
        if (actor.getMemberType() == MemberType.GUEST) {
            return new PerformanceDetailResponse(
                    PerformanceResponse.from(performance), PerformancePolicyResponse.from(policy), null, null);
        }
        // 팀·후보곡·확정곡·승인 참여자 수를 상세 화면 집계로 묶는다.
        var counts = new PerformanceDetailResponse.Counts(
                count("SELECT COUNT(*) FROM team WHERE performance_id = :id", performanceId),
                count("SELECT COUNT(*) FROM song WHERE performance_id = :id AND status = 'PROPOSED'", performanceId),
                count("SELECT COUNT(*) FROM song WHERE performance_id = :id AND status = 'CONFIRMED'", performanceId),
                count(
                        "SELECT COUNT(*) FROM performance_member WHERE performance_id = :id AND status = 'APPROVED'",
                        performanceId));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // E-07: 임원은 임시 일정까지, 그 밖의 부원은 확정 일정만 대상으로 한다(취소 제외).
        boolean admin = actor.getRole() == MembershipRole.CLUB_ADMIN;
        var next = schedules.schedulesOfPerformance(clubId, performanceId).stream()
                .filter(schedule -> (schedule.getStatus() == ScheduleStatus.CONFIRMED
                                || admin && schedule.getStatus() == ScheduleStatus.TENTATIVE)
                        && !schedule.getStartAt().isBefore(now))
                .min(Comparator.comparing(PracticeSchedule::getStartAt))
                .map(schedule -> new PerformanceDetailResponse.NextSchedule(
                        schedule.getId().toString(),
                        schedule.getTitle(),
                        schedule.getStartAt()
                                .atOffset(ZoneOffset.UTC)
                                .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                                .toOffsetDateTime()))
                .orElse(null);
        return new PerformanceDetailResponse(
                PerformanceResponse.from(performance), PerformancePolicyResponse.from(policy), counts, next);
    }

    /** 공연 상세 화면의 하위 데이터 개수를 집계한다. */
    private long count(String sql, Long performanceId) {
        return ((Number) entityManager
                        .createNativeQuery(sql)
                        .setParameter("id", performanceId)
                        .getSingleResult())
                .longValue();
    }
}
