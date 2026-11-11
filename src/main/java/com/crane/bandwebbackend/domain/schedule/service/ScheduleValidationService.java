package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleCreateRequest;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-106·109: 저장 없이 요청 내용을 병합하고 참석 대상과 경고를 계산한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleValidationService {
    private final MembershipService memberships;
    private final PerformanceService performances;
    private final PracticeScheduleRepository schedules;
    private final ScheduleTargetReader targetReader;
    private final ScheduleEditService warnings;
    private final EntityManager entityManager;

    /** 일정 생성한다 */
    @Transactional(readOnly = true)
    public Map<String, Object> create(
            Long clubId, Long performanceId, Long actorUserId, ScheduleCreateRequest request) {
        // 저장 없이 관리자 권한과 동아리·공연의 편집 가능 상태를 확인한다.
        memberships.requireAdmin(clubId, actorUserId);
        var performance = performances.require(clubId, performanceId);
        requireActiveClub(performance.getClub().getStatus());
        requireEditable(performance.getStatus());
        if (request == null || request.startAt() == null || request.endAt() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "시작·종료 시각이 필요합니다.");
        }
        ScheduleStatus status = request.status() == null ? ScheduleStatus.TENTATIVE : request.status();
        if (status == ScheduleStatus.CANCELED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "취소 상태는 생성할 수 없습니다.");
        }
        // 대상 ID와 UTC 시간 범위를 정규화하고 참조 경계를 검증한다.
        List<Long> teamIds = ids(request.teamIds(), "teamIds");
        List<Long> songIds = ids(request.songIds(), "songIds");
        validateTargets(performanceId, teamIds, songIds);
        LocalDateTime start =
                request.startAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime end =
                request.endAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        validateTime(start, end);
        if (request.title() != null && request.title().length() > 100
                || request.location() != null && request.location().length() > 200
                || request.memo() != null && request.memo().length() > 500) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "일정 문자열 길이가 유효하지 않습니다.");
        }
        return result(
                clubId,
                performanceId,
                performance.getVersion(),
                null,
                status,
                start,
                end,
                Boolean.TRUE.equals(request.forAllParticipants()),
                teamIds,
                songIds,
                true);
    }

    /** 일정 수정한다 */
    @Transactional(readOnly = true)
    public Map<String, Object> edit(
            Long clubId, Long performanceId, Long scheduleId, Long actorUserId, JsonNode patch) {
        // 현재 일정과 revision을 조회해 저장될 값과 동일한 병합 결과를 만든다.
        memberships.requireAdmin(clubId, actorUserId);
        var performance = performances.require(clubId, performanceId);
        requireActiveClub(performance.getClub().getStatus());
        requireEditable(performance.getStatus());
        PracticeSchedule schedule = schedules
                .findByIdAndPerformance_IdAndPerformance_Club_Id(scheduleId, performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
        if (schedule.getStatus() == ScheduleStatus.CANCELED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "취소 일정은 수정할 수 없습니다.");
        }
        if (patch == null || !patch.isObject() || !patch.hasNonNull("version")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "version이 필요합니다.");
        }
        if (!String.valueOf(schedule.getVersion()).equals(patch.path("version").asText())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "일정 버전이 달라졌습니다.");
        }
        if (patch.has("status")
                && !schedule.getStatus().name().equals(patch.path("status").asText())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "상태 전이는 별도 API로 처리합니다.");
        }
        // 패치에 없는 시간·대상·전체 참석 여부는 현재 일정 값을 유지한다.
        LocalDateTime start = patch.has("startAt") ? parseTime(patch.get("startAt")) : schedule.getStartAt();
        LocalDateTime end = patch.has("endAt") ? parseTime(patch.get("endAt")) : schedule.getEndAt();
        validateTime(start, end);
        List<Long> teamIds = patch.has("teamIds")
                ? ids(patch.get("teamIds"), "teamIds")
                : targetReader.teamIds(List.of(scheduleId)).getOrDefault(scheduleId, List.of()).stream()
                        .map(Long::valueOf)
                        .toList();
        List<Long> songIds = patch.has("songIds")
                ? ids(patch.get("songIds"), "songIds")
                : targetReader.songIds(List.of(scheduleId)).getOrDefault(scheduleId, List.of()).stream()
                        .map(Long::valueOf)
                        .toList();
        validateTargets(performanceId, teamIds, songIds);
        boolean all = patch.has("forAllParticipants")
                ? bool(patch.get("forAllParticipants"))
                : schedule.isForAllParticipants();
        for (String field : List.of("title", "location", "memo")) {
            if (patch.has(field) && !patch.get(field).isNull()) {
                int max = field.equals("title") ? 100 : field.equals("location") ? 200 : 500;
                if (!patch.get(field).isTextual() || patch.get(field).asText().length() > max) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "가 유효하지 않습니다.");
                }
            }
        }
        if (patch.has("eventType")) {
            try {
                ScheduleType.valueOf(patch.path("eventType").asText());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "eventType이 유효하지 않습니다.");
            }
        }
        return result(
                clubId,
                performanceId,
                performance.getVersion(),
                scheduleId,
                schedule.getStatus(),
                start,
                end,
                all,
                teamIds,
                songIds,
                false);
    }

    /** 검증 결과와 계산된 영향을 공통 응답 맵으로 구성한다. */
    private Map<String, Object> result(
            Long clubId,
            Long performanceId,
            Long performanceVersion,
            Long excludeScheduleId,
            ScheduleStatus status,
            LocalDateTime start,
            LocalDateTime end,
            boolean all,
            List<Long> teamIds,
            List<Long> songIds,
            boolean includeParticipants) {
        // 대상 규칙에서 참석 소속을 계산하고 확정 일정의 빈 대상 여부를 오류로 만든다.
        Set<Long> memberIds = attendees(performanceId, all, teamIds, songIds);
        List<Object> hardErrors = new ArrayList<>();
        if (status == ScheduleStatus.CONFIRMED && memberIds.isEmpty()) {
            hardErrors.add(Map.of("type", "SCHEDULE_TARGET_EMPTY", "message", "참석 대상이 없습니다."));
        }
        // 실제 저장 없이 후보 시간과 참석자 기준의 충돌 경고를 계산한다.
        List<Object> warningItems = new ArrayList<>(
                warnings.warningsForCandidate(clubId, performanceId, excludeScheduleId, start, end, memberIds));
        Map<String, String> versions = Map.of("performance", performanceVersion.toString());
        if (!includeParticipants) {
            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("hardErrors", hardErrors);
            response.put("warnings", warningItems);
            response.put("confirmed", false);
            response.put("expiresAt", null);
            response.put("versions", versions);
            return response;
        }
        List<Map<String, String>> participants = memberIds.stream()
                .sorted()
                .map(id -> {
                    Membership member = entityManager.find(Membership.class, id);
                    String name =
                            member.getDisplayName() == null ? member.getUser().getName() : member.getDisplayName();
                    return Map.of("membershipId", id.toString(), "displayName", name);
                })
                .toList();
        return Map.of(
                "hardErrors", hardErrors, "warnings", warningItems, "participants", participants, "versions", versions);
    }

    /** 전체·팀·곡 대상 조건으로 실제 참여자 집합을 계산한다. */
    private Set<Long> attendees(Long performanceId, boolean all, List<Long> teamIds, List<Long> songIds) {
        // 전체·팀·곡 대상에서 승인 참여자의 소속 ID를 합집합으로 수집한다.
        Set<Long> result = new HashSet<>();
        if (all) {
            addIds(
                    result,
                    entityManager
                            .createNativeQuery("""
					SELECT membership_id FROM performance_member
					WHERE performance_id = :performanceId AND status = 'APPROVED'
					""")
                            .setParameter("performanceId", performanceId)
                            .getResultList());
        }
        for (Long teamId : teamIds) {
            addIds(
                    result,
                    entityManager
                            .createNativeQuery("""
					SELECT pm.membership_id FROM team_member tm
					JOIN performance_member pm ON pm.id = tm.performance_member_id
					WHERE tm.team_id = :teamId AND pm.status = 'APPROVED'
					""")
                            .setParameter("teamId", teamId)
                            .getResultList());
        }
        for (Long songId : songIds) {
            addIds(
                    result,
                    entityManager
                            .createNativeQuery("""
					SELECT pm.membership_id FROM song_session_member ssm
					JOIN performance_member pm ON pm.id = ssm.performance_member_id
					WHERE ssm.song_id = :songId AND pm.status = 'APPROVED'
					""")
                            .setParameter("songId", songId)
                            .getResultList());
        }
        return result;
    }

    /** 네이티브 쿼리 숫자 결과를 식별자 집합에 추가한다. */
    private static void addIds(Set<Long> target, List<?> rows) {
        rows.forEach(value -> target.add(((Number) value).longValue()));
    }

    /** 요청한 팀과 곡이 모두 해당 공연에 속하는지 검증한다. */
    private void validateTargets(Long performanceId, List<Long> teamIds, List<Long> songIds) {
        for (Long id : teamIds) {
            Number count = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM team WHERE id = :id AND performance_id = :performanceId")
                    .setParameter("id", id)
                    .setParameter("performanceId", performanceId)
                    .getSingleResult();
            if (count.longValue() == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "팀을 찾을 수 없습니다.");
        }
        for (Long id : songIds) {
            Number count = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM song WHERE id = :id AND performance_id = :performanceId")
                    .setParameter("id", id)
                    .setParameter("performanceId", performanceId)
                    .getSingleResult();
            if (count.longValue() == 0) throw new BusinessException(ErrorCode.NOT_FOUND, "곡을 찾을 수 없습니다.");
        }
    }

    /** 문자열 대상 식별자 목록을 검증해 양의 정수 목록으로 변환한다. */
    private static List<Long> ids(List<String> raw, String field) {
        if (raw == null) return List.of();
        List<Long> result =
                raw.stream().map(value -> ApiIds.positive(value, field)).toList();
        if (new HashSet<>(result).size() != result.size())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "에 중복 ID가 있습니다.");
        return result;
    }

    /** JSON 배열의 대상 식별자를 검증해 양의 정수 목록으로 변환한다. */
    private static List<Long> ids(JsonNode raw, String field) {
        if (!raw.isArray()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "는 배열이어야 합니다.");
        List<String> values = new ArrayList<>();
        raw.forEach(value -> values.add(value.asText()));
        return ids(values, field);
    }

    /** JSON 노드를 필수 불리언 값으로 변환한다. */
    private static boolean bool(JsonNode raw) {
        if (!raw.isBoolean()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "boolean 값이 유효하지 않습니다.");
        return raw.asBoolean();
    }

    /** JSON 오프셋 시각을 UTC 지역 시각으로 변환한다. */
    private static LocalDateTime parseTime(JsonNode raw) {
        if (!raw.isTextual()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "일시가 유효하지 않습니다.");
        try {
            return OffsetDateTime.parse(raw.asText())
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "일시가 유효하지 않습니다.");
        }
    }

    /** 일정 시작과 종료 시각의 순서 및 최대 길이를 검증한다. */
    private static void validateTime(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)
                || start.getMinute() % 30 != 0
                || end.getMinute() % 30 != 0
                || start.getSecond() != 0
                || end.getSecond() != 0
                || start.getNano() != 0
                || end.getNano() != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "시각은 30분 단위이며 종료가 시작보다 뒤여야 합니다.");
        }
    }

    /** 공연 상태가 일정 편집을 허용하는지 확인한다. */
    private static void requireEditable(PerformanceStatus status) {
        if (status != PerformanceStatus.DRAFT
                && status != PerformanceStatus.ACTIVE
                && status != PerformanceStatus.LOCKED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "일정을 검사할 수 없는 공연 상태입니다.");
        }
    }

    /** 동아리가 일정 변경 가능한 활성 상태인지 확인한다. */
    private static void requireActiveClub(ClubStatus status) {
        if (status != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 일정을 검사할 수 없습니다.");
        }
    }
}
