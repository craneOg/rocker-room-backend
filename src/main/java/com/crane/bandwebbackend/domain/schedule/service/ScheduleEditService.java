package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleSong;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleTeam;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-110: 모든 변경과 경고 계산을 한 트랜잭션에서 끝낸다. 경고는 저장을 막지 않는다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleEditService {
    private final MembershipService membershipService;
    private final PracticeScheduleRepository schedules;
    private final PracticeScheduleTeamRepository teamLinks;
    private final PracticeScheduleSongRepository songLinks;
    private final PerformanceService performances;
    private final EntityManager entityManager;
    private final AuditRecorder auditRecorder;

    /** 일정 수정한다 */
    @Transactional
    public EditResult edit(Long clubId, Long performanceId, Long scheduleId, Long actorUserId, JsonNode body) {
        // 일정 수정 권한과 낙관적 잠금에 사용할 요청 버전을 먼저 검증한다.
        membershipService.requireAdmin(clubId, actorUserId);
        if (body == null || !body.isObject() || !body.hasNonNull("version")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "version이 필요합니다.");
        }
        String version = body.path("version").asText();
        if (!version.matches("[0-9]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "version 형식이 올바르지 않습니다.");
        }
        // URL의 동아리·공연에 실제로 속한 일정만 조회한다.
        PracticeSchedule schedule = schedules
                .findByIdAndPerformance_IdAndPerformance_Club_Id(scheduleId, performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
        // 비활성 동아리, 종료된 공연, 취소된 일정에서는 수정을 금지한다.
        if (schedule.getPerformance().getClub().getStatus() != ClubStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성 동아리에서는 수정할 수 없습니다.");
        }
        if (schedule.getStatus() == ScheduleStatus.CANCELED
                || schedule.getPerformance().getStatus() == PerformanceStatus.FINISHED
                || schedule.getPerformance().getStatus() == PerformanceStatus.CANCELED) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "수정할 수 없는 일정입니다.");
        }
        // 조회 이후 다른 요청이 먼저 수정했다면 덮어쓰지 않고 충돌로 응답한다.
        if (!version.equals(String.valueOf(schedule.getVersion()))) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "일정 버전이 달라졌습니다.");
        }
        entityManager.lock(schedule, LockModeType.OPTIMISTIC_FORCE_INCREMENT);

        // PATCH에 전달된 값만 교체하고 생략된 필드는 기존 값을 유지한다.
        ScheduleType type = body.has("eventType") ? enumValue(body.get("eventType")) : schedule.getScheduleType();
        LocalDateTime start = body.has("startAt") ? dateTime(body.get("startAt")) : schedule.getStartAt();
        LocalDateTime end = body.has("endAt") ? dateTime(body.get("endAt")) : schedule.getEndAt();
        String title = body.has("title") ? string(body.get("title"), Integer.MAX_VALUE) : schedule.getTitle();
        String location = body.has("location") ? string(body.get("location"), 200) : schedule.getLocation();
        String memo = body.has("memo") ? string(body.get("memo"), 500) : schedule.getMemo();
        boolean all = body.has("forAllParticipants")
                ? booleanValue(body.get("forAllParticipants"))
                : schedule.isForAllParticipants();
        // 제목을 비웠다면 일정 유형과 대상 팀을 이용해 읽기 쉬운 기본 제목을 만든다.
        if (title == null || title.isBlank()) {
            title = automaticTitle(
                    schedule,
                    type,
                    body.has("teamIds")
                            ? ids(body.get("teamIds"), "teamIds")
                            : teamLinks.findByPracticeSchedule_Id(scheduleId).stream()
                                    .map(link -> link.getTeam().getId())
                                    .toList());
        }
        title = ScheduleTitles.resolve(title, schedule.getPerformance(), type, null, 0);
        // 엔티티가 보장하는 시간·필드 불변식을 적용하고 검증 오류로 변환한다.
        try {
            schedule.replace(type, title, start, end, location, all, memo);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage());
        }
        // teamIds가 명시된 경우에만 기존 팀 연결을 요청 값으로 완전히 교체한다.
        if (body.has("teamIds")) {
            List<Long> ids = ids(body.get("teamIds"), "teamIds");
            teamLinks.deleteAll(teamLinks.findByPracticeSchedule_Id(scheduleId));
            teamLinks.flush();
            for (Long id : ids) {
                Team team = entityManager.find(Team.class, id);
                if (team == null || !team.getPerformance().getId().equals(performanceId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "같은 공연의 팀을 찾을 수 없습니다.");
                }
                teamLinks.save(PracticeScheduleTeam.link(schedule, team));
            }
        }
        // songIds가 명시된 경우에만 기존 곡 연결을 요청 값으로 완전히 교체한다.
        if (body.has("songIds")) {
            List<Long> ids = ids(body.get("songIds"), "songIds");
            songLinks.deleteAll(songLinks.findByPracticeSchedule_Id(scheduleId));
            songLinks.flush();
            for (Long id : ids) {
                Song song = entityManager.find(Song.class, id);
                if (song == null || !song.getPerformance().getId().equals(performanceId)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "같은 공연의 곡을 찾을 수 없습니다.");
                }
                songLinks.save(PracticeScheduleSong.link(schedule, song));
            }
        }
        // 연결 변경을 DB에 반영한 뒤 확정 일정의 실제 참석 대상이 남아 있는지 재검증한다.
        entityManager.flush();
        if (schedule.getStatus() == ScheduleStatus.CONFIRMED
                && attendeeIds(scheduleId).isEmpty()) {
            throw new BusinessException(ErrorCode.RULE_VIOLATION, "SCHEDULE_TARGET_EMPTY", "확정 일정에 참석 대상이 없습니다.");
        }
        // 충돌은 저장을 막지 않고 경고로 반환하며, 수정 사실은 감사 로그에 남긴다.
        List<Map<String, Object>> warnings = warnings(schedule, true);
        auditRecorder.record(
                clubId,
                actorUserId,
                "SCHEDULE_UPDATED",
                "PRACTICE_SCHEDULE",
                scheduleId,
                null,
                Map.of("version", version));
        return new EditResult(schedule, warnings);
    }

    public record EditResult(PracticeSchedule schedule, List<Map<String, Object>> warnings) {}

    /** 일정 확정과 충돌 조회에서 같은 경고 계산을 재사용한다. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> warningsFor(Long clubId, Long performanceId, Long scheduleId, Long actorUserId) {
        // 동아리 소속과 일정 경계를 확인해 다른 동아리 일정의 존재가 새지 않게 한다.
        Membership actor = membershipService.requireMember(clubId, actorUserId);
        performances.detail(clubId, performanceId, actorUserId);
        PracticeSchedule schedule = schedules
                .findByIdAndPerformance_IdAndPerformance_Club_Id(scheduleId, performanceId, clubId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다."));
        // 일반 회원에게는 확정되었고 본인이 참석 대상인 일정의 경고만 허용한다.
        if (actor.getRole() != MembershipRole.CLUB_ADMIN) {
            if (schedule.getStatus() != ScheduleStatus.CONFIRMED
                    || !attendeeIds(scheduleId).contains(actor.getId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "일정을 찾을 수 없습니다.");
            }
        }
        // 시간 충돌·시즌 경고와 곡별 파트 부족 경고를 하나의 목록으로 합친다.
        Set<Long> visiblePerformances = performances.list(clubId, actorUserId).stream()
                .map(performance -> performance.getId())
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> result = new ArrayList<>(warningsForCandidate(
                clubId,
                performanceId,
                scheduleId,
                schedule.getStartAt(),
                schedule.getEndAt(),
                new HashSet<>(attendeeIds(scheduleId)),
                actor.getRole() == MembershipRole.CLUB_ADMIN,
                visiblePerformances));
        result.addAll(partShortages(scheduleId));
        if (actor.getRole() == MembershipRole.CLUB_ADMIN) {
            return result;
        }
        // 개인 조회에는 본인이 겹치는 항목과 본인의 표시명만 남긴다.
        return result.stream()
                .filter(item -> {
                    if (!String.valueOf(item.get("type")).startsWith("OVERLAP")) {
                        return true;
                    }
                    Object participants = item.get("participants");
                    return participants instanceof List<?> list
                            && list.stream()
                                    .anyMatch(participant -> participant instanceof Map<?, ?> person
                                            && actor.getId().toString().equals(person.get("membershipId")));
                })
                .map(item -> {
                    if (!String.valueOf(item.get("type")).startsWith("OVERLAP")) {
                        return item;
                    }
                    Map<String, Object> filtered = new java.util.LinkedHashMap<>();
                    filtered.putAll(item);
                    filtered.put("participants", List.of(participant(actor.getId())));
                    return filtered;
                })
                .toList();
    }

    /** API-113: 연결 곡의 필요 파트와 승인된 실제 연주자 수를 비교한다. */
    private List<Map<String, Object>> partShortages(Long scheduleId) {
        // 연결 곡별 요구 인원과 승인된 세션 배정 인원을 한 쿼리에서 비교한다.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery("""
				SELECT r.song_id, p.name, r.required_count,
					   (SELECT COUNT(*) FROM song_session_member ssm
						JOIN performance_member pm ON pm.id = ssm.performance_member_id
						WHERE ssm.song_id = r.song_id AND ssm.part_id = r.part_id
						  AND pm.status = 'APPROVED') AS assigned_count
				FROM practice_schedule_song pss
				JOIN song_part_requirement r ON r.song_id = pss.song_id
				JOIN part p ON p.id = r.part_id
				WHERE pss.practice_schedule_id = :scheduleId
				""")
                .setParameter("scheduleId", scheduleId)
                .getResultList();
        List<Map<String, Object>> result = new ArrayList<>();
        // 부족 인원이 양수인 파트만 사용자 경고 형태로 변환한다.
        for (Object[] row : rows) {
            long missing = ((Number) row[2]).longValue() - ((Number) row[3]).longValue();
            if (missing > 0) {
                result.add(Map.of(
                        "type",
                        "PART_SHORTAGE",
                        "message",
                        row[1] + "이 " + missing + "명 부족합니다.",
                        "targetType",
                        "SONG",
                        "targetId",
                        row[0].toString()));
            }
        }
        return result;
    }

    /** 일정 참석자가 있는지 확인한다 */
    @Transactional(readOnly = true)
    public boolean hasAttendees(Long scheduleId) {
        // 대상 규칙을 실제 회원 ID로 해석한 결과가 하나라도 있는지 확인한다.
        return !attendeeIds(scheduleId).isEmpty();
    }

    /** 수정 후보 일정에서 충돌과 파트 부족 경고를 계산한다. */
    private List<Map<String, Object>> warnings(PracticeSchedule candidate, boolean includeTentative) {
        // 저장된 일정의 시간과 실제 참석자를 후보 값으로 사용해 공통 경고 계산기로 넘긴다.
        return warningsForCandidate(
                candidate.getPerformance().getClub().getId(),
                candidate.getPerformance().getId(),
                candidate.getId(),
                candidate.getStartAt(),
                candidate.getEndAt(),
                new HashSet<>(attendeeIds(candidate.getId())),
                includeTentative,
                null);
    }

    /** API-106·109의 저장 전 검사도 이 순수 조회 경로를 사용한다. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> warningsForCandidate(
            Long clubId,
            Long performanceId,
            Long excludeScheduleId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Set<Long> candidateMembers) {
        return warningsForCandidate(
                clubId, performanceId, excludeScheduleId, startAt, endAt, candidateMembers, true, null);
    }

    private List<Map<String, Object>> warningsForCandidate(
            Long clubId,
            Long performanceId,
            Long excludeScheduleId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Set<Long> candidateMembers,
            boolean includeTentative,
            Set<Long> visiblePerformances) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 공연 정책의 합주 시즌을 벗어나면 저장 차단 대신 경고를 추가한다.
        performances.policyIfExists(clubId, performanceId).ifPresent(policy -> {
            if (policy.getPracticeSeasonStart() != null
                    && policy.getPracticeSeasonEnd() != null
                    && (kst(startAt).toLocalDate().isBefore(policy.getPracticeSeasonStart())
                            || kst(endAt).toLocalDate().isAfter(policy.getPracticeSeasonEnd()))) {
                result.add(Map.of("type", "OUT_OF_SEASON", "message", "합주 시즌 밖 일정입니다."));
            }
        });
        // 참석 대상이 없으면 회원 간 시간 충돌을 비교할 필요가 없다.
        if (candidateMembers.isEmpty()) {
            return result;
        }
        // 같은 동아리의 겹치는 일정 중 공통 참석자가 있는 경우 상세 충돌을 만든다.
        for (PracticeSchedule other :
                schedules.findByPerformance_Club_IdAndStartAtLessThanAndEndAtGreaterThan(clubId, endAt, startAt)) {
            if (other.getId().equals(excludeScheduleId)
                    || (visiblePerformances != null
                            && !visiblePerformances.contains(
                                    other.getPerformance().getId()))
                    || other.getStatus() == ScheduleStatus.CANCELED
                    || (!includeTentative && other.getStatus() == ScheduleStatus.TENTATIVE)) {
                continue;
            }
            Set<Long> common = new HashSet<>(candidateMembers);
            common.retainAll(attendeeIds(other.getId()));
            if (!common.isEmpty()) {
                result.add(Map.of(
                        "type",
                        "OVERLAP",
                        "scheduleId",
                        other.getId().toString(),
                        "title",
                        other.getTitle() == null ? "" : other.getTitle(),
                        "startAt",
                        kst(startAt.isAfter(other.getStartAt()) ? startAt : other.getStartAt()),
                        "endAt",
                        kst(endAt.isBefore(other.getEndAt()) ? endAt : other.getEndAt()),
                        "participants",
                        common.stream().map(this::participant).toList()));
            }
        }
        // 다른 동아리의 확정 일정은 대상 사용자만 비교한다. 상대 동아리·일정 식별 정보는 돌려주지 않는다.
        @SuppressWarnings("unchecked")
        List<Number> otherIds = entityManager
                .createNativeQuery("""
				SELECT ps.id FROM practice_schedule ps JOIN performance p ON p.id = ps.performance_id
				WHERE p.club_id <> :clubId AND ps.status = 'CONFIRMED'
				  AND ps.start_at < :endAt AND ps.end_at > :startAt
				""")
                .setParameter("clubId", clubId)
                .setParameter("startAt", startAt)
                .setParameter("endAt", endAt)
                .getResultList();
        // 다른 동아리에서는 membership ID가 다르므로 user ID로 동일 인물을 비교한다.
        Set<Long> candidateUsers = new HashSet<>(candidateMembers.stream()
                .map(id -> entityManager.find(Membership.class, id).getUser().getId())
                .toList());
        for (Number number : otherIds) {
            PracticeSchedule other = schedules.findById(number.longValue()).orElseThrow();
            Set<Long> otherUsers = new HashSet<>(attendeeIds(other.getId()).stream()
                    .map(id ->
                            entityManager.find(Membership.class, id).getUser().getId())
                    .toList());
            otherUsers.retainAll(candidateUsers);
            if (!otherUsers.isEmpty()) {
                result.add(Map.of(
                        "type",
                        "OVERLAP_OTHER_CLUB",
                        "message",
                        "다른 일정 있음",
                        "startAt",
                        kst(startAt.isAfter(other.getStartAt()) ? startAt : other.getStartAt()),
                        "endAt",
                        kst(endAt.isBefore(other.getEndAt()) ? endAt : other.getEndAt()),
                        "participants",
                        candidateMembers.stream()
                                .filter(id -> otherUsers.contains(entityManager
                                        .find(Membership.class, id)
                                        .getUser()
                                        .getId()))
                                .map(this::participant)
                                .toList()));
            }
        }
        return result;
    }

    /** 경고 응답에 사용할 참여자 식별자와 표시명을 조회한다. */
    private Map<String, String> participant(Long membershipId) {
        // 동아리 표시명이 없을 때만 사용자 실명을 대신 사용한다.
        Membership membership = entityManager.find(Membership.class, membershipId);
        String displayName =
                membership.getDisplayName() == null ? membership.getUser().getName() : membership.getDisplayName();
        return Map.of("membershipId", membershipId.toString(), "displayName", displayName);
    }

    /** UTC 지역 시각을 한국 오프셋 시각으로 변환한다. */
    private static OffsetDateTime kst(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                .toOffsetDateTime();
    }

    /** 일정 대상 규칙에 포함되는 참여자 식별자를 조회한다. */
    private List<Long> attendeeIds(Long scheduleId) {
        // 전체 참여자, 연결 팀원, 연결 곡 세션 중 하나에 해당하는 승인 회원을 구한다.
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager
                .createNativeQuery("""
				SELECT pm.membership_id FROM practice_schedule ps
				JOIN performance_member pm ON pm.performance_id = ps.performance_id AND pm.status = 'APPROVED'
				WHERE ps.id = :scheduleId AND (ps.for_all_participants = TRUE
					OR EXISTS (SELECT 1 FROM practice_schedule_team pst JOIN team_member tm
						ON tm.team_id = pst.team_id WHERE pst.practice_schedule_id = ps.id
						AND tm.performance_member_id = pm.id)
					OR EXISTS (SELECT 1 FROM practice_schedule_song pss JOIN song_session_member ssm
						ON ssm.song_id = pss.song_id WHERE pss.practice_schedule_id = ps.id
						AND ssm.performance_member_id = pm.id))
				""")
                .setParameter("scheduleId", scheduleId)
                .getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    /** 일정 유형과 대상 팀을 이용해 자동 제목을 생성한다. */
    private String automaticTitle(PracticeSchedule schedule, ScheduleType type, List<Long> teamIds) {
        // 공연 날짜가 있으면 연·월을 제목 접두어로 붙인다.
        String teamName = null;
        // 대상 팀이 있으면 첫 팀 이름과 나머지 팀 수를 제목에 요약한다.
        if (!teamIds.isEmpty()) {
            Team first = entityManager.find(Team.class, teamIds.get(0));
            if (first == null
                    || !first.getPerformance()
                            .getId()
                            .equals(schedule.getPerformance().getId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "같은 공연의 팀을 찾을 수 없습니다.");
            }
            teamName = first.getName();
        }
        // 일정 유형을 사용자가 이해할 수 있는 한국어 접미어로 변환한다.
        return ScheduleTitles.resolve(null, schedule.getPerformance(), type, teamName, teamIds.size());
    }

    /** JSON 배열의 식별자를 중복 없는 양의 정수 목록으로 변환한다. */
    private static List<Long> ids(JsonNode node, String field) {
        // 배열 형식과 각 ID의 양수 여부를 검증한다.
        if (node == null || !node.isArray()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "은 배열이어야 합니다.");
        }
        List<Long> result = new ArrayList<>();
        for (JsonNode item : node) {
            result.add(ApiIds.positive(item.asText(), field));
        }
        // 같은 연결 엔티티가 중복 생성되지 않도록 중복 ID도 거부한다.
        if (new HashSet<>(result).size() != result.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "에 중복 ID가 있습니다.");
        }
        return result;
    }

    /** JSON 문자열을 최대 길이에 맞게 검증하고 정규화한다. */
    private static String string(JsonNode node, int max) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual() || node.asText().length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "문자열 필드가 유효하지 않습니다.");
        }
        return node.asText();
    }

    /** JSON 노드를 필수 불리언 값으로 변환한다. */
    private static boolean booleanValue(JsonNode node) {
        if (!node.isBoolean()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "boolean 필드가 유효하지 않습니다.");
        }
        return node.asBoolean();
    }

    /** JSON 문자열을 유효한 일정 유형으로 변환한다. */
    private static ScheduleType enumValue(JsonNode node) {
        if (!node.isTextual()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "eventType이 유효하지 않습니다.");
        }
        try {
            return ScheduleType.valueOf(node.asText());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "eventType이 유효하지 않습니다.");
        }
    }

    /** JSON 오프셋 시각을 UTC 지역 시각으로 변환한다. */
    private static LocalDateTime dateTime(JsonNode node) {
        if (!node.isTextual()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "일시가 유효하지 않습니다.");
        }
        try {
            return OffsetDateTime.parse(node.asText())
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "일시가 유효하지 않습니다.");
        }
    }
}
