package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PracticeScheduleService {
    private final PracticeScheduleRepository repository;
    private final MembershipService membershipService;
    private final PerformanceService performanceService;
    private final ParticipationLookup participationLookup;
    private final PracticeScheduleTeamRepository teamRepository;
    private final PracticeScheduleSongRepository songRepository;

    /** SCH-01: 기간과 공개 범위를 DB 페이지 조회 전에 적용한다. */
    @Transactional(readOnly = true)
    public Page<PracticeSchedule> page(
            Long clubId,
            Long performanceId,
            Long actorUserId,
            LocalDateTime from,
            LocalDateTime to,
            ScheduleStatus status,
            ScheduleType type,
            Long teamId,
            Long songId,
            Pageable pageable) {
        // 기본 목록 API는 개인 일정 제한 없이 공통 페이지 조회로 위임한다.
        return page(clubId, performanceId, actorUserId, from, to, status, type, teamId, songId, false, pageable);
    }

    /** 일정 페이지를 조회한다 */
    @Transactional(readOnly = true)
    public Page<PracticeSchedule> page(
            Long clubId,
            Long performanceId,
            Long actorUserId,
            LocalDateTime from,
            LocalDateTime to,
            ScheduleStatus status,
            ScheduleType type,
            Long teamId,
            Long songId,
            boolean mine,
            Pageable pageable) {
        // 조회 기간을 93일로 제한하고 소속·공연 열람 권한을 확인한다.
        if (from == null
                || to == null
                || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(93)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "조회 기간은 93일 이하여야 합니다.");
        }
        Membership actor = membershipService.requireMember(clubId, actorUserId);
        performanceService.detail(clubId, performanceId, actorUserId);
        // 공연·동아리·기간 조건을 기본 Specification으로 구성한다.
        Specification<PracticeSchedule> spec = (root, query, builder) -> builder.and(
                builder.equal(root.get("performance").get("id"), performanceId),
                builder.equal(root.get("performance").get("club").get("id"), clubId),
                builder.greaterThanOrEqualTo(root.get("startAt"), from),
                builder.lessThan(root.get("startAt"), to));
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("scheduleType"), type));
        }
        if (actor.getRole() != MembershipRole.CLUB_ADMIN) {
            spec = spec.and((root, query, builder) -> builder.notEqual(root.get("status"), ScheduleStatus.TENTATIVE));
        }
        // 객원 또는 내 일정 요청은 승인 참여자가 실제 참석하는 일정 ID로 제한한다.
        Set<Long> visibleIds = null;
        if (actor.getMemberType() == MemberType.GUEST || mine) {
            Long memberId = participationLookup
                    .approvedMemberId(performanceId, actor.getId())
                    .orElse(null);
            if (memberId == null) {
                return Page.empty(pageable);
            }
            visibleIds = new HashSet<>(repository.findAttendedScheduleIds(performanceId, memberId));
        }
        // 팀·곡 필터는 기존 가시 일정 집합과 교집합으로 결합한다.
        if (teamId != null) {
            Set<Long> linked = new HashSet<>(teamRepository.findByTeam_Id(teamId).stream()
                    .map(link -> link.getPracticeSchedule().getId())
                    .toList());
            visibleIds = intersect(visibleIds, linked);
        }
        if (songId != null) {
            Set<Long> linked = new HashSet<>(songRepository.findBySong_Id(songId).stream()
                    .map(link -> link.getPracticeSchedule().getId())
                    .toList());
            visibleIds = intersect(visibleIds, linked);
        }
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                return Page.empty(pageable);
            }
            Set<Long> ids = visibleIds;
            spec = spec.and((root, query, builder) -> root.get("id").in(ids));
        }
        return repository.findAll(spec, pageable);
    }

    /** 두 대상 식별자 집합의 교집합을 계산한다. */
    private static Set<Long> intersect(Set<Long> current, Set<Long> next) {
        if (current == null) {
            return next;
        }
        current.retainAll(next);
        return current;
    }

    /** 일정 목록을 조회한다 */
    @Transactional(readOnly = true)
    public List<PracticeSchedule> list(Long clubId, Long performanceId, Long actorUserId) {
        // 일반 목록은 객원을 제외하고 관리자 여부에 따라 임시 일정을 숨긴다.
        Membership actor = membershipService.requireMember(clubId, actorUserId);
        performanceService.detail(clubId, performanceId, actorUserId);
        if (actor.getMemberType() == MemberType.GUEST) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "일정 목록을 찾을 수 없습니다.");
        }
        List<PracticeSchedule> schedules = repository.findByPerformance_IdAndPerformance_Club_Id(performanceId, clubId);
        if (actor.getRole() == MembershipRole.CLUB_ADMIN) {
            return schedules;
        }
        return schedules.stream()
                .filter(schedule -> schedule.getStatus() != ScheduleStatus.TENTATIVE)
                .toList();
    }

    /** 일정 상세 정보를 조회한다 */
    @Transactional(readOnly = true)
    public PracticeSchedule detail(Long clubId, Long performanceId, Long scheduleId, Long actorUserId) {
        // 동아리·공연·일정 경계를 모두 확인하고 권한에 따라 존재를 숨긴다.
        Membership actor = membershipService.requireMember(clubId, actorUserId);
        performanceService.detail(clubId, performanceId, actorUserId);
        PracticeSchedule schedule = repository
                .findByIdAndPerformance_IdAndPerformance_Club_Id(scheduleId, performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
        if (schedule.getStatus() == ScheduleStatus.TENTATIVE && actor.getRole() != MembershipRole.CLUB_ADMIN) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
        }
        // 객원은 본인이 참석 대상인 확정 일정만 상세 조회할 수 있다.
        if (actor.getMemberType() == MemberType.GUEST) {
            Long memberId = participationLookup
                    .approvedMemberId(performanceId, actor.getId())
                    .orElse(null);
            if (memberId == null
                    || !repository
                            .findAttendedScheduleIds(performanceId, memberId)
                            .contains(scheduleId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
            }
        }
        return schedule;
    }
}
